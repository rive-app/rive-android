package app.rive.compose

import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.ExperimentalDeferredRendering
import app.rive.RenderBackend
import app.rive.Result
import app.rive.RiveAndroidTest
import app.rive.RiveFileSource
import app.rive.RiveResourceClosedException
import app.rive.ViewModelInstance
import app.rive.core.RiveWorker
import app.rive.rememberDeferredRiveWorker
import app.rive.rememberRiveWorker
import app.rive.rememberViewModelInstanceResult
import app.rive.runtime.kotlin.test.R
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RiveWorkerComposeTest : RiveAndroidTest() {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Verifies a remembered worker is released after leaving composition. */
    @Test
    fun removedFromComposition_releasesRememberedWorker() {
        lateinit var worker: RiveWorker
        lateinit var showWorker: MutableState<Boolean>

        composeRule.setContent {
            showWorker = remember { mutableStateOf(true) }
            if (showWorker.value) {
                worker = rememberRiveWorker(autoPoll = false)
            }
        }

        assertEquals(1, worker.refCount)
        assertFalse(worker.isDisposed)

        composeRule.runOnUiThread { showWorker.value = false }
        composeRule.waitForIdle()

        assertEquals(0, worker.refCount)
        assertTrue(worker.isDisposed)
    }

    /** Verifies warmup is fixed per worker and changing the option releases the old worker. */
    @Test
    fun precompileShadersChange_recreatesWorker() {
        lateinit var worker: RiveWorker
        lateinit var precompileShaders: MutableState<Boolean>
        lateinit var showWorker: MutableState<Boolean>

        composeRule.setContent {
            precompileShaders = remember { mutableStateOf(false) }
            showWorker = remember { mutableStateOf(true) }
            if (showWorker.value) {
                worker = rememberRiveWorker(
                    autoPoll = false,
                    precompileShaders = precompileShaders.value,
                )
            }
        }
        val original = worker
        composeRule.runOnUiThread { precompileShaders.value = true }
        composeRule.waitForIdle()
        val replacement = worker
        assertNotSame(original, replacement)
        assertTrue(original.isDisposed)
        assertFalse(replacement.isDisposed)

        composeRule.runOnUiThread { showWorker.value = false }
        composeRule.waitForIdle()
        assertTrue(replacement.isDisposed)
    }

    /** Verifies backend changes replace the worker and recreate its complete resource tree. */
    @Test
    @OptIn(ExperimentalDeferredRendering::class)
    fun deferredWorker_backendChange_recreatesWorkerAndResources() {
        val source = RiveFileSource.RawRes(R.raw.ore, context.resources)
        val generations = CopyOnWriteArrayList<WorkerGeneration>()
        val latestError = AtomicReference<Throwable?>()
        lateinit var selectedBackend: MutableState<RenderBackend>
        lateinit var showContent: MutableState<Boolean>

        composeRule.setContent {
            selectedBackend = remember { mutableStateOf(RenderBackend.OpenGL) }
            showContent = remember { mutableStateOf(true) }
            if (showContent.value) {
                val requestedBackend = selectedBackend.value
                val worker = rememberDeferredRiveWorker(renderBackend = requestedBackend)
                val resourcesResult = rememberTestRiveResources(source, worker)
                val contentResult = resourcesResult.andThen { resources ->
                    rememberViewModelInstanceResult(resources.file).map { viewModelInstance ->
                        WorkerGeneration(requestedBackend, worker, resources, viewModelInstance)
                    }
                }

                when (contentResult) {
                    is Result.Error -> SideEffect {
                        latestError.set(contentResult.throwable)
                    }

                    Result.Loading -> Unit

                    is Result.Success -> SideEffect {
                        if (generations.lastOrNull()?.worker !== worker) {
                            generations += contentResult.value
                        }
                    }
                }
            }
        }

        awaitGeneration(generations, latestError, expectedCount = 1)
        val openGlWorker = generations[0].worker

        composeRule.runOnUiThread { selectedBackend.value = RenderBackend.Vulkan }
        awaitGeneration(generations, latestError, expectedCount = 2)
        assertTrue(openGlWorker.isDisposed, "The replaced OpenGL worker was not disposed")
        val vulkanWorker = generations[1].worker
        assertNotSame(openGlWorker, vulkanWorker)
        assertResourcesReplaced(generations[0], generations[1])

        composeRule.runOnUiThread { selectedBackend.value = RenderBackend.OpenGL }
        awaitGeneration(generations, latestError, expectedCount = 3)
        assertTrue(vulkanWorker.isDisposed, "The replaced Vulkan worker was not disposed")
        val replacementOpenGlWorker = generations[2].worker
        assertNotSame(openGlWorker, replacementOpenGlWorker)
        assertNotSame(vulkanWorker, replacementOpenGlWorker)
        assertResourcesReplaced(generations[1], generations[2])
        assertResourcesReplaced(generations[0], generations[2])

        assertEquals(
            listOf(RenderBackend.OpenGL, RenderBackend.Vulkan, RenderBackend.OpenGL),
            generations.map(WorkerGeneration::requestedBackend),
        )

        composeRule.runOnUiThread { showContent.value = false }
        composeRule.awaitWithWallClock(
            timeoutMessage = { "The final OpenGL worker was not disposed" },
        ) {
            replacementOpenGlWorker.isDisposed
        }
        assertResourcesClosed(generations[2])
    }

    /**
     * Waits for a deferred worker's complete resource generation to finish loading.
     *
     * @param generations Successfully loaded worker generations.
     * @param latestError Most recent resource-loading failure, if any.
     * @param expectedCount Number of generations expected after the backend change.
     */
    private fun awaitGeneration(
        generations: List<WorkerGeneration>,
        latestError: AtomicReference<Throwable?>,
        expectedCount: Int,
    ) {
        composeRule.awaitWithWallClock(
            timeoutMessage = {
                "Expected $expectedCount deferred worker generations; " +
                    "observed ${generations.size}. Latest error: ${latestError.get()}"
            },
        ) {
            generations.size >= expectedCount
        }
    }

    /**
     * Verifies replacement resources are distinct and open, and the old resources are closed.
     *
     * @param previous The generation removed from composition.
     * @param replacement The generation currently in composition.
     */
    private fun assertResourcesReplaced(
        previous: WorkerGeneration,
        replacement: WorkerGeneration,
    ) {
        assertNotSame(previous.resources.file, replacement.resources.file)
        assertNotSame(previous.resources.artboard, replacement.resources.artboard)
        assertNotSame(previous.resources.stateMachine, replacement.resources.stateMachine)
        assertNotSame(previous.viewModelInstance, replacement.viewModelInstance)
        replacement.resources.file.checkOpen()
        replacement.resources.artboard.checkOpen()
        replacement.resources.stateMachine.checkOpen()
        replacement.viewModelInstance.checkOpen()
        assertResourcesClosed(previous)
    }

    /**
     * Verifies every resource in a generation rejects use after leaving composition.
     *
     * @param generation The generation whose resources should be closed.
     */
    private fun assertResourcesClosed(generation: WorkerGeneration) {
        assertFailsWith<RiveResourceClosedException> { generation.resources.file.checkOpen() }
        assertFailsWith<RiveResourceClosedException> { generation.resources.artboard.checkOpen() }
        assertFailsWith<RiveResourceClosedException> {
            generation.resources.stateMachine.checkOpen()
        }
        assertFailsWith<RiveResourceClosedException> { generation.viewModelInstance.checkOpen() }
    }

    /** A worker and its complete resource tree, retained for replacement and closure assertions. */
    private data class WorkerGeneration(
        val requestedBackend: RenderBackend,
        val worker: RiveWorker,
        val resources: TestRiveResources,
        val viewModelInstance: ViewModelInstance,
    )
}

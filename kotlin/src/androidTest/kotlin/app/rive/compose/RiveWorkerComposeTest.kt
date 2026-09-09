package app.rive.compose

import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.ExperimentalDeferredRendering
import app.rive.RenderBackend
import app.rive.Result
import app.rive.Rive
import app.rive.RiveAndroidTest
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.RiveResourceClosedException
import app.rive.ViewModelInstance
import app.rive.core.CommandQueuePoller
import app.rive.core.DefaultRiveResources
import app.rive.core.RiveWorker
import app.rive.core.assertDisposed
import app.rive.core.loadDefaultRiveResources
import app.rive.rememberDeferredRiveWorker
import app.rive.rememberRiveWorker
import app.rive.rememberViewModelInstanceResult
import app.rive.runtime.kotlin.test.R
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
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

    /** Verifies replacing a file from another worker creates and renders to a fresh surface. */
    @Test
    fun rive_recreatesSurface_whenWorkerChanges() = runBlocking<Unit> {
        val firstResources = loadDefaultRiveResources(R.raw.empty)
        val secondWorker = RiveWorker()
        val secondPoller = CommandQueuePoller(secondWorker)
        val secondResources = secondWorker.loadDefaultRiveResources(R.raw.empty)
        lateinit var activeResources: MutableState<DefaultRiveResources>
        var showContent: MutableState<Boolean>? = null
        val renderedFrameCount = AtomicInteger()

        // Handles are worker-local, so this also exercises a worker replacement whose native
        // effect keys would otherwise appear unchanged.
        assertEquals(firstResources.artboard.artboardHandle, secondResources.artboard.artboardHandle)
        assertEquals(
            firstResources.stateMachine.stateMachineHandle,
            secondResources.stateMachine.stateMachineHandle,
        )

        try {
            composeRule.setContent {
                activeResources = remember { mutableStateOf(firstResources) }
                val activeShowContent = remember { mutableStateOf(true) }
                showContent = activeShowContent
                if (activeShowContent.value) {
                    val resources = activeResources.value
                    Rive(
                        file = resources.file,
                        modifier = Modifier.size(100.dp),
                        playing = false,
                        artboard = resources.artboard,
                        stateMachine = resources.stateMachine,
                        onBitmapAvailable = { renderedFrameCount.incrementAndGet() },
                    )
                }
            }

            composeRule.awaitWithWallClock(
                timeoutMessage = { "The first worker did not render a frame" },
            ) {
                renderedFrameCount.get() >= 1
            }

            composeRule.runOnUiThread {
                activeResources.value = secondResources
            }

            composeRule.awaitWithWallClock(
                timeoutMessage = { "The replacement worker did not render to a fresh surface" },
            ) {
                renderedFrameCount.get() >= 2
            }
        } finally {
            showContent?.let { activeShowContent ->
                composeRule.runOnUiThread { activeShowContent.value = false }
                composeRule.waitForIdle()
            }
            secondResources.close()
            secondPoller.close()
            secondWorker.release(javaClass.simpleName, "Test cleanup")
            assertDisposed(secondWorker)
        }
    }

    /** Verifies same-worker resource loading does not remove the active rendering surface. */
    @Test
    fun rive_preservesSurface_whileReplacementResourcesLoad() = runBlocking<Unit> {
        val worker = RiveWorker()
        val poller = CommandQueuePoller(worker)
        val source = RiveFileSource.RawRes(R.raw.empty, context.resources)
        val firstFile = RiveFile.load(source, worker)
        val secondFile = RiveFile.load(source, worker)
        lateinit var activeFile: MutableState<RiveFile>
        var showContent: MutableState<Boolean>? = null
        val renderedFrameCount = AtomicInteger()

        try {
            composeRule.setContent {
                activeFile = remember { mutableStateOf(firstFile) }
                val activeShowContent = remember { mutableStateOf(true) }
                showContent = activeShowContent
                if (activeShowContent.value) {
                    Rive(
                        file = activeFile.value,
                        modifier = Modifier.size(100.dp),
                        playing = false,
                        onBitmapAvailable = { renderedFrameCount.incrementAndGet() },
                    )
                }
            }

            composeRule.awaitWithWallClock(
                timeoutMessage = { "The initial file did not create a surface and render" },
            ) {
                renderedFrameCount.get() >= 1
            }
            val initialTextureView = checkNotNull(currentTextureView())
            assertTrue(initialTextureView.isAvailable)
            val initialSurfaceTexture = checkNotNull(initialTextureView.surfaceTexture)

            poller.withPollingPaused {
                composeRule.runOnUiThread {
                    activeFile.value = secondFile
                }
                composeRule.waitForIdle()

                val loadingTextureView = checkNotNull(currentTextureView())
                assertSame(initialTextureView, loadingTextureView)
                assertSame(initialSurfaceTexture, loadingTextureView.surfaceTexture)
                assertEquals(1, renderedFrameCount.get())
            }
        } finally {
            showContent?.let { activeShowContent ->
                composeRule.runOnUiThread { activeShowContent.value = false }
                composeRule.waitForIdle()
            }
            firstFile.close()
            secondFile.close()
            poller.close()
            worker.release(javaClass.simpleName, "Test cleanup")
            assertDisposed(worker)
        }
    }

    /** Verifies a new resource generation reruns effects when presentation settings are unchanged. */
    @Test
    fun rive_unsettlesReplacementStateMachine_whenEffectParametersAreUnchanged() = runBlocking<Unit> {
        val firstResources = loadDefaultRiveResources(R.raw.empty)
        val secondResources = loadDefaultRiveResources(R.raw.empty)
        lateinit var activeResources: MutableState<DefaultRiveResources>
        var showContent: MutableState<Boolean>? = null
        val renderedFrameCount = AtomicInteger()

        try {
            composeRule.setContent {
                activeResources = remember { mutableStateOf(firstResources) }
                val activeShowContent = remember { mutableStateOf(true) }
                showContent = activeShowContent
                if (activeShowContent.value) {
                    val resources = activeResources.value
                    Rive(
                        file = resources.file,
                        modifier = Modifier.size(100.dp),
                        artboard = resources.artboard,
                        stateMachine = resources.stateMachine,
                        onBitmapAvailable = { renderedFrameCount.incrementAndGet() },
                    )
                }
            }

            composeRule.awaitWithWallClock(
                timeoutMessage = { "The initial state machine did not render and settle" },
            ) {
                renderedFrameCount.get() >= 1 && firstResources.stateMachine.settled.value
            }

            composeRule.runOnUiThread {
                riveWorker.onStateMachineSettled(
                    Long.MAX_VALUE,
                    secondResources.stateMachine.stateMachineHandle,
                )
                assertTrue(secondResources.stateMachine.settled.value)
            }

            // Launch the replacement effects in a controlled frame, and pause polling so a
            // native callback cannot settle the replacement before its newly keyed effects
            // are observed.
            composeRule.mainClock.autoAdvance = false
            withRiveWorkerPollingPaused {
                composeRule.runOnUiThread {
                    activeResources.value = secondResources
                }
                // Commit the replacement and launch its resource-keyed effects.
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.waitForIdle()

                assertFalse(secondResources.stateMachine.settled.value)
            }
        } finally {
            try {
                showContent?.let { activeShowContent ->
                    composeRule.runOnUiThread { activeShowContent.value = false }
                    // Commit removal before restoring automatic frames so a suspended draw
                    // cannot resume against the surface while that surface is being disposed.
                    if (!composeRule.mainClock.autoAdvance) {
                        composeRule.mainClock.advanceTimeByFrame()
                    }
                    composeRule.waitForIdle()
                }
            } finally {
                composeRule.mainClock.autoAdvance = true
            }
        }
    }

    /** Returns the TextureView currently hosted by the Rive composable, if one exists. */
    private fun currentTextureView(): TextureView? {
        val textureView = AtomicReference<TextureView?>()
        composeRule.activityRule.scenario.onActivity { activity ->
            textureView.set(activity.window.decorView.findTextureView())
        }
        return textureView.get()
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
        assertFailsWith<RiveResourceClosedException> { generation.resources.stateMachine.checkOpen() }
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

/** Returns the first TextureView in this view subtree, if present. */
private fun View.findTextureView(): TextureView? {
    if (this is TextureView) {
        return this
    }
    if (this !is ViewGroup) {
        return null
    }
    for (index in 0 until childCount) {
        getChildAt(index).findTextureView()?.let { return it }
    }
    return null
}

package app.rive.compose

import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.Rive
import app.rive.RiveAndroidTest
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.core.CommandQueuePoller
import app.rive.core.DefaultRiveResources
import app.rive.core.RiveWorker
import app.rive.core.assertDisposed
import app.rive.core.loadDefaultRiveResources
import app.rive.rememberRiveWorker
import app.rive.runtime.kotlin.test.R
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

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
    fun rive_unsettlesReplacementStateMachine_whenEffectParametersAreUnchanged() =
        runBlocking<Unit> {
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

package app.rive

import android.graphics.Rect
import android.os.Build
import androidx.annotation.RawRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import app.rive.core.RenderContextVulkan
import app.rive.core.RiveWorker
import app.rive.runtime.kotlin.test.R
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/** Exercises a GPU Canvas fixture through Android's deferred renderer. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalHardwareBitmapRendering::class)
open class DeferredRenderingTest : RiveAndroidTest() {
    /** @return A worker whose rendering commands record through a deferred session. */
    override fun createTestRiveWorker(): RiveWorker = RiveWorker.createDeferred()

    /**
     * Verifies that a GPU Canvas file imports and produces its first deferred frame.
     *
     * This exercises file and scripting registration, default resource creation, and deferred
     * replay without comparing pixels.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun multiStage_importsAndRendersFirstFrame() = runBlocking {
        renderFirstFrame(R.raw.multi_stage)
    }

    /**
     * Verifies that alternating GPU Canvas files render after each resource generation is closed.
     *
     * The sequence starts with `ore.riv`, switches files ten times, and fully tears down the file,
     * artboard, state machine, VMI, and canvas session between generations. This guards against
     * accidentally combining handles owned by different files while also exercising repeated
     * deferred-session creation and destruction.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun gpuCanvasFiles_switchTenTimesAndRenderEveryGeneration() = runBlocking {
        renderFirstFrame(R.raw.ore)
        repeat(10) { switchIndex ->
            val nextFile = if (switchIndex % 2 == 0) R.raw.multi_stage else R.raw.ore
            renderFirstFrame(nextFile)
        }
    }

    /**
     * Loads one GPU Canvas file, waits for a deferred frame, and closes its complete resource tree.
     *
     * @param rawResourceId The raw Rive resource to render.
     */
    private suspend fun renderFirstFrame(@RawRes rawResourceId: Int) {
        val resources = loadDefaultRiveResources(rawResourceId)
        try {
            ViewModelInstance.create(
                resources.file,
                ViewModelSource.DefaultForArtboard(resources.artboard).defaultInstance(),
            ).use { viewModelInstance ->
                coroutineScope {
                    val lifecycleOwner = withContext(Dispatchers.Main.immediate) {
                        TestLifecycleOwner()
                    }
                    val session = withContext(Dispatchers.Main.immediate) {
                        RiveCanvasSession(
                            context = context,
                            riveWorker = riveWorker,
                            artboard = resources.artboard,
                            stateMachine = resources.stateMachine,
                            viewModelInstance = viewModelInstance,
                        ).also { it.setRegion(Rect(0, 0, 96, 96)) }
                    }
                    val firstFrame = async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeout(10.seconds) { session.frameAvailable.first() }
                    }
                    val playback = launch(Dispatchers.Main.immediate) {
                        session.beginPlaying(lifecycleOwner.lifecycle)
                    }

                    try {
                        withContext(Dispatchers.Main.immediate) {
                            lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
                        }
                        firstFrame.await()
                    } finally {
                        // A playback failure cancels this scope, but the session must still release
                        // its surface reference before RiveAndroidTest releases the worker.
                        withContext(NonCancellable) {
                            withContext(Dispatchers.Main.immediate) {
                                lifecycleOwner.moveToState(Lifecycle.State.DESTROYED)
                                session.close()
                            }
                            playback.cancelAndJoin()
                        }
                    }
                }
            }
        } finally {
            resources.close()
        }
    }

    /** Lifecycle owner used to drive the rendering session. */
    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply {
            currentState = Lifecycle.State.CREATED
        }

        override val lifecycle: Lifecycle
            get() = registry

        /** Moves the lifecycle to [state]. */
        fun moveToState(state: Lifecycle.State) {
            registry.currentState = state
        }
    }
}

/** Runs the deferred-rendering smoke and lifecycle suite on a concrete Vulkan render context. */
@RunWith(AndroidJUnit4::class)
class DeferredVulkanRenderingTest : DeferredRenderingTest() {
    /**
     * Creates a Vulkan-only deferred worker without the public API's OpenGL fallback.
     *
     * @return A worker that must initialize and render with Vulkan.
     */
    override fun createTestRiveWorker(): RiveWorker = RiveWorker(
        renderContext = RenderContextVulkan(),
        deferredEnabled = true,
    )
}

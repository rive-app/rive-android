package app.rive.core

import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.Fit
import app.rive.RiveAndroidTest
import app.rive.runtime.kotlin.test.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private val TEST_TIMEOUT = 3.seconds.inWholeMilliseconds

@RunWith(AndroidJUnit4::class)
class RiveSurfaceLifecycleTest : RiveAndroidTest() {
    @Test
    fun surfaceClose_disposesBeforeFinalDisconnect() {
        val closeableSurface = LatchingImageReaderSurface()
        val surface = riveWorker.createRiveSurface(closeableSurface)

        surface.close()

        assertClosed(closeableSurface)
    }

    @Test
    fun surfaceClose_afterCallerRelease_disposesBeforeFinalDisconnect() {
        val commandQueue = CommandQueue()
        val closeableSurface = LatchingImageReaderSurface()
        val surface = commandQueue.createRiveSurface(closeableSurface)

        commandQueue.release(
            "RiveSurfaceLifecycleTest",
            "Release caller reference before surface close"
        )

        assertFalse(
            commandQueue.isDisposed,
            "RiveSurface should keep CommandQueue alive after caller release"
        )

        surface.close()

        assertDisposed(commandQueue)
        assertClosed(closeableSurface)
    }

    @Test
    fun createRiveSurface_returnsImmediately() = runBlocking {
        val blockEntered = CountDownLatch(1)
        val blockMayExit = CountDownLatch(1)

        // Block the command server before surface creation. Surface creation should only allocate
        // the lazy render target holder and must not wait for this queued work to complete.
        riveWorker.runOnCommandServer {
            blockEntered.countDown()
            blockMayExit.await()
        }

        val closeableSurface = LatchingImageReaderSurface()
        try {
            assertTrue(
                blockEntered.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS),
                "Command server did not enter blocking test work"
            )

            val createSurface = async(Dispatchers.Default) {
                riveWorker.createRiveSurface(closeableSurface)
            }
            assertNotNull(
                withTimeoutOrNull(TEST_TIMEOUT) {
                    createSurface.await()
                },
                "Surface creation did not complete before command server block was allowed to exit"
            ).close()
        } finally {
            blockMayExit.countDown()
        }

        assertClosed(closeableSurface)
    }

    @Test
    fun draw_beforeSurfaceClose_isCanceledBeforeSurfaceDisposal() {
        val gate = CountDownLatch(1)

        val (artboardHandle, stateMachineHandle) =
            riveWorker.loadDefaultArtboardAndStateMachine(R.raw.empty)
        val closeableSurface = LatchingImageReaderSurface()
        val surface = riveWorker.createRiveSurface(closeableSurface)

        // Block command processing so draw and close queue behind this gate. Once released,
        // the server observes draw -> cancelDraw -> dispose in one processing pass.
        riveWorker.runOnCommandServer {
            gate.await(2, TimeUnit.SECONDS)
        }
        riveWorker.draw(
            artboardHandle,
            stateMachineHandle,
            surface,
            Fit.Contain(),
            Color.TRANSPARENT
        )

        surface.close()
        gate.countDown()

        assertClosed(closeableSurface)
        assertFalse(
            closeableSurface.awaitFrameAvailable(timeoutMillis = 200),
            "Expected draw queued before surface close to be canceled before producing a frame"
        )
    }

    @Test
    fun stress_draw_beforeSurfaceClose_isCanceledBeforeSurfaceDisposal() {
        val (artboardHandle, stateMachineHandle) =
            riveWorker.loadDefaultArtboardAndStateMachine(R.raw.empty)

        riveWorker.withPolling {
            // Stressed variant of draw_beforeSurfaceClose_isCanceledBeforeSurfaceDisposal.
            // Repeated close should cancel pending coalesced draws and keep disposal orderly.
            repeat(200) {
                val surface = createImageSurface(64, 64)
                draw(
                    artboardHandle,
                    stateMachineHandle,
                    surface,
                    Fit.Contain(),
                    Color.TRANSPARENT
                )
                surface.close()
            }
        }
    }

    @Test
    fun draw_afterSurfaceClose_throwsBeforeEnqueue() {
        val surface = riveWorker.createImageSurface(64, 64)

        surface.close()

        assertFailsWith<IllegalStateException>(
            "Expected draw to a closed surface to throw IllegalStateException"
        ) {
            riveWorker.draw(
                ArtboardHandle(1),
                StateMachineHandle(1),
                surface,
                Fit.Contain(),
                Color.TRANSPARENT
            )
        }
    }

    /**
     * Test [CloseableSurface] backed by an [ImageReader].
     *
     * The latches let lifecycle tests assert both sides of surface ordering: whether a queued draw
     * produced an image, and whether surface-backed resources were closed before queue disposal
     * completed.
     */
    private class LatchingImageReaderSurface : CloseableSurface {
        private val closed = CountDownLatch(1)
        private val frameAvailable = CountDownLatch(1)
        private val closeCalled = AtomicBoolean(false)
        private val callbackThread = HandlerThread("RiveTestImageReader").also {
            it.start()
        }
        private val imageReader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ImageReader.newInstance(
                64,
                64,
                PixelFormat.RGBA_8888,
                2,
                HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
            )
        } else {
            ImageReader.newInstance(
                64,
                64,
                PixelFormat.RGBA_8888,
                2
            )
        }

        override val surface: Surface = imageReader.surface

        init {
            imageReader.setOnImageAvailableListener(
                { reader ->
                    reader.acquireLatestImage()?.use {
                        frameAvailable.countDown()
                    }
                },
                Handler(callbackThread.looper)
            )
        }

        override fun close() {
            if (closeCalled.compareAndSet(false, true)) {
                imageReader.setOnImageAvailableListener(null, null)
                imageReader.acquireLatestImage()?.use {
                    frameAvailable.countDown()
                }
                imageReader.close()
                callbackThread.quitSafely()
                closed.countDown()
            }
        }

        fun awaitFrameAvailable(timeoutMillis: Long = 2000): Boolean =
            frameAvailable.await(timeoutMillis, TimeUnit.MILLISECONDS)

        fun awaitClosed(): Boolean = closed.await(2, TimeUnit.SECONDS)
    }

    private fun assertClosed(surface: LatchingImageReaderSurface) {
        assertTrue(
            surface.awaitClosed(),
            "Surface-backed resources were not closed before CommandQueue disposal completed"
        )
    }
}

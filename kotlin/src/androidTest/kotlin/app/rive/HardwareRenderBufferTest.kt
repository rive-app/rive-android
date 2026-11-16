package app.rive

import android.graphics.Bitmap
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import app.rive.core.RiveWorker
import app.rive.core.assertDisposed
import app.rive.runtime.kotlin.test.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalHardwareBitmapRendering::class)
open class HardwareRenderBufferTest : RiveAndroidTest() {
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun close_afterFramePublication_closesBufferAndSurface() = runBlocking {
        val resources = loadRiveResources(R.raw.empty)
        val threadsBeforeConstruction = liveImageReaderThreads()
        HardwareRenderBuffer(64, 64, riveWorker).use { buffer ->
            val frameAvailable = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(2_000L) { buffer.frameAvailable.first() }
            }
            buffer.render(resources.artboard, resources.stateMachine)
            frameAvailable.await()
            assertEquals(Bitmap.Config.HARDWARE, buffer.consumeLatestBitmap()?.config)

            buffer.close()

            assertTrue(buffer.closed)
            assertTrue(buffer.surface.closed)
            assertEquals(threadsBeforeConstruction, liveImageReaderThreads())
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun constructor_whenSurfaceCreationFails_stopsImageReaderThread() {
        val disposedWorker = RiveWorker()
        disposedWorker.release(javaClass.simpleName, "Force surface creation failure")
        assertDisposed(disposedWorker)
        val threadsBeforeConstruction = liveImageReaderThreads()

        assertFailsWith<RiveResourceClosedException> {
            HardwareRenderBuffer(64, 64, disposedWorker)
        }

        val threadsAfterFailure = liveImageReaderThreads()
        assertEquals(threadsBeforeConstruction, threadsAfterFailure)
    }

    /** Background changes publish fresh frames even when the artboard is unchanged. */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun render_withChangedClearColor_emitsAndConsumesHardwareBitmaps() = runBlocking {
        val resources = loadRiveResources(R.raw.empty)
        HardwareRenderBuffer(64, 64, riveWorker).use { buffer ->
            repeat(3) { color ->
                val frameAvailable = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeout(2_000L) { buffer.frameAvailable.first() }
                }
                buffer.render(resources.artboard, resources.stateMachine, clearColor = color)
                frameAvailable.await()

                assertEquals(
                    Bitmap.Config.HARDWARE,
                    buffer.consumeLatestBitmap()?.config
                )
            }
        }
    }

    /** Unchanged submissions publish nothing, but presentation changes still draw. */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun render_unchanged_skipsUntilPresentationChanges() = runBlocking {
        val resources = loadRiveResources(R.raw.empty)
        HardwareRenderBuffer(64, 64, riveWorker).use { buffer ->
            val fits = listOf(
                Fit.Contain(),
                Fit.Cover(),
                Fit.Cover(Alignment.TopLeft),
                Fit.Layout(1f),
                Fit.Layout(2f),
            )
            for (fit in fits) {
                val changedFrame = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeout(2_000L) { buffer.frameAvailable.first() }
                }
                buffer.render(resources.artboard, resources.stateMachine, fit)
                changedFrame.await()
                assertEquals(Bitmap.Config.HARDWARE, buffer.consumeLatestBitmap()?.config)

                val unchangedFrame = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeoutOrNull(300L) { buffer.frameAvailable.first() }
                }
                buffer.render(resources.artboard, resources.stateMachine, fit)
                assertNull(unchangedFrame.await())
            }
        }
    }

    /** Each surface must present even when another surface consumed the artboard change. */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun render_sharedArtboard_presentsOnEachSurface() = runBlocking {
        val resources = loadRiveResources(R.raw.empty)
        HardwareRenderBuffer(64, 64, riveWorker).use { first ->
            HardwareRenderBuffer(96, 96, riveWorker).use { second ->
                repeat(2) { pass ->
                    if (pass == 1) {
                        resources.artboard.resizeArtboard(second.surface)
                        resources.stateMachine.advance(kotlin.time.Duration.ZERO)
                    }
                    for (buffer in listOf(first, second)) {
                        val frame = async(start = CoroutineStart.UNDISPATCHED) {
                            withTimeout(2_000L) { buffer.frameAvailable.first() }
                        }
                        buffer.render(resources.artboard, resources.stateMachine)
                        frame.await()
                        assertEquals(Bitmap.Config.HARDWARE, buffer.consumeLatestBitmap()?.config)
                    }
                }
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun close_fromFrameCallbackThread_doesNotJoinItself() = runBlocking {
        val resources = loadRiveResources(R.raw.empty)
        HardwareRenderBuffer(64, 64, riveWorker).use { buffer ->
            val callbackThread = CompletableDeferred<Thread>()
            val closeResult = CompletableDeferred<kotlin.Result<Unit>>()
            val collector = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                buffer.frameAvailable.first()
                callbackThread.complete(Thread.currentThread())
                closeResult.complete(runCatching { buffer.close() })
            }

            buffer.render(resources.artboard, resources.stateMachine)

            // The first frame can take a while, so only close() is held to the short limit.
            val imageReaderThread = withTimeout(5_000L) { callbackThread.await() }
            withTimeout(750L) { closeResult.await() }.getOrThrow()
            assertEquals("Rive/ImageReader", imageReaderThread.name)
            collector.join()
            withTimeout(2_000L) {
                while (imageReaderThread.isAlive) {
                    delay(10L)
                }
            }
            assertFalse(imageReaderThread.isAlive)
        }
    }

    /** Returns the live callback threads created by [HardwareRenderBuffer]. */
    private fun liveImageReaderThreads(): Set<Thread> =
        Thread.getAllStackTraces().keys.filterTo(mutableSetOf()) { thread ->
            thread.isAlive && thread.name == "Rive/ImageReader"
        }
}

/** Runs the same surface publication and draw-skip checks through deferred rendering. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalHardwareBitmapRendering::class)
class DeferredHardwareRenderBufferTest : HardwareRenderBufferTest() {
    /** Resource cleanup must run even when the remaining surface has no visual changes. */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun render_afterClosingOtherFile_drainsDestructionsBeforeSkipping() = runBlocking {
        val resources = loadRiveResources(R.raw.empty)
        RiveFile.load(
            RiveFileSource.RawRes(R.raw.shapes, context.resources),
            riveWorker
        ).use { otherFile ->
            HardwareRenderBuffer(64, 64, riveWorker).use { buffer ->
                val firstFrame = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeout(2_000L) { buffer.frameAvailable.first() }
                }
                // Replay the other file's resource creations, then leave the session idle.
                buffer.render(resources.artboard, resources.stateMachine)
                firstFrame.await()
                buffer.consumeLatestBitmap()

                val unchangedFrame = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeoutOrNull(300L) { buffer.frameAvailable.first() }
                }
                buffer.render(resources.artboard, resources.stateMachine)
                assertNull(unchangedFrame.await(), "Precondition: the surface must be unchanged")

                // These destructions arrive after the previous replay's resetFrame drained queues.
                otherFile.close()
                val cleanupFrame = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeout(2_000L) { buffer.frameAvailable.first() }
                }
                buffer.render(resources.artboard, resources.stateMachine)
                cleanupFrame.await()
                assertEquals(Bitmap.Config.HARDWARE, buffer.consumeLatestBitmap()?.config)

                val afterCleanup = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeoutOrNull(300L) { buffer.frameAvailable.first() }
                }
                buffer.render(resources.artboard, resources.stateMachine)
                assertNull(afterCleanup.await(), "Skipping must resume after cleanup is replayed")
            }
        }
    }

    /** @return A worker that records and replays draws through a deferred session. */
    override fun createTestRiveWorker(): RiveWorker = RiveWorker(enableGPUCanvas = true)
}

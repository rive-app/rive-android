package app.rive

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import app.rive.core.CheckableAutoCloseable
import app.rive.core.CloseOnce
import app.rive.core.RenderingDefaults
import app.rive.core.RiveSurface
import app.rive.core.RiveWorker
import app.rive.core.traceSection
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * GPU-backed offscreen render target for realtime canvas presentation (API 29+).
 *
 * Use this when you need high-throughput rendering into a hardware-accelerated [Canvas] without
 * CPU pixel readback. For software pixels, snapshot testing, or CPU-side image workflows, use
 * [SoftwareRenderBuffer].
 *
 * The dimensions of this buffer are fixed at construction and cannot be resized. To render at a
 * different size, create a new buffer.
 *
 * Ownership/lifecycle:
 * - This class owns an ImageReader surface and a callback thread to receive messages from it, and
 *   must be [closed][close].
 * - It is expected that there is only a single consumer using [consumeLatestBitmap].
 *
 * Performance:
 * - [render] enqueues GPU work and returns; frame publication is asynchronous.
 * - Frame publication notifications are exposed via [frameAvailable].
 *
 * Threading:
 * - Image acquisition and hardware-buffer wrapping run on a dedicated HandlerThread.
 * - [render] and [consumeLatestBitmap] are safe to call from the caller thread (typically main).
 *
 * API level:
 * - Requires Android API 29+ for hardware bitmap and usage-flag support.
 */
@ExperimentalHardwareBitmapRendering
@RequiresApi(Build.VERSION_CODES.Q)
class HardwareRenderBuffer private constructor(
    val width: Int,
    val height: Int,
    private val riveWorker: RiveWorker,
    frameSourceFactory: HardwareFrameSourceFactory,
    sdkInt: Int,
    private val firstFrameTimeoutMillis: Long,
) : CheckableAutoCloseable {
    companion object {
        private const val TAG = "Rive/RenderBuffer/Hardware"
        // Deferred Vulkan can compile pipelines before ImageReader publishes its first frame.
        private const val FIRST_FRAME_TIMEOUT_MILLIS = 2_000L

        /** @return true when hardware bitmap rendering is supported on this API level. */
        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        /**
         * Creates a hardware render buffer with injectable platform behavior for local tests.
         *
         * @param width Buffer width in pixels.
         * @param height Buffer height in pixels.
         * @param riveWorker Worker used for draw submission.
         * @param frameSourceFactory Factory for the test frame source and surface.
         * @param sdkInt Android API level used for the hardware support check.
         * @param firstFrameTimeoutMillis Timeout used while waiting for the first frame.
         * @return The created hardware render buffer.
         * @throws IllegalArgumentException If dimensions or timeout are invalid.
         * @throws IllegalStateException If [sdkInt] does not support hardware rendering.
         */
        @VisibleForTesting
        internal fun createForTesting(
            width: Int,
            height: Int,
            riveWorker: RiveWorker,
            frameSourceFactory: HardwareFrameSourceFactory,
            sdkInt: Int = Build.VERSION_CODES.Q,
            firstFrameTimeoutMillis: Long = 0L,
        ): HardwareRenderBuffer = HardwareRenderBuffer(
            width,
            height,
            riveWorker,
            frameSourceFactory,
            sdkInt,
            firstFrameTimeoutMillis,
        )
    }

    /**
     * Creates a GPU-backed offscreen render target.
     *
     * @param width Width in pixels.
     * @param height Height in pixels.
     * @param riveWorker Worker used for draw submission.
     * @throws IllegalArgumentException If width or height are not positive.
     * @throws IllegalStateException If hardware rendering is unsupported on this API level.
     * @throws RiveResourceClosedException If the owning Rive worker has been disposed.
     * @throws RiveRenderException If the hardware render surface cannot be created.
     */
    @Throws(
        IllegalArgumentException::class,
        IllegalStateException::class,
        RiveResourceClosedException::class,
        RiveRenderException::class
    )
    constructor(
        width: Int,
        height: Int,
        riveWorker: RiveWorker,
    ) : this(
        width,
        height,
        riveWorker,
        AndroidHardwareFrameSource,
        Build.VERSION.SDK_INT,
        FIRST_FRAME_TIMEOUT_MILLIS,
    )

    init {
        require(width > 0 && height > 0) { "HardwareRenderBuffer width/height must be > 0" }
        require(firstFrameTimeoutMillis >= 0L) {
            "HardwareRenderBuffer first-frame timeout must be >= 0"
        }
        check(sdkInt >= Build.VERSION_CODES.Q) {
            "Hardware bitmap rendering requires API ${Build.VERSION_CODES.Q}+"
        }
    }

    /** Emits a signal whenever a new frame is published by the callback path. */
    private val _frameAvailable = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Signal stream for newly published frames.
     *
     * Collect this flow to know when [consumeLatestBitmap] should be called.
     */
    val frameAvailable: SharedFlow<Unit> = _frameAvailable

    /** Platform source that publishes hardware bitmaps rendered through [surface]. */
    private val frameSource = frameSourceFactory.create(width, height, riveWorker)

    /** Destination surface used by the worker draw call. */
    val surface: RiveSurface
        get() = frameSource.surface

    /** Protects [pendingBitmap]/[currentBitmap] handoff. */
    private val bitmapLock = Any()

    /** First-frame readiness gate used by [render]. */
    private val firstFrameLatch = CountDownLatch(1)

    @Volatile
    private var isClosedFlag = false

    @Volatile
    private var firstFramePublished = false

    @Volatile
    private var imageReaderFailure: Throwable? = null

    /** Latest frame available to consumers. */
    private var currentBitmap: Bitmap? = null

    /** Newly published frame awaiting consumption. */
    private var pendingBitmap: Bitmap? = null

    private val closer = CloseOnce("HardwareRenderBuffer") {
        isClosedFlag = true
        firstFrameLatch.countDown()
        var cleanupFailure = runCleanupStep(null) { frameSource.stop() }
        val bitmapsToRecycle = synchronized(bitmapLock) {
            val pending = pendingBitmap
            val current = currentBitmap
            pendingBitmap = null
            currentBitmap = null
            if (pending === current) arrayOf(pending) else arrayOf(pending, current)
        }
        bitmapsToRecycle.forEach { bitmap ->
            if (bitmap != null && !bitmap.isRecycled) {
                cleanupFailure = runCleanupStep(cleanupFailure) { bitmap.recycle() }
            }
        }
        cleanupFailure = runCleanupStep(cleanupFailure) { surface.close() }
        cleanupFailure?.let { throw it }
    }

    override val closed: Boolean
        get() = closer.closed

    init {
        try {
            frameSource.setListener(object : HardwareFrameSource.Listener {
                override fun onFrame(bitmap: Bitmap) = publishFrame(bitmap)

                override fun onFailure(failure: Throwable) = reportFrameFailure(failure)
            })
        } catch (failure: Throwable) {
            // The failed instance never reaches its caller, so run its complete close path.
            runCleanupStep(failure) { closer.close() }
            throw failure
        }
    }

    /** Closes this buffer and its owned render surface. */
    override fun close() = closer.close()

    /**
     * Runs one cleanup operation while retaining an earlier failure for eventual rethrow.
     *
     * @param firstFailure The first failure from an earlier cleanup step, if any.
     * @param cleanup Cleanup operation to attempt.
     * @return The first failure encountered, with any later failure added as suppressed.
     */
    private inline fun runCleanupStep(
        firstFailure: Throwable?,
        cleanup: () -> Unit,
    ): Throwable? = try {
        cleanup()
        firstFailure
    } catch (failure: Throwable) {
        if (firstFailure == null) {
            failure
        } else {
            if (failure !== firstFailure) {
                firstFailure.addSuppressed(failure)
            }
            firstFailure
        }
    }

    /**
     * Enqueues rendering into this hardware surface.
     *
     * The first call waits (bounded) for initial frame publication to keep startup deterministic.
     *
     * @throws RiveResourceClosedException If this buffer, its surface, [artboard], or
     *    [stateMachine] has been closed, or if the owning Rive worker has been disposed.
     * @throws RiveIncompatibleResourceException If [artboard] or [stateMachine] are not owned by
     *    this buffer's worker, or if [stateMachine] was not created from [artboard].
     * @throws RiveRenderException If first-frame publication times out or hardware image
     *    acquisition fails.
     */
    @Throws(
        RiveIncompatibleResourceException::class,
        RiveResourceClosedException::class,
        RiveRenderException::class
    )
    fun render(
        artboard: Artboard,
        stateMachine: StateMachine,
        fit: Fit = RenderingDefaults.defaultFit(),
        clearColor: Int = RenderingDefaults.CLEAR_COLOR
    ) {
        closer.checkOpen()
        surface.checkOpen()
        artboard.checkOpen()
        stateMachine.checkOpen()
        artboard.requireOwnedBy(riveWorker)
        stateMachine.requireFromArtboard(artboard)

        traceSection("Rive/RenderBuffer/Render") {
            traceSection("Rive/RenderBuffer/Hardware/Draw") {
                riveWorker.draw(
                    artboard.artboardHandle,
                    stateMachine.stateMachineHandle,
                    surface,
                    fit,
                    clearColor
                )
            }
            if (!firstFramePublished) {
                traceSection("Rive/RenderBuffer/Hardware/WaitFirstFrame") {
                    waitForFirstFrame()
                }
            }
        }
    }

    /**
     * Returns the latest published bitmap, or null when no frame has been published yet.
     *
     * This is a consume/swap API: when a newer frame exists, prior consumed bitmaps may be
     * superseded and recycled.
     *
     * @return The latest bitmap, or null if no frame is available.
     * @throws RiveResourceClosedException If this buffer has been closed.
     * @throws RiveRenderException If hardware image acquisition has failed.
     */
    @Throws(RiveResourceClosedException::class, RiveRenderException::class)
    fun consumeLatestBitmap(): Bitmap? {
        closer.checkOpen()
        val failure = imageReaderFailure
        if (failure != null) {
            throw RiveRenderException(
                "Hardware ImageReader failed while acquiring a frame; recreate HardwareRenderBuffer",
                failure
            )
        }
        return traceSection("Rive/RenderBuffer/ToBitmap") {
            synchronized(bitmapLock) {
                val pending = pendingBitmap
                if (pending != null) {
                    pendingBitmap = null
                    val previousCurrent = currentBitmap
                    currentBitmap = pending
                    if (previousCurrent != null &&
                        previousCurrent !== pending &&
                        !previousCurrent.isRecycled
                    ) {
                        previousCurrent.recycle()
                    }
                }
                currentBitmap
            }
        }
    }

    private fun waitForFirstFrame() {
        if (firstFramePublished) {
            return
        }
        val success = firstFrameLatch.await(firstFrameTimeoutMillis, TimeUnit.MILLISECONDS)
        val failure = imageReaderFailure
        if (failure != null) {
            throw RiveRenderException(
                "Hardware ImageReader failed while acquiring a frame; recreate HardwareRenderBuffer",
                failure
            )
        }
        if (success && firstFramePublished) {
            return
        }
        throw RiveRenderException(
            "No hardware image available after render (timed out waiting for ImageReader frame)"
        )
    }

    /** Publishes [bitmap], replacing and recycling any frame still waiting to be consumed. */
    private fun publishFrame(bitmap: Bitmap) {
        if (isClosedFlag) {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
            return
        }
        traceSection("Rive/RenderBuffer/Hardware/Callback/PublishBitmap") {
            synchronized(bitmapLock) {
                if (isClosedFlag) {
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                    return@synchronized
                }
                val previousPending = pendingBitmap
                pendingBitmap = bitmap
                if (previousPending != null &&
                    previousPending !== bitmap &&
                    !previousPending.isRecycled
                ) {
                    previousPending.recycle()
                }
                firstFramePublished = true
                firstFrameLatch.countDown()
            }
            _frameAvailable.tryEmit(Unit)
        }
    }

    /** Records [failure] and releases a caller waiting for first-frame publication. */
    private fun reportFrameFailure(failure: Throwable) {
        if (isClosedFlag) {
            return
        }
        RiveLog.e(TAG, failure) {
            if (failure is Error) {
                "Fatal error in ImageReader callback while publishing hardware frame"
            } else {
                "ImageReader callback failed while publishing hardware frame"
            }
        }
        imageReaderFailure = failure
        firstFrameLatch.countDown()
    }
}

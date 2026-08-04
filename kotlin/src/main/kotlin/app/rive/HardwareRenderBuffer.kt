package app.rive

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import app.rive.core.CheckableAutoCloseable
import app.rive.core.CloseOnce
import app.rive.core.ImageReaderSurface
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
 *
 * @param width Width in pixels.
 * @param height Height in pixels.
 * @param riveWorker Worker used for draw submission.
 * @throws IllegalArgumentException If width or height are not positive.
 * @throws IllegalStateException If hardware rendering is unsupported on this API level.
 * @throws RiveResourceClosedException If the owning Rive worker has been disposed.
 * @throws RiveRenderException If the hardware render surface cannot be created.
 */
@ExperimentalHardwareBitmapRendering
@RequiresApi(Build.VERSION_CODES.Q)
class HardwareRenderBuffer @Throws(
    IllegalArgumentException::class,
    IllegalStateException::class,
    RiveResourceClosedException::class,
    RiveRenderException::class
) constructor(
    val width: Int,
    val height: Int,
    private val riveWorker: RiveWorker
) : CheckableAutoCloseable {
    companion object {
        private const val TAG = "Rive/RenderBuffer/Hardware"
        private const val FIRST_FRAME_TIMEOUT_MILLIS = 250L

        /** @return true when hardware bitmap rendering is supported on this API level. */
        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    init {
        require(width > 0 && height > 0) { "HardwareRenderBuffer width/height must be > 0" }
        check(isSupported()) {
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

    /** Receives rendered frames through [surface]. */
    private val imageReader: ImageReader =
        ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            2,
            HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
        )

    /** Dedicated callback thread for ImageReader callbacks and acquisition work. */
    private val imageReaderThread = HandlerThread("Rive/ImageReader")

    /** Destination surface used by the worker draw call. */
    val surface: RiveSurface

    /** Explicit SRGB color interpretation for wrapped hardware bitmaps. */
    private val srgbColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)

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

    init {
        val imageReaderSurface = ImageReaderSurface(imageReader)
        imageReaderThread.start()
        var createdSurface: RiveSurface? = null
        try {
            val imageReaderHandler = Handler(imageReaderThread.looper)
            createdSurface = riveWorker.createRiveSurface(imageReaderSurface)
            imageReader.setOnImageAvailableListener({ reader ->
                onImageAvailable(reader)
            }, imageReaderHandler)
            surface = createdSurface
        } catch (failure: Throwable) {
            try {
                // Once created, the RiveSurface owns the ImageReaderSurface. Before that point,
                // construction retains responsibility for closing it.
                createdSurface?.close() ?: imageReaderSurface.close()
            } catch (cleanupFailure: Throwable) {
                failure.addSuppressed(cleanupFailure)
            }
            shutdownImageReaderThread()
            throw failure
        }
    }

    private val closer = CloseOnce("HardwareRenderBuffer") {
        isClosedFlag = true
        firstFrameLatch.countDown()
        imageReader.setOnImageAvailableListener(null, null)
        shutdownImageReaderThread()
        synchronized(bitmapLock) {
            pendingBitmap?.let { if (!it.isRecycled) it.recycle() }
            currentBitmap?.let { if (!it.isRecycled) it.recycle() }
            pendingBitmap = null
            currentBitmap = null
        }
        surface.close()
    }

    override val closed: Boolean
        get() = closer.closed

    /** Closes this buffer and its owned render surface. */
    override fun close() = closer.close()

    /** Stops the ImageReader callback thread and waits briefly for its queued work to finish. */
    private fun shutdownImageReaderThread() {
        imageReaderThread.quitSafely()
        try {
            imageReaderThread.join(1000)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
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
        val success = firstFrameLatch.await(FIRST_FRAME_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
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

    private fun onImageAvailable(reader: ImageReader) {
        if (isClosedFlag) {
            return
        }
        try {
            val image = traceSection(
                "Rive/RenderBuffer/Hardware/Callback/AcquireLatestImage"
            ) {
                reader.acquireLatestImage()
            } ?: return

            var hardwareBuffer: HardwareBuffer? = null
            val wrappedBitmap = try {
                traceSection("Rive/RenderBuffer/Hardware/Callback/WrapHardwareBuffer") {
                    val buffer = image.hardwareBuffer
                        ?: throw RiveRenderException("Image did not provide a HardwareBuffer")
                    hardwareBuffer = buffer
                    Bitmap.wrapHardwareBuffer(buffer, srgbColorSpace)
                        ?: throw RiveRenderException("Failed to wrap HardwareBuffer as Bitmap")
                }
            } finally {
                hardwareBuffer?.close()
                image.close()
            }

            traceSection("Rive/RenderBuffer/Hardware/Callback/PublishBitmap") {
                synchronized(bitmapLock) {
                    if (isClosedFlag) {
                        if (!wrappedBitmap.isRecycled) {
                            wrappedBitmap.recycle()
                        }
                        return@synchronized
                    }
                    val previousPending = pendingBitmap
                    pendingBitmap = wrappedBitmap
                    if (previousPending != null &&
                        previousPending !== wrappedBitmap &&
                        !previousPending.isRecycled
                    ) {
                        previousPending.recycle()
                    }
                    firstFramePublished = true
                    firstFrameLatch.countDown()
                }
                _frameAvailable.tryEmit(Unit)
            }
        } catch (e: Exception) {
            if (isClosedFlag) {
                return
            }
            RiveLog.e(TAG, e) { "ImageReader callback failed while publishing hardware frame" }
            imageReaderFailure = e
            firstFrameLatch.countDown()
        } catch (e: Error) {
            if (isClosedFlag) {
                throw e
            }
            RiveLog.e(TAG, e) {
                "Fatal error in ImageReader callback while publishing hardware frame"
            }
            imageReaderFailure = e
            firstFrameLatch.countDown()
            throw e
        }
    }
}

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
import app.rive.core.createRiveSurface
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
 * @throws IllegalArgumentException if width or height are not > 0.
 * @throws IllegalStateException if hardware rendering is unsupported on this API level.
 */
@ExperimentalHardwareBitmapRendering
@RequiresApi(Build.VERSION_CODES.Q)
class HardwareRenderBuffer(
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

    /** Dedicated callback thread for ImageReader callbacks and acquisition work. */
    private val imageReaderThread = HandlerThread("Rive/ImageReader").apply { start() }

    /** Handler bound to [imageReaderThread], required by ImageReader listener API. */
    private val imageReaderHandler = Handler(imageReaderThread.looper)

    /** Receives rendered frames through [surface]. */
    private val imageReader: ImageReader =
        ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            2,
            HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
        )

    /** Destination surface used by the worker draw call. */
    val surface: RiveSurface = riveWorker.createRiveSurface(ImageReaderSurface(imageReader))

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

    private val closer = CloseOnce("HardwareRenderBuffer") {
        isClosedFlag = true
        firstFrameLatch.countDown()
        imageReader.setOnImageAvailableListener(null, null)
        imageReaderThread.quitSafely()
        try {
            imageReaderThread.join(1000)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
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

    init {
        imageReader.setOnImageAvailableListener({ reader ->
            onImageAvailable(reader)
        }, imageReaderHandler)
    }

    override fun close() = closer.close()

    /**
     * Enqueues rendering into this hardware surface.
     *
     * The first call waits (bounded) for initial frame publication to keep startup deterministic.
     *
     * @throws IllegalArgumentException If [artboard] or [stateMachine] are not owned by this
     *    buffer's worker, or if [stateMachine] was not created from [artboard].
     * @throws IllegalStateException If this buffer has been closed, its surface has been closed,
     *    or the worker has been released.
     * @throws RiveRenderException If first-frame publication times out or hardware image
     *    acquisition fails.
     */
    @Throws(
        IllegalArgumentException::class,
        IllegalStateException::class,
        RiveRenderException::class
    )
    fun render(
        artboard: Artboard,
        stateMachine: StateMachine,
        fit: Fit = RenderingDefaults.defaultFit(),
        clearColor: Int = RenderingDefaults.CLEAR_COLOR
    ) {
        check(!closed) { "HardwareRenderBuffer is closed" }
        require(artboard.isOwnedBy(riveWorker)) {
            "HardwareRenderBuffer and Artboard must use the same RiveWorker"
        }
        require(stateMachine.isOwnedBy(riveWorker)) {
            "HardwareRenderBuffer and StateMachine must use the same RiveWorker"
        }
        require(stateMachine.isFromArtboard(artboard)) {
            "HardwareRenderBuffer StateMachine must be created from the supplied Artboard"
        }

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
     */
    fun consumeLatestBitmap(): Bitmap? {
        check(!closed) { "HardwareRenderBuffer is closed" }
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

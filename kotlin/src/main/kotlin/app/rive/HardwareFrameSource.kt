package app.rive

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.RequiresApi
import app.rive.core.ImageReaderSurface
import app.rive.core.RiveSurface
import app.rive.core.RiveWorker
import app.rive.core.traceSection

/**
 * Supplies hardware bitmap frames and the Rive surface into which those frames are rendered.
 *
 * Implementations own their callback infrastructure, but [surface] remains open after [stop] so
 * [HardwareRenderBuffer] can dispose buffered bitmaps before closing the surface in queue order.
 */
internal interface HardwareFrameSource {
    /** Surface into which the owning Rive worker renders. */
    val surface: RiveSurface

    /**
     * Sets the receiver for subsequent frames and failures, or clears it when [listener] is null.
     *
     * @param listener Receiver for frame-source events, or null to stop delivering events.
     */
    fun setListener(listener: Listener?)

    /** Stops callback delivery and joins any callback thread owned by this source. */
    fun stop()

    /** Receives frames and acquisition failures from a [HardwareFrameSource]. */
    interface Listener {
        /**
         * Publishes a newly acquired hardware bitmap.
         *
         * @param bitmap Bitmap whose ownership transfers to the listener.
         */
        fun onFrame(bitmap: Bitmap)

        /**
         * Reports a failure while acquiring or wrapping a frame.
         *
         * @param failure Failure encountered by the frame source.
         */
        fun onFailure(failure: Throwable)
    }
}

/** Creates a [HardwareFrameSource] for a fixed-size hardware render buffer. */
internal fun interface HardwareFrameSourceFactory {
    /**
     * Creates a frame source owned by [riveWorker].
     *
     * @param width Frame width in pixels.
     * @param height Frame height in pixels.
     * @param riveWorker Worker that owns the returned source's surface.
     * @return The created frame source.
     * @throws RiveRenderException If the frame source or render surface cannot be created.
     * @throws RiveResourceClosedException If [riveWorker] has been disposed.
     */
    fun create(width: Int, height: Int, riveWorker: RiveWorker): HardwareFrameSource
}

/** Android [ImageReader]-backed implementation used by the public hardware buffer constructor. */
@RequiresApi(Build.VERSION_CODES.Q)
internal class AndroidHardwareFrameSource private constructor(
    private val imageReaderThread: HandlerThread,
    private val imageReader: ImageReader,
    override val surface: RiveSurface,
) : HardwareFrameSource {
    companion object : HardwareFrameSourceFactory {
        /**
         * Creates an Android frame source and transfers its [ImageReader] to the returned surface.
         *
         * @param width Frame width in pixels.
         * @param height Frame height in pixels.
         * @param riveWorker Worker that owns the returned Rive surface.
         * @return The created frame source.
         * @throws RiveRenderException If the reader or render surface cannot be created.
         * @throws RiveResourceClosedException If [riveWorker] has been disposed.
         */
        override fun create(
            width: Int,
            height: Int,
            riveWorker: RiveWorker,
        ): HardwareFrameSource {
            val thread = HandlerThread("Rive/ImageReader").apply { start() }
            var reader: ImageReader? = null
            var readerSurface: ImageReaderSurface? = null
            var createdSurface: RiveSurface? = null
            return try {
                val createdReader = ImageReader.newInstance(
                    width,
                    height,
                    PixelFormat.RGBA_8888,
                    2,
                    HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or
                            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE,
                )
                reader = createdReader
                val closeableSurface = ImageReaderSurface(createdReader)
                readerSurface = closeableSurface
                val surface = riveWorker.createRiveSurface(closeableSurface)
                createdSurface = surface
                AndroidHardwareFrameSource(thread, createdReader, surface)
            } catch (failure: Throwable) {
                try {
                    // A created RiveSurface owns the ImageReaderSurface; before that point, the
                    // frame-source factory retains responsibility for the reader-backed surface.
                    when {
                        createdSurface != null -> createdSurface.close()
                        readerSurface != null -> readerSurface.close()
                        else -> reader?.close()
                    }
                } catch (cleanupFailure: Throwable) {
                    failure.addSuppressed(cleanupFailure)
                }
                stopThread(thread)
                throw failure
            }
        }

        /** Stops [thread] and preserves interruption status if joining is interrupted. */
        private fun stopThread(thread: HandlerThread) {
            thread.quitSafely()
            // A callback may synchronously close its owner; joining the current thread would stall.
            if (Thread.currentThread() === thread) {
                return
            }
            try {
                thread.join(1_000L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private val imageReaderHandler = Handler(imageReaderThread.looper)
    private val srgbColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)

    @Volatile
    private var listener: HardwareFrameSource.Listener? = null

    override fun setListener(listener: HardwareFrameSource.Listener?) {
        this.listener = listener
        imageReader.setOnImageAvailableListener(
            if (listener == null) null else ImageReader.OnImageAvailableListener(::onImageAvailable),
            if (listener == null) null else imageReaderHandler,
        )
    }

    override fun stop() {
        try {
            setListener(null)
        } finally {
            // Listener removal can fail after the reader changes state; the thread is still ours.
            stopThread(imageReaderThread)
        }
    }

    /** Acquires, wraps, and publishes the latest image available from [reader]. */
    private fun onImageAvailable(reader: ImageReader) {
        val activeListener = listener ?: return
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

            activeListener.onFrame(wrappedBitmap)
        } catch (failure: Exception) {
            listener?.onFailure(failure)
        } catch (failure: Error) {
            listener?.onFailure(failure)
            throw failure
        }
    }
}

package app.rive.runtime.kotlin.renderers

import androidx.annotation.WorkerThread
import app.rive.RiveLog
import app.rive.core.traceSection
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.RendererType
import app.rive.runtime.kotlin.core.Rive

enum class PointerEvents {
    POINTER_DOWN, POINTER_UP, POINTER_MOVE, POINTER_EXIT
}

open class RiveArtboardRenderer(
    trace: Boolean = false,
    rendererType: RendererType = Rive.defaultRendererType,
    private var controller: RiveFileController,
) : Renderer(rendererType, trace) {
    companion object {
        const val TAG = "RiveL/RiveArtboardRenderer"
    }

    private val fit get() = controller.fit
    private val alignment get() = controller.alignment
    private val scaleFactor get() = controller.layoutScaleFactorActive

    init {
        RiveLog.d(TAG) { "Initializing." }
        controller.also {
            it.onStart = ::start
            it.acquire()
            // Add controller to the Renderer dependencies.
            // When the renderer is disposed, it'll `release()` `it`
            dependencies.add(it)
        }
    }

    @WorkerThread
    private fun resizeArtboard() {
        if (fit == Fit.LAYOUT) {
            traceSection("Rive/Layout/ResizeArtboard") {
                val newWidth = width / scaleFactor
                val newHeight = height / scaleFactor
                controller.activeArtboard?.apply {
                    width = newWidth
                    height = newHeight
                }
            }
        } else {
            traceSection("Rive/Layout/ResetArtboardSize") {
                controller.activeArtboard?.resetArtboardSize()
            }
        }
    }

    // Be aware of thread safety!
    @WorkerThread
    override fun draw() {
        // Deref and draw under frameLock
        synchronized(frameLock) {
            // Early out for deleted renderer or inactive controller.
            // Note: controller.isActive can change at any time, but
            // hasCppObject can only change while holding frameLock
            if (!hasCppObject || !controller.isActive) return

            // Protect both resize and draw operations with fileLock to prevent race conditions
            // with file/artboard changes on the UI thread. This matches the locking strategy
            // used in controller.advance(). Always acquire frameLock before fileLock.
            synchronized(controller.file?.fileLock ?: this) {
                if (!controller.isActive) return
                if (controller.requireArtboardResize.getAndSet(false)) {
                    resizeArtboard()
                }
                controller.activeArtboard?.draw(
                    cppPointer,
                    fit,
                    alignment,
                    scaleFactor = scaleFactor
                )
            }
        }
    }

    // Be aware of thread safety!
    @WorkerThread
    override fun advance(elapsed: Float) {
        if (!hasCppObject) {
            return
        }
        if (controller.isActive) {
            controller.advance(elapsed)
        }

        // Don't stop if we're queueing more inputs.
        synchronized(controller.startStopLock) {
            // Are we done playing?
            if (!controller.isAdvancing) {
                stopThread()
            }
        }
    }

    fun reset() {
        RiveLog.d(TAG) { "Reset." }
        controller.stopAnimations()
        controller.reset()
        stop()
        controller.selectArtboard()
        start()
    }
}

package app.rive.runtime.kotlin.renderers

import androidx.annotation.WorkerThread
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.RendererType
import app.rive.runtime.kotlin.core.Rive

enum class PointerEvents {
    POINTER_DOWN, POINTER_UP, POINTER_MOVE
}

open class RiveArtboardRenderer(
    trace: Boolean = false,
    rendererType: RendererType = Rive.defaultRendererType,
    private var controller: RiveFileController,
) : Renderer(rendererType, trace) {

    private val fit get() = controller.fit
    private val alignment get() = controller.alignment

    init {
        controller.also {
            it.onStart = ::start
            it.acquire()
            // Add controller to the Renderer dependencies.
            // When the renderer is disposed, it'll `release()` `it`
            dependencies.add(it)
        }
    }

    /// Note: This is happening in the render thread
    /// be aware of thread safety!
    @WorkerThread
    override fun draw() {
        if (!hasCppObject) {
            return
        }
        if (controller.isActive) {
            controller.activeArtboard?.drawSkia(cppPointer, fit, alignment)
        }
    }

    /// Note: This is happening in the render thread
    /// be aware of thread safety!
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
        controller.stopAnimations()
        controller.reset()
        stop()
        controller.selectArtboard()
        start()
    }

    override fun disposeDependencies() {
        // Lock to make sure things are disposed in an orderly manner.
        synchronized(controller.file?.lock ?: this) { super.disposeDependencies() }
    }
}

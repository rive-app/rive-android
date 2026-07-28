package app.rive.runtime.kotlin.renderers

import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import app.rive.RiveLog
import app.rive.core.traceSection
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.Artboard
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

    /**
     * Runs [block] with the active artboard while holding its current file's lock.
     *
     * Because a file (and its lock) can be replaced while this thread is waiting for the lock,
     * we retry acquisition until we have the lock for the current file. Alternatively, if the
     * controller has no active file or artboard, this returns `false` without running [block]. The
     * caller should check for `false` and handle that situation appropriately.
     *
     * @param block Work to run on the locked active artboard.
     * @return `true` if [block] ran, or `false` if there is no active file or artboard.
     */
    private fun withLockedActiveArtboard(block: Artboard.() -> Unit): Boolean {
        while (true) {
            val activeFile = controller.file ?: return false
            synchronized(activeFile.fileLock) {
                if (controller.file === activeFile) {
                    val activeArtboard = controller.activeArtboard ?: return false
                    activeArtboard.block()
                    return true
                }
            }
        }
    }

    /**
     * Applies artboard sizing based on the active fit mode before the next draw.
     *
     * `Fit.LAYOUT` can require a re-layout, so state machines advance by 0 to apply these changes
     * after their artboard receives its new dimensions.
     *
     * @return `true` if the resize was applied to the active artboard, or `false` if the renderer
     *    cannot currently apply it.
     */
    @WorkerThread
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    open fun resizeArtboard(): Boolean {
        // Apply any layout changes by advancing all held state machines by 0 without changing their
        // playback status.
        val applyLayout = {
            traceSection("Rive/Layout/AdvanceStateMachines") {
                controller.stateMachines.forEach { stateMachine ->
                    controller.resolveStateMachineAdvance(stateMachine, 0f)
                }
            }
        }

        return if (fit == Fit.LAYOUT) {
            traceSection("Rive/Layout/ResizeArtboard") {
                // Read surface dimensions under frameLock so delete() cannot null cppPointer between
                // hasCppObject checks and width/height dereference.
                val (newWidth, newHeight) =
                    synchronized(frameLock) {
                        if (!hasCppObject || !controller.isActive) {
                            null
                        } else {
                            Pair(width / scaleFactor, height / scaleFactor)
                        }
                    } ?: return@traceSection false

                // Acquire file lock only after the frameLock section to avoid lock-order inversion and
                // serialize artboard mutations with controller/file lifecycle operations.
                withLockedActiveArtboard {
                    val dimensionsChanged = width != newWidth || height != newHeight
                    width = newWidth
                    height = newHeight
                    if (dimensionsChanged) {
                        applyLayout()
                    }
                }
            }
        } else {
            traceSection("Rive/Layout/ResetArtboardSize") {
                withLockedActiveArtboard {
                    val oldWidth = width
                    val oldHeight = height
                    resetArtboardSize()
                    if (width != oldWidth || height != oldHeight) {
                        applyLayout()
                    }
                }
            }
        }
    }

    /**
     * Applies a pending artboard resize and preserves the request if it cannot currently be
     * handled.
     */
    private fun resizeArtboardIfNeeded() {
        if (!controller.requireArtboardResize.getAndSet(false)) {
            return
        }
        if (!resizeArtboard()) {
            controller.requireArtboardResize.set(true)
        }
    }

    // Be aware of thread safety!
    @WorkerThread
    override fun draw() {
        resizeArtboardIfNeeded()

        // Deref and draw under frameLock
        synchronized(frameLock) {
            // Early out for deleted renderer or inactive controller.
            if (!hasCppObject || !controller.isActive) return

            controller.activeArtboard?.draw(cppPointer, fit, alignment, scaleFactor = scaleFactor)
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

    override fun disposeDependencies() {
        // Lock to make sure things are disposed in an orderly manner.
        synchronized(controller.file?.fileLock ?: this) { super.disposeDependencies() }
    }
}

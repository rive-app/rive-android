package app.rive.runtime.kotlin

import android.graphics.PointF
import android.graphics.RectF
import androidx.annotation.WorkerThread
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Artboard
import app.rive.runtime.kotlin.core.Direction
import app.rive.runtime.kotlin.core.File
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.Helpers
import app.rive.runtime.kotlin.core.Loop
import app.rive.runtime.kotlin.core.PlayableInstance
import app.rive.runtime.kotlin.renderers.RendererSkia
import kotlin.DeprecationLevel.WARNING


enum class PointerEvents {
    POINTER_DOWN, POINTER_UP, POINTER_MOVE
}

open class RiveArtboardRenderer(
    // PUBLIC
    fit: Fit = Fit.CONTAIN,
    alignment: Alignment = Alignment.CENTER,
    loop: Loop = Loop.AUTO,
    // TODO: would love to get rid of these three fields here.
    var artboardName: String? = null,
    var animationName: String? = null,
    var stateMachineName: String? = null,
    var autoplay: Boolean = true,
    trace: Boolean = false,
    // Default state or override.
    customState: RiveFileController? = null,
) : RendererSkia(trace) {

    var controller: RiveFileController =
        (customState ?: RiveFileController(fit, alignment, loop)).apply {
            // Register the callback right away.
            onStart = ::start
        }
        set(value) {
            if (field == value) {
                return
            }
            field = value
            value.onStart = ::start
        }

    private var selectedArtboard: Artboard? = controller.selectedArtboard

    var targetBounds: RectF = RectF()
    var loop: Loop
        get() = controller.loop
        set(value) {
            controller.loop = value
        }
    var activeArtboard: Artboard?
        get() = controller.activeArtboard
        set(value) {
            controller.activeArtboard = value
        }
    val file: File? get() = controller.file
    val animations get() = controller.animations
    val playingAnimations get() = controller.playingAnimations
    val stateMachines get() = controller.stateMachines
    val playingStateMachines get() = controller.playingStateMachines

    var fit = controller.fit
        set(value) {
            field = value
            // make sure we draw the next frame even if we are not playing right now
            start()
        }

    var alignment = controller.alignment
        set(value) {
            field = value
            // make sure we draw the next frame even if we are not playing right now
            start()
        }

    /// Note: This is happening in the render thread
    /// be aware of thread safety!
    @WorkerThread
    override fun draw() {
        if (!hasCppObject) {
            return
        }
        activeArtboard?.drawSkia(
            cppPointer, fit, alignment
        )
    }

    /// Note: This is happening in the render thread
    /// be aware of thread safety!
    @WorkerThread
    override fun advance(elapsed: Float) {
        if (!hasCppObject) {
            return
        }
        controller.advance(elapsed)
        // Are we done playing?
        if (!controller.hasPlayingAnimations) {
            stopThread()
        }
    }


    // PUBLIC FUNCTIONS
    fun setRiveFile(file: File) {
        controller.reset()
        controller.file = file
        // The Renderer takes care of disposing of this file.
        dependencies.add(file)
        selectArtboard()
    }

    fun setArtboardByName(artboardName: String?) {
        if (this.artboardName == artboardName) {
            return
        }

        controller.stopAnimations()
        this.artboardName = artboardName
        selectArtboard()
    }

    fun artboardBounds(): RectF = activeArtboard?.bounds ?: RectF()

    fun reset() {
        controller.stopAnimations()
        stop()
        selectArtboard()
        start()
    }

    @Deprecated(
        "Use RiveFileController directly",
        level = WARNING, replaceWith =
        ReplaceWith("controller.play(animationNames, loop, direction, areStateMachines, settleInitialState)")
    )
    fun play(
        animationNames: List<String>,
        loop: Loop = Loop.AUTO,
        direction: Direction = Direction.AUTO,
        areStateMachines: Boolean = false,
        settleInitialState: Boolean = true,
    ) {
        controller.play(animationNames, loop, direction, areStateMachines, settleInitialState)
    }

    @Deprecated(
        "Use RiveFileController directly",
        level = WARNING, replaceWith =
        ReplaceWith("controller.play(animationName, loop, direction, isStateMachine, settleInitialState)")
    )
    fun play(
        animationName: String,
        loop: Loop = Loop.AUTO,
        direction: Direction = Direction.AUTO,
        isStateMachine: Boolean = false,
        settleInitialState: Boolean = true,
    ) {
        controller.play(animationName, loop, direction, isStateMachine, settleInitialState)
    }

    @Deprecated(
        "Use RiveFileController directly",
        level = WARNING,
        replaceWith = ReplaceWith("controller.play(loop, direction, settleInitialState)")
    )
    fun play(
        loop: Loop = Loop.AUTO,
        direction: Direction = Direction.AUTO,
        settleInitialState: Boolean = true,
    ) {
        controller.play(loop, direction, settleInitialState)
    }

    @Deprecated(
        "Use RiveFileController directly",
        level = WARNING, replaceWith = ReplaceWith("controller.pause()")
    )
    fun pause() {
        controller.pause()
    }

    @Deprecated(
        "Use RiveFileController directly",
        level = WARNING,
        replaceWith = ReplaceWith("controller.pause(animationNames, areStateMachines)"),
    )
    fun pause(animationNames: List<String>, areStateMachines: Boolean = false) {
        controller.pause(animationNames, areStateMachines)
    }

    @Deprecated(
        "Use RiveFileController directly",
        level = WARNING,
        replaceWith = ReplaceWith("controller.pause(animationName, isStateMachine)"),
    )
    fun pause(animationName: String, isStateMachine: Boolean = false) {
        controller.pause(animationName, isStateMachine)
    }

    @Deprecated(
        "Use RiveFileController directly",
        level = WARNING, replaceWith = ReplaceWith("controller.stopAnimations()"),
    )
    fun stopAnimations() {
        controller.stopAnimations()
    }

    @Deprecated(
        "Use RiveFileController directly",
        level = WARNING,
        replaceWith = ReplaceWith("controller.stopAnimations(animationNames, areStateMachines)"),
    )
    fun stopAnimations(animationNames: List<String>, areStateMachines: Boolean = false) {
        controller.stopAnimations(animationNames, areStateMachines)
    }

    @Deprecated(
        "Use RiveFileController directly",
        level = WARNING,
        replaceWith = ReplaceWith("controller.stopAnimations(animationName, isStateMachine)"),
    )
    fun stopAnimations(animationName: String, isStateMachine: Boolean = false) {
        controller.stopAnimations(animationName, isStateMachine)
    }

    @Deprecated(
        "Use RiveFileController directly",
        level = WARNING,
        replaceWith = ReplaceWith("controller.fireState(stateMachineName, inputName)"),
    )
    fun fireState(stateMachineName: String, inputName: String) {
        controller.fireState(stateMachineName, inputName)
    }

    @Deprecated(
        "Use RiveFileController directly",
        level = WARNING,
        replaceWith =
        ReplaceWith("controller.setBooleanState(stateMachineName, inputName, value)"),
    )
    fun setBooleanState(stateMachineName: String, inputName: String, value: Boolean) {
        controller.setBooleanState(stateMachineName, inputName, value)
    }

    @Deprecated(
        "Use RiveFileController directly",
        level = WARNING,
        replaceWith =
        ReplaceWith("controller.setNumberState(stateMachineName, inputName, value)"),
    )
    fun setNumberState(stateMachineName: String, inputName: String, value: Float) {
        controller.setNumberState(stateMachineName, inputName, value)
    }

    fun pointerEvent(eventType: PointerEvents, x: Float, y: Float) {
        /// TODO: once we start composing artboards we may need x,y offsets here...
        val artboardEventLocation = Helpers.convertToArtboardSpace(
            targetBounds,
            PointF(x, y),
            fit,
            alignment,
            artboardBounds()
        )
        controller.stateMachines.forEach {

            when (eventType) {
                PointerEvents.POINTER_DOWN -> it.pointerDown(
                    artboardEventLocation.x,
                    artboardEventLocation.y
                )

                PointerEvents.POINTER_UP -> it.pointerUp(
                    artboardEventLocation.x,
                    artboardEventLocation.y
                )

                PointerEvents.POINTER_MOVE -> it.pointerMove(
                    artboardEventLocation.x,
                    artboardEventLocation.y
                )

            }
            controller.play(it, settleStateMachineState = false)
        }
    }

    private fun selectArtboard() {
        controller.file?.let { file ->
            artboardName?.let { artboardName ->
                selectedArtboard = file.artboard(artboardName)
                selectedArtboard?.let { selectedArtboard ->
                    setArtboard(selectedArtboard)
                }

            } ?: run {
                selectedArtboard = file.firstArtboard
                selectedArtboard?.let { selectedArtboard ->
                    setArtboard(selectedArtboard)
                }
            }
        }
    }

    private fun setArtboard(artboard: Artboard) {
        // This goes straight to setting the State.
        this.activeArtboard = artboard

        if (autoplay) {
            animationName?.let { animationName ->
                controller.play(animationName = animationName)
            } ?: run {
                stateMachineName?.let { stateMachineName ->
                    // With autoplay, we default to settling the initial state
                    controller.play(
                        animationName = stateMachineName,
                        isStateMachine = true,
                        settleInitialState = true
                    )
                } ?: run {
                    // With autoplay, we default to settling the initial state
                    controller.play(settleInitialState = true)
                }

            }
        } else {
            this.activeArtboard?.advance(0f)
            start()
        }
    }

    @Deprecated(
        "Use RiveFileController.Listener",
        level = WARNING,
        replaceWith = ReplaceWith("Replace with RiveController.Listener")
    )
    interface Listener {
        fun notifyPlay(animation: PlayableInstance)
        fun notifyPause(animation: PlayableInstance)
        fun notifyStop(animation: PlayableInstance)
        fun notifyLoop(animation: PlayableInstance)
        fun notifyStateChanged(stateMachineName: String, stateName: String)
    }
}

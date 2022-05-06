package app.rive.runtime.kotlin.core

import app.rive.runtime.kotlin.core.errors.AnimationException
import app.rive.runtime.kotlin.core.errors.RiveException
import app.rive.runtime.kotlin.core.errors.StateMachineException


/**
 * [Artboard]s as designed in the Rive animation editor.
 *
 * This object has a counterpart in c++, which implements a lot of functionality.
 * The [cppPointer] keeps track of this relationship.
 *
 * [Artboard]s provide access to available [Animation]s, and some basic properties.
 * You can [draw] artboards using a [Renderer] that is tied to a canvas.
 *
 * The constructor uses a [cppPointer] to point to its c++ counterpart object.
 */
class Artboard(val cppPointer: Long) {
    private external fun cppName(cppPointer: Long): String
    private external fun cppFirstAnimation(cppPointer: Long): Long

    private external fun cppAnimationByIndex(cppPointer: Long, index: Int): Long
    private external fun cppAnimationByName(cppPointer: Long, name: String): Long
    private external fun cppAnimationCount(cppPointer: Long): Int

    private external fun cppFirstStateMachine(cppPointer: Long): Long
    private external fun cppStateMachineByIndex(cppPointer: Long, index: Int): Long
    private external fun cppStateMachineByName(cppPointer: Long, name: String): Long
    private external fun cppStateMachineCount(cppPointer: Long): Int

    private external fun cppAdvance(cppPointer: Long, elapsedTime: Float): Boolean

    // TODO: this will be a cppDraw call after we remove our old renderer.
    private external fun cppDrawSkia(
        cppPointer: Long, rendererPointer: Long
    )

    private external fun cppDrawSkiaAligned(
        cppPointer: Long, rendererPointer: Long,
        fit: Fit, alignment: Alignment,
    )

    private external fun cppBounds(cppPointer: Long): Long
    private external fun cppDelete(cppPointer: Long)

    /**
     * Get the [name] of the Artboard.
     */
    val name: String
        get() = cppName(cppPointer)

    /**
     * Get the first [Animation] of the [Artboard].
     *
     * If you use more than one animation, it is preferred to use the [animation] functions.
     */
    val firstAnimation: LinearAnimationInstance
        @Throws(RiveException::class)
        get() {
            val animationPointer = cppFirstAnimation(cppPointer)
            if (animationPointer == 0L) {
                throw AnimationException("No Animations found.")
            }
            return LinearAnimationInstance(
                animationPointer
            )
        }

    /**
     * Get the animation at a given [index] in the [Artboard].
     *
     * This starts at 0.
     */
    @Throws(RiveException::class)
    fun animation(index: Int): LinearAnimationInstance {
        val animationPointer = cppAnimationByIndex(cppPointer, index)
        if (animationPointer == 0L) {
            throw AnimationException("No Animation found at index $index.")
        }
        return LinearAnimationInstance(
            animationPointer
        )
    }

    /**
     * Get the animation with a given [name] in the [Artboard].
     */
    @Throws(RiveException::class)
    fun animation(name: String): LinearAnimationInstance {
        val animationPointer = cppAnimationByName(cppPointer, name)
        if (animationPointer == 0L) {
            throw AnimationException(
                "Animation \"$name\" not found. " +
                        "Available Animations: ${animationNames.map { "\"$it\"" }}\""
            )
        }
        return LinearAnimationInstance(
            animationPointer
        )
    }

    /**
     * Get the first [StateMachine] of the [Artboard].
     *
     * If you use more than one animation, it is preferred to use the [stateMachine] functions.
     */
    val firstStateMachine: StateMachineInstance
        @Throws(RiveException::class)
        get() {
            val stateMachinePointer = cppFirstStateMachine(cppPointer)
            if (stateMachinePointer == 0L) {
                throw StateMachineException("No StateMachines found.")
            }
            return StateMachineInstance(
                stateMachinePointer
            )
        }


    /**
     * Get the animation at a given [index] in the [Artboard].
     *
     * This starts at 0.
     */
    @Throws(RiveException::class)
    fun stateMachine(index: Int): StateMachineInstance {
        val stateMachinePointer = cppStateMachineByIndex(cppPointer, index)
        if (stateMachinePointer == 0L) {
            throw StateMachineException("No StateMachine found at index $index.")
        }
        return StateMachineInstance(
            stateMachinePointer
        )
    }

    /**
     * Get the animation with a given [name] in the [Artboard].
     */
    @Throws(RiveException::class)
    fun stateMachine(name: String): StateMachineInstance {
        val stateMachinePointer = cppStateMachineByName(cppPointer, name)
        if (stateMachinePointer == 0L) {
            throw StateMachineException("No StateMachine found with name $name.")
        }
        return StateMachineInstance(
            stateMachinePointer
        )
    }

    /**
     * Get the number of animations stored inside the [Artboard].
     */
    val animationCount: Int
        get() = cppAnimationCount(cppPointer)

    /**
     * Get the number of state machines stored inside the [Artboard].
     */
    val stateMachineCount: Int
        get() = cppStateMachineCount(cppPointer)

    /**
     * Advancing the artboard updates the layout for all dirty components contained in the [Artboard]
     * updates the positions forces all components in the [Artboard] to be laid out.
     *
     * Components are all the shapes, bones and groups of an [Artboard].
     * Whenever components are added to an artboard, for example when an artboard is first loaded, they are considered dirty.
     * Whenever animations change properties of components, move a shape or change a color, they are marked as dirty.
     *
     * Before any changes to components will be visible in the next rendered frame, the artbaord needs to be [advance]d.
     *
     * [elapsedTime] is currently not taken into account.
     */
    fun advance(elapsedTime: Float): Boolean {
        return cppAdvance(cppPointer, elapsedTime)
    }

    /**
     * Draw the the artboard to the [renderer].
     */
    fun drawSkia(rendererAddress: Long) {
        cppDrawSkia(cppPointer, rendererAddress)
    }

    /**
     * Draw the the artboard to the [renderer].
     * Also align the artboard to the render surface
     */
    fun drawSkia(rendererAddress: Long, fit: Fit, alignment: Alignment) {
        cppDrawSkiaAligned(cppPointer, rendererAddress, fit, alignment)
    }

    /**
     * Get the bounds of Artboard as defined in the rive editor.
     */
    val bounds: AABB
        get() = AABB(cppBounds(cppPointer))

    /**
     * Get the names of the animations in the artboard.
     */
    val animationNames: List<String>
        get() = (0 until animationCount).map { animation(it).name }

    /**
     * Get the names of the stateMachines in the artboard.
     */
    val stateMachineNames: List<String>
        get() = (0 until stateMachineCount).map { stateMachine(it).name }


    protected fun finalize() {
        // If we are done with the artboard, and the artboard is an artboard instance, lets get rid of it.
        // Otherwise we are letting the cpp manage the artboard lifecycle
        if (cppPointer != -1L) {
            cppDelete(cppPointer)
        }
    }
}
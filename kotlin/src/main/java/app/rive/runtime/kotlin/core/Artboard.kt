package app.rive.runtime.kotlin.core

import android.graphics.Canvas

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

    private external fun cppAdvance(cppPointer: Long, elapsedTime: Float)
    private external fun cppDraw(
        cppPointer: Long,
        rendererPointer: Long,
        renderer: Renderer,
        canvas: Canvas
    )

    private external fun cppBounds(cppPointer: Long): Long

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
    val firstAnimation: Animation
        @Throws(RiveException::class)
        get() {
            var animationPointer = cppFirstAnimation(cppPointer);
            if (animationPointer == 0L) {
                throw RiveException("No Animations found.")
            }
            return Animation(
                animationPointer
            )
        }


    /**
     * Get the animation at a given [index] in the [Artboard].
     *
     * This starts at 0.
     */
    @Throws(RiveException::class)
    fun animation(index: Int): Animation {
        var animationPointer = cppAnimationByIndex(cppPointer, index)
        if (animationPointer == 0L) {
            throw RiveException("No Animation found at index $index.")
        }
        return Animation(
            animationPointer
        )
    }

    /**
     * Get the animation with a given [name] in the [Artboard].
     */
    @Throws(RiveException::class)
    fun animation(name: String): Animation {
        var animationPointer = cppAnimationByName(cppPointer, name)
        if (animationPointer == 0L) {
            throw RiveException("No Animation found with name $name.")
        }
        return Animation(
            animationPointer
        )
    }

    /**
     * Get the first [StateMachine] of the [Artboard].
     *
     * If you use more than one animation, it is preferred to use the [stateMachine] functions.
     */
    val firstStateMachine: StateMachine
        @Throws(RiveException::class)
        get() {
            var stateMachinePointer = cppFirstStateMachine(cppPointer);
            if (stateMachinePointer == 0L) {
                throw RiveException("No StateMachines found.")
            }
            return StateMachine(
                stateMachinePointer
            )
        }


    /**
     * Get the animation at a given [index] in the [Artboard].
     *
     * This starts at 0.
     */
    @Throws(RiveException::class)
    fun stateMachine(index: Int): StateMachine {
        var stateMachinePointer = cppStateMachineByIndex(cppPointer, index)
        if (stateMachinePointer == 0L) {
            throw RiveException("No StateMachine found at index $index.")
        }
        return StateMachine(
            stateMachinePointer
        )
    }

    /**
     * Get the animation with a given [name] in the [Artboard].
     */
    @Throws(RiveException::class)
    fun stateMachine(name: String): StateMachine {
        var stateMachinePointer = cppStateMachineByName(cppPointer, name)
        if (stateMachinePointer == 0L) {
            throw RiveException("No StateMachine found with name $name.")
        }
        return StateMachine(
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
    fun advance(elapsedTime: Float) {
        cppAdvance(cppPointer, elapsedTime)
    }

    /**
     * Draw the the artboard to the [renderer].
     */
    fun draw(renderer: Renderer) {
        cppDraw(cppPointer, renderer.cppPointer, renderer, renderer.canvas)
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
}
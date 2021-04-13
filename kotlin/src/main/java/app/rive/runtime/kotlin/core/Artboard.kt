package app.rive.runtime.kotlin.core

import android.graphics.Canvas

/**
 * [Artboard]s as designed in the Rive animation editor.
 *
 * This object has a counterpart in c++, which implements a lot of functionality.
 * The [nativePointer] keeps track of this relationship.
 *
 * [Artboard]s provide access to available [Animation]s, and some basic properties.
 * You can [draw] artboards using a [Renderer] that is tied to a canvas.
 *
 * The constructor uses a [nativePointer] to point to its c++ counterpart object.
 */
class Artboard(val nativePointer: Long) {
    private external fun nativeName(nativePointer: Long): String
    private external fun nativeFirstAnimation(nativePointer: Long): Long
    private external fun nativeAnimationByIndex(nativePointer: Long, index: Int): Long
    private external fun nativeAnimationByName(nativePointer: Long, name: String): Long
    private external fun nativeAnimationCount(nativePointer: Long): Int
    private external fun nativeAdvance(nativePointer: Long, elapsedTime: Float)
    private external fun nativeDraw(
        nativePointer: Long,
        rendererPointer: Long,
        renderer: Renderer,
        canvas: Canvas
    )

    private external fun nativeBounds(nativePointer: Long): Long

    /**
     * Get the [name] of the Artboard.
     */
    val name: String
        get() = nativeName(nativePointer)

    /**
     * Get the first [Animation] of the [Artboard].
     *
     * If you use more than one animation, it is preferred to use the [animation] functions.
     */
    val firstAnimation: Animation
        @Throws(RiveException::class)
        get() {
            var animationPointer = nativeFirstAnimation(nativePointer);
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
        var animationPointer = nativeAnimationByIndex(nativePointer, index)
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
        var animationPointer= nativeAnimationByName(nativePointer, name)
        if (animationPointer == 0L) {
            throw RiveException("No Animation found with name $name.")
        }
        return Animation(
            animationPointer
        )
    }

    /**
     * Get the number of animations stored inside the [Artboard].
     */
    val animationCount: Int
        get() = nativeAnimationCount(nativePointer)

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
        nativeAdvance(nativePointer, elapsedTime)
    }

    /**
     * Draw the the artboard to the [renderer].
     */
    fun draw(renderer: Renderer) {
        nativeDraw(nativePointer, renderer.nativePointer, renderer, renderer.canvas)
    }

    /**
     * Get the bounds of Artboard as defined in the rive editor.
     */
    val bounds: AABB
        get() =  AABB(nativeBounds(nativePointer))
}
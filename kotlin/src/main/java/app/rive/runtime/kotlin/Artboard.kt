package app.rive.runtime.kotlin

import android.graphics.Canvas

class Artboard(val nativePointer: Long) {
    //    NOTES:
    //    missing implementations: objects, find
    //    only linearAnimation is implemented

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

    fun name(): String {
        return nativeName(nativePointer)
    }

    fun firstAnimation(): Animation {
        return Animation(
            nativeFirstAnimation(nativePointer)
        )
    }

    fun animation(index: Int): Animation {
        return Animation(
            nativeAnimationByIndex(nativePointer, index)
        )
    }

    fun animation(name: String): Animation {
        return Animation(
            nativeAnimationByName(nativePointer, name)
        )
    }

    fun animationCount(): Int {
        return nativeAnimationCount(nativePointer)
    }

    fun advance(elapsedTime: Float) {
        nativeAdvance(nativePointer, elapsedTime)
    }

    fun draw(renderer: Renderer) {
        nativeDraw(nativePointer, renderer.nativePointer, renderer, renderer.canvas)
    }

    fun bounds(): AABB {
        return AABB(nativeBounds(nativePointer))
    }
}
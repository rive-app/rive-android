package app.rive.runtime.kotlin

import android.graphics.Canvas

class Artboard {
    //    NOTES:
    //    missing implementations: objects, find
    //    only linearAnimation is implemented
    var nativePointer: Long

    constructor(_nativePointer: Long) : super() {
        nativePointer = _nativePointer
    }

    external private fun nativeName(nativePointer: Long): String
    external private fun nativeFirstAnimation(nativePointer: Long): Long
    external private fun nativeAnimationByIndex(nativePointer: Long, index: Int): Long
    external private fun nativeAnimationByName(nativePointer: Long, name: String): Long
    external private fun nativeAnimationCount(nativePointer: Long): Int
    external private fun nativeAdvance(nativePointer: Long, elapsedTime: Float)
    external private fun nativeDraw(
        nativePointer: Long,
        rendererPointer: Long,
        renderer: Renderer,
        canvas: Canvas
    )

    external private fun nativeBounds(nativePointer: Long): Long

    companion object {
        init {
            System.loadLibrary("jnirivebridge")
        }
    }

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

    fun draw(renderer: Renderer, canvas: Canvas) {
        nativeDraw(nativePointer, renderer.nativePointer, renderer, canvas)
    }

    fun bounds(): AABB {
        return AABB(nativeBounds(nativePointer))
    }
}
package app.rive.runtime.kotlin.core

import android.os.SystemClock
import android.util.Log

class RendererSkia {
    private external fun cleanupJNI(cppPointer: Long)
    private external fun constructor(): Long
    private external fun startFrame(cppPointer: Long)
    private external fun initializeSkiaGL(cppPointer: Long)
    private external fun setViewport(cppPointer: Long, width: Int, height: Int)
    private external fun cppDraw(artboardPointer: Long, rendererPointer: Long)

    var cppPointer: Long = constructor()
        private set

    fun initializeSkia() {
        initializeSkiaGL(cppPointer)
    }

    fun cleanup() {
        cleanupJNI(cppPointer)
        cppPointer = 0
    }

    fun setViewport(width: Int, height: Int) {
        setViewport(cppPointer, width, height)
    }

    // TODO: this should be an abstract call.
    fun draw(artboard: Artboard) {
        // TODO: not sure we need to clear the background every frame?
        startFrame(cppPointer)
        cppDraw(artboard.cppPointer, cppPointer)
//        var start = SystemClock.elapsedRealtimeNanos()
//        artboard.drawSkia(this)
//        val now = SystemClock.elapsedRealtimeNanos()
//        Log.d("SKIA DRAW", "Frame: ${(now - start) / 1000000} ms")
    }
}
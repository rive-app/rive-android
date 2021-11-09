package app.rive.runtime.kotlin.core

class RendererOpenGL {
    private external fun constructor(): Long
    private external fun cleanupJNI(cppPointer: Long)
    private external fun startFrame(cppPointer: Long)
    private external fun setViewport(cppPointer: Long, width: Int, height: Int)

    private lateinit var file: File
    private var artboard: Artboard? = null
    private var animationInstance: LinearAnimationInstance? = null

    fun initFile(bytes: ByteArray) {
        this.file = File(bytes)
        artboard = file.firstArtboard.getInstance()
        artboard!!.advance(0.0f)
        animationInstance = LinearAnimationInstance(artboard!!.firstAnimation)
        animationInstance!!.advance(0.0f)
        animationInstance!!.apply(artboard!!)

    }

    var cppPointer: Long = 0

    init {
        cppPointer = constructor()
    }

    fun cleanup() {
        cleanupJNI(cppPointer)
        cppPointer = 0
    }

    fun setViewport(width: Int, height: Int) {
        setViewport(cppPointer, width, height)
    }

    fun draw() {
        artboard ?: return; animationInstance ?: return;

        startFrame(cppPointer)
        // TODO: elapsedTime
        animationInstance!!.advance(0.16f)
        animationInstance!!.apply(artboard!!)
        artboard!!.advance(0.16f)
        artboard!!.drawGL(this)
    }
}
package app.rive.runtime.kotlin.core

class RendererOpenGL {
    private external fun constructor(): Long
    private external fun cleanupJNI(cppPointer: Long)
    private external fun startFrame(cppPointer: Long)
    private external fun initializeGL(cppPointer: Long)
    private external fun setViewport(cppPointer: Long, width: Int, height: Int)

    var cppPointer: Long = 0

    private var file: File? = null
    private var artboard: Artboard? = null
    private var animationInstance: LinearAnimationInstance? = null

    fun initializeGL() {
        initializeGL(cppPointer)
    }

    fun initFile(bytes: ByteArray) {
        this.file?.run {
            // Cleanup the old file.
        }
        val f = File(bytes)
        this.file = f

        val ab = f.firstArtboard.getInstance()
        ab.advance(0.0f)
        this.artboard = ab

        val ai = LinearAnimationInstance(ab.firstAnimation)
        ai.advance(0.0f)
        ai.apply(artboard!!)
        this.animationInstance = ai
    }

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

    fun draw(elapsed: Float) {
        artboard ?: return; animationInstance ?: return;

        startFrame(cppPointer)
        animationInstance!!.advance(elapsed)
        animationInstance!!.apply(artboard!!)
        artboard!!.advance(elapsed)
        artboard!!.drawGL(this)
    }


    private fun fileCleanup() {

    }
}
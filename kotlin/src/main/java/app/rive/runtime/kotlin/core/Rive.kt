package app.rive.runtime.kotlin.core

import android.content.Context
import android.graphics.RectF
import app.rive.runtime.kotlin.fonts.FontHelper
import app.rive.runtime.kotlin.fonts.Fonts
import app.rive.runtime.kotlin.fonts.NativeFontHelper
import com.getkeepsafe.relinker.ReLinker

object Rive {
    private external fun cppInitialize()
    private external fun cppCalculateRequiredBounds(
        fit: Fit, alignment: Alignment,
        availableBounds: RectF,
        artboardBounds: RectF,
        requiredBounds: RectF,
        scaleFactor: Float
    )

    private const val RIVE_ANDROID = "rive-android"

    /**
     * Public getter for the default renderer type.
     *
     * This can be customized via [Rive.init].
     */
    var defaultRendererType: RendererType = RendererType.Rive
        private set

    /**
     * Initialises Rive.
     *
     * This loads the C++ libraries required to use Rive objects and then makes sure to initialize
     * the C++ environment.
     *
     * To handle loading .so files for the rive-android lib yourself, use [initializeCppEnvironment]
     * instead.
     *
     * @param defaultRenderer The default renderer to use when initializing [File] or
     *    [RiveAnimationView]. Defaults to [RendererType.Skia].
     */
    fun init(context: Context, defaultRenderer: RendererType = RendererType.Rive) {
        // NOTE: loadLibrary also allows us to specify a version, something we might want to take
        //       advantage of
        ReLinker.loadLibrary(context, RIVE_ANDROID)
        defaultRendererType = defaultRenderer
        initializeCppEnvironment()
    }

    /**
     * Initialises the JNI Bindings.
     *
     * We update the C++ environment with a pointer to the JavaVM so that it can interact with JVM
     * objects.
     *
     * Normally done as part of init, and only required if you are avoiding calling [init].
     */
    fun initializeCppEnvironment() {
        cppInitialize()
    }


    fun calculateRequiredBounds(
        fit: Fit,
        alignment: Alignment,
        availableBounds: RectF,
        artboardBounds: RectF,
        scaleFactor: Float = 1.0f,
    ): RectF {
        val requiredBounds = RectF()
        cppCalculateRequiredBounds(
            fit,
            alignment,
            availableBounds,
            artboardBounds,
            requiredBounds,
            scaleFactor
        )
        return requiredBounds
    }

    /**
     * Set a fallback font for the Rive runtime.
     *
     * @param byteArray The [ByteArray] bytes for a font file.
     * @return Whether the font was successfully registered.
     */
    fun setFallbackFont(byteArray: ByteArray): Boolean =
        NativeFontHelper.cppRegisterFallbackFont(byteArray)

    /**
     * Set a fallback font for the Rive runtime.
     *
     * @param opts The [Fonts.FontOpts] specifying the desired font characteristics. If not
     *    provided, default options are used.
     * @return Whether the font was successfully registered.
     */
    fun setFallbackFont(opts: Fonts.FontOpts? = null): Boolean {
        FontHelper.getFallbackFontBytes(opts)?.let { bytes ->
            return NativeFontHelper.cppRegisterFallbackFont(bytes)
        }
        return false
    }
}
package app.rive.runtime.kotlin.core

import android.content.Context
import android.graphics.RectF
import android.util.Log
import com.getkeepsafe.relinker.ReLinker

object Rive {
    private external fun cppInitialize()
    private external fun cppCalculateRequiredBounds(
        fit: Fit, alignment: Alignment,
        availableBounds: RectF,
        artboardBounds: RectF,
        requiredBounds: RectF
    )

    private const val RiveAndroid = "rive-android"

    /**
     * Public getter for the default renderer type.
     * This can be customized via [Rive.init]
     */
    var defaultRendererType: RendererType = RendererType.Skia
        private set

    /**
     * Initialises Rive.
     *
     * This loads the c++ libraries required to use Rive objects and then makes sure we
     * initialize our cpp environment.
     *
     * Specify the default renderer to be used when initializing [File] or [RiveAnimationView]
     * by providing a [defaultRenderer] value. This defaults to [RendererType.Skia]
     *
     * To handle loading .so files for the rive-android lib yourself, use [initializeCppEnvironment]
     * instead.
     */
    fun init(context: Context, defaultRenderer: RendererType = RendererType.Skia) {
        // NOTE: loadLibrary also allows us to specify a version, something we might want to take
        //       advantage of
        ReLinker
            // .log { Log.d("ReLinkerLogs", "(${Thread.currentThread().id}) $it") }
            .loadLibrary(context, RiveAndroid)
        defaultRendererType = defaultRenderer
        initializeCppEnvironment()
    }

    /**
     * Initialises the JNI Bindings.
     *
     * We update the c++ environment with a pointer to the JavaVM so that
     * it can interact with Java objects.
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
        artboardBounds: RectF
    ): RectF {
        val requiredBounds = RectF()
        cppCalculateRequiredBounds(
            fit,
            alignment,
            availableBounds,
            artboardBounds,
            requiredBounds
        )
        return requiredBounds
    }
}
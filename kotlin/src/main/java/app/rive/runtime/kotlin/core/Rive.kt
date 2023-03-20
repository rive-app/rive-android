package app.rive.runtime.kotlin.core

import android.content.Context
import android.graphics.RectF
import com.getkeepsafe.relinker.ReLinker

object Rive {
    private external fun cppInitialize()
    private external fun cppCalculateRequiredBounds(
        fit: Fit, alignment: Alignment,
        availableBounds: RectF,
        artboardBounds: RectF,
        requiredBounds: RectF
    )

    private const val JNIRiveBridge = "jnirivebridge"

    /**
     * Initialises Rive.
     *
     * This loads the c++ libraries required to use Rive objects and then makes sure we
     * initialize our cpp environment.
     *
     * To handle loading .so files for the jnirivebridge yourself, use [initializeCppEnvironment]
     * instead.
     */
    fun init(context: Context) {
        // NOTE: loadLibrary also allows us to specify a version, something we might want to take
        //       advantage of
        ReLinker.loadLibrary(context, JNIRiveBridge)
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
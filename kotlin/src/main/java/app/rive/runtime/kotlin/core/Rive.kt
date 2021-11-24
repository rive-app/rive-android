package app.rive.runtime.kotlin.core

import android.content.Context
import com.getkeepsafe.relinker.ReLinker


object Rive {
    private external fun cppInitialize()
    private external fun cppCalculateRequiredBounds(
        fit: Fit, alignment: Alignment,
        availableBoundsPointer: Long,
        artboardBoundsPointer: Long,
        requiredBoundsPointer: Long
    )

    private const val JNIRiveBridge = "jnirivebridge"

    /**
     * Initialises Rive.
     *
     * This loads the c++ libraries required to use Rive objects and then
     * updates the c++ environment with a pointer to the JavaVM so that
     * it can interact with Java objects.
     */
    fun init(context: Context) {
        // NOTE: loadLibrary also allows us to specify a version, something we might want to take
        //       advantage of
        ReLinker.loadLibrary(context, JNIRiveBridge);
        cppInitialize()
    }


    fun calculateRequiredBounds(
        fit: Fit,
        alignment: Alignment,
        availableBounds: AABB,
        artboardBounds: AABB
    ): AABB {
        val requiredBounds = AABB(0f, 0f)
        cppCalculateRequiredBounds(
            fit,
            alignment,
            availableBounds.cppPointer,
            artboardBounds.cppPointer,
            requiredBounds.cppPointer
        )
        return requiredBounds
    }
}
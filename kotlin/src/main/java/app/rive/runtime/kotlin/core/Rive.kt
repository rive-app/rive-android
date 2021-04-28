package app.rive.runtime.kotlin.core

object Rive {
    private external fun cppInitialize()
    private external fun cppCalculateRequiredBounds(
        fit: Fit, alignment: Alignment,
        availableBoundsPointer: Long,
        artboardBoundsPointer: Long,
        requiredBoundsPointer: Long
    )


    /**
     * Initialises Rive.
     *
     * This loads the c++ libraries required to use Rive objects and then
     * updates the c++ environment with a pointer to the JavaVM so that
     * it can interact with Java objects.
     */
    fun init() {
        System.loadLibrary("jnirivebridge")
        cppInitialize()
    }

    fun calculateRequiredBounds(
        fit: Fit,
        alignment: Alignment,
        availableBounds: AABB,
        artboardBounds: AABB
    ): AABB {
        var requiredBounds = AABB(0f, 0f)
        cppCalculateRequiredBounds(
            fit,
            alignment,
            availableBounds.cppPointer,
            artboardBounds.cppPointer,
            requiredBounds.cppPointer
        )
        return requiredBounds;
    }

}
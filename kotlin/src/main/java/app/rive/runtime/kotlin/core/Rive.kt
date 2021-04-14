package app.rive.runtime.kotlin.core

object Rive {
    private external fun nativeInitialize()
    private external fun nativeCalculateRequiredBounds(
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
        nativeInitialize()
    }

    fun calculateRequiredBounds(
        fit: Fit,
        alignment: Alignment,
        availableBounds: AABB,
        artboardBounds: AABB
    ): AABB {
        var requiredBounds = AABB(0f, 0f)
        nativeCalculateRequiredBounds(
            fit,
            alignment,
            availableBounds.nativePointer,
            artboardBounds.nativePointer,
            requiredBounds.nativePointer
        )
        return requiredBounds;
    }

}
package app.rive.runtime.kotlin.core

interface NativeObject {

    companion object {
        // Static const value for a pointer.
        const val NULL_POINTER = 0L
    }

    // This C++ object pointer
    var cppPointer: Long

    // Collection of native objects that are owned(created) by this.
    val dependencies: MutableCollection<NativeObject>?

    // Up to the implementer (interfaces cannot have external functions)
    fun cppDelete(pointer: Long)

    fun dispose() {
        dependencies?.forEach {
            it.dispose()
        }
        dependencies?.clear()

        // Do we want to warn/throw when deleting twice?
        if (cppPointer != NULL_POINTER) {
            cppDelete(cppPointer)
            cppPointer = NULL_POINTER
        }
    }
}
package app.rive.runtime.kotlin.core

import app.rive.runtime.kotlin.core.errors.RiveException

abstract class NativeObject(private var unsafeCppPointer: Long) {

    companion object {
        // Static const value for a pointer.
        const val NULL_POINTER = 0L
    }

    val hasCppObject: Boolean
        get() {
            return unsafeCppPointer != NULL_POINTER
        }

    var cppPointer: Long
        set(value) {
            unsafeCppPointer = value
        }
        get() {
            if (!hasCppObject) {
                // we are not using the objects toString, because that could itself call native methods
                throw RiveException(
                    "C++ object for ${this.javaClass.name}@${
                        Integer.toHexString(
                            this.hashCode()
                        )
                    } does not exist. See MEMORY_MANAGEMENT.md for more information."
                )
            }
            return unsafeCppPointer
        }

    // Collection of native objects that are owned(created) by this.
    val dependencies = mutableListOf<NativeObject>()

    // Up to the implementer (interfaces cannot have external functions)
    open fun cppDelete(pointer: Long) {}

    fun dispose() {
        dependencies.forEach {
            it.dispose()
        }
        dependencies.clear()

        // Do we want to warn/throw when deleting twice?
        if (hasCppObject) {
            cppDelete(unsafeCppPointer)
            unsafeCppPointer = NULL_POINTER
        }
    }
}
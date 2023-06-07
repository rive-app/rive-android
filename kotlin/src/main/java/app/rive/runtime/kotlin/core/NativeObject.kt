package app.rive.runtime.kotlin.core

import app.rive.runtime.kotlin.core.errors.RiveException
import java.util.concurrent.atomic.AtomicInteger


/**
 * NativeObject is a Kotlin object that's backed by a C++ counterpart via the JNI.
 * It keeps track of the current pointer value in its local variable [unsafeCppPointer].
 *
 * [unsafeCppPointer] is accessible via the [cppPointer] getter/setter.
 */
abstract class NativeObject(private var unsafeCppPointer: Long) : RefCount {

    companion object {
        /** Static const value for an empty pointer. */
        const val NULL_POINTER = 0L
    }

    /**
     * Whether this objects underlying pointer is still valid.
     */
    val hasCppObject get() = unsafeCppPointer != NULL_POINTER

    override var refs = AtomicInteger(1)
    /**
     * Getter/Setter for the underlying C++ pointer value.
     *
     * @throws RiveException if this object wraps a [unsafeCppPointer] set to [NULL_POINTER].
     */
    var cppPointer: Long
        set(value) {
            unsafeCppPointer = value
        }
        get() {
            if (!hasCppObject) {
                // we are not using the objects toString, because that could itself call native methods
                throw RiveException(
                    "C++ object for ${this.javaClass.name}@${
                        this.hashCode()
                    } does not exist. See MEMORY_MANAGEMENT.md for more information."
                )
            }
            return unsafeCppPointer
        }

    // Collection of native objects that are owned(created) by this.
    val dependencies = mutableListOf<RefCount>()

    // Up to the implementer (interfaces cannot have external functions)
    open fun cppDelete(pointer: Long) {}

    /**
     * Increments the references for this counter.
     *
     * @throws IllegalArgumentException if refs already is 0.
     */
    @Synchronized
    override fun acquire(): Int {
        val count = super.acquire()
        require(count >= 0)
        return count
    }

    /**
     * Decrements the reference counter.
     *
     * @throws IllegalArgumentException if refs already is 0.
     */
    @Synchronized
    override fun release(): Int {
        val count = super.release()
        require(count >= 0)

        if (count == 0 && hasCppObject) {
            dispose()
        }
        return count
    }

    /**
     * Disposes of this reference and potentially any of the dependents.
     *
     * Uses [refs] to keep track of how many objects are using this NativeObject.
     * If refs reaches 0 the object can be disposed.
     */
    @Synchronized
    private fun dispose() {
        require(refs.get() == 0)

        dependencies.apply {
            // Release all dependencies and clear the collection.
            // If anyone is holding a reference to one of this object's dependents, they will need
            // to also `release()` it to clean up memory.
            forEach { it.release() }
            clear()
        }
        cppDelete(unsafeCppPointer)
        unsafeCppPointer = NULL_POINTER
    }
}
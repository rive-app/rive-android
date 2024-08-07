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

    private var disposeStackTrace: Sequence<StackTraceElement>? = null

    companion object {
        /** Static const value for an empty pointer. */
        const val NULL_POINTER = 0L
    }

    /**
     * Whether this objects underlying pointer is still valid.
     */
    val hasCppObject get() = unsafeCppPointer != NULL_POINTER

    final override var refs = AtomicInteger(
        if (unsafeCppPointer == NULL_POINTER) 0 // null objects cannot be referenced.
        else 1
    )

    /**
     * Getter/Setter for the underlying C++ pointer value.
     *
     * @throws RiveException if this object wraps a [unsafeCppPointer] set to [NULL_POINTER].
     */
    var cppPointer: Long
        set(value) {
            unsafeCppPointer = value
        }
        @Throws(RiveException::class)
        get() {
            if (!hasCppObject) {
                val nativeObjectName = this.javaClass.simpleName
                val riveException = RiveException(
                    "Accessing disposed C++ object $nativeObjectName. "
                )

                riveException.stackTrace = buildCombinedStackTrace().toTypedArray()

                throw riveException
            }
            return unsafeCppPointer
        }

    // Collection of native objects that are owned(created) by this.
    val dependencies = mutableListOf<RefCount>()

    // Up to the implementer (interfaces cannot have external functions)
    open fun cppDelete(pointer: Long) {}


    /**
     * Builds a combined stack trace incorporating the disposal stack trace and the current access trace.
     * This helps diagnosing issues with invalid memory access.
     *
     * @return A list of [StackTraceElement] combining `dispose()` Stack Trace (if available)
     *              with the current one.
     */
    private fun buildCombinedStackTrace(): List<StackTraceElement> {
        val combinedTrace = mutableListOf<StackTraceElement>()

        // Append the disposal stack trace if available
        disposeStackTrace?.also { trace ->
            combinedTrace += StackTraceElement("Dispose_Trace", "Start", null, -1)
            combinedTrace += trace
            combinedTrace += StackTraceElement("Current_Trace", "Start", null, -1)
        }

        // Append the current stack trace
        combinedTrace += Helpers.getCurrentStackTrace()
            .drop(1) // Remove call to buildCombinedStackTrace()

        return combinedTrace
    }


    /**
     * Increments the references for this counter.
     *
     * @throws IllegalArgumentException if refs already is 0.
     */
    @Throws(IllegalArgumentException::class)
    @Synchronized
    override fun acquire(): Int {
        val count = super.acquire()
        require(count > 1) // Never acquire a disposed object.
        return count
    }

    /**
     * Decrements the reference counter.
     *
     * @throws IllegalArgumentException if refs already is 0.
     */
    @Throws(IllegalArgumentException::class)
    @Synchronized
    override fun release(): Int {
        val count = super.release()
        require(count >= 0) // Never release a disposed object.

        if (count == 0 && hasCppObject) {
            dispose()
        }
        return count
    }

    /**
     * Disposes of this reference and potentially any of the dependents.
     *
     * Uses [refs] to keep track of how many objects are using this NativeObject.
     * When refs == 0 the object will be disposed.
     *
     * @throws IllegalArgumentException if refs is != 0
     */
    @Throws(IllegalArgumentException::class)
    @Synchronized
    private fun dispose() {
        require(refs.get() == 0)

        disposeStackTrace = Helpers.getCurrentStackTrace()

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
package app.rive.runtime.kotlin.core

import java.util.concurrent.locks.ReentrantLock

/**
 * A boolean state machine input.
 *
 * @deprecated State machine inputs are deprecated. Use data binding properties instead.
 */
@Deprecated("State machine inputs are deprecated. Use data binding properties instead.")
class SMIBoolean internal constructor(
    unsafeCppPointer: Long,
    fileLock: ReentrantLock,
) : SMIInput(unsafeCppPointer, fileLock) {
    private external fun cppValue(cppPointer: Long): Boolean
    private external fun cppSetValue(cppPointer: Long, newValue: Boolean)

    var value: Boolean
        get() = synchronized(fileLock) { cppValue(cppPointer) }
        internal set(value) = synchronized(fileLock) { cppSetValue(cppPointer, value) }

    override fun toString(): String = "SMIBoolean $name\n"
}

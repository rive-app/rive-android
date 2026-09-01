package app.rive.runtime.kotlin.core

import java.util.concurrent.locks.ReentrantLock

/**
 * A trigger state machine input.
 *
 * @deprecated State machine inputs are deprecated. Use data binding properties instead.
 */
@Deprecated("State machine inputs are deprecated. Use data binding properties instead.")
class SMITrigger internal constructor(
    unsafeCppPointer: Long,
    fileLock: ReentrantLock,
) : SMIInput(unsafeCppPointer, fileLock) {
    private external fun cppFire(cppPointer: Long)

    /** Fires this input's trigger. */
    internal fun fire() = synchronized(fileLock) { cppFire(cppPointer) }

    override fun toString(): String = "SMITrigger $name\n"
}

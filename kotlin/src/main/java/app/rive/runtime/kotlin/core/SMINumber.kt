package app.rive.runtime.kotlin.core

import java.util.concurrent.locks.ReentrantLock

/** A floating point number state machine input. */
class SMINumber internal constructor(
    unsafeCppPointer: Long,
    fileLock: ReentrantLock,
) : SMIInput(unsafeCppPointer, fileLock) {
    private external fun cppValue(cppPointer: Long): Float
    private external fun cppSetValue(cppPointer: Long, value: Float)

    var value: Float
        get() = synchronized(fileLock) { cppValue(cppPointer) }
        internal set(value) = synchronized(fileLock) { cppSetValue(cppPointer, value) }

    override fun toString(): String = "SMINumber $name\n"
}

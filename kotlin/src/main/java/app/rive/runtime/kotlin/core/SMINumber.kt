package app.rive.runtime.kotlin.core

import java.util.concurrent.locks.ReentrantLock

/**
 * [SMINumber]s represents a boolean input for State Machines
 */
class SMINumber(unsafeCppPointer: Long, private val artboardLock: ReentrantLock) :
    SMIInput(unsafeCppPointer) {
    private external fun cppValue(cppPointer: Long): Float
    private external fun cppSetValue(cppPointer: Long, value: Float)

    var value: Float
        get() = cppValue(cppPointer)
        set(value) {
            synchronized(artboardLock) { cppSetValue(cppPointer, value) }
        }

    override fun toString(): String {
        return "SMINumber $name\n"
    }
}

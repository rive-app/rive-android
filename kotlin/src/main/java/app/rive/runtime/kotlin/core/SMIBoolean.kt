package app.rive.runtime.kotlin.core

import java.util.concurrent.locks.ReentrantLock

/**
 * [SMIBoolean]s is a boolean state machine input
 */
class SMIBoolean(unsafeCppPointer: Long, private val artboardLock: ReentrantLock) :
    SMIInput(unsafeCppPointer) {
    private external fun cppValue(cppPointer: Long): Boolean
    private external fun cppSetValue(cppPointer: Long, newValue: Boolean)

    var value: Boolean
        get() = cppValue(cppPointer)
        set(value) {
            synchronized(artboardLock) { cppSetValue(cppPointer, value) }
        }

    override fun toString(): String {
        return "SMIBoolean $name\n"
    }
}

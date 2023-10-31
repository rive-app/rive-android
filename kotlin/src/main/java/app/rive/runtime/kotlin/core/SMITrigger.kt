package app.rive.runtime.kotlin.core

import java.util.concurrent.locks.ReentrantLock

/**
 * [SMITrigger]s represents a trigger input for State Machines
 */
class SMITrigger(unsafeCppPointer: Long, private val artboardLock: ReentrantLock) :
    SMIInput(unsafeCppPointer) {
    private external fun cppFire(cppPointer: Long)

    fun fire() {
        synchronized(artboardLock) { cppFire(cppPointer) }
    }

    override fun toString(): String {
        return "SMITrigger $name\n"
    }
}

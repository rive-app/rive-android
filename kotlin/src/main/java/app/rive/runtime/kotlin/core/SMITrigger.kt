package app.rive.runtime.kotlin.core

/**
 * [SMITrigger]s represents a trigger input for State Machines
 */
class SMITrigger(unsafeCppPointer: Long) : SMIInput(unsafeCppPointer) {
    private external fun cppFire(cppPointer: Long)

    fun fire() {
        cppFire(cppPointer)
    }

    override fun toString(): String {
        return "SMITrigger $name\n"
    }
}

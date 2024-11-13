package app.rive.runtime.kotlin.core

/** A trigger state machines input. */
class SMITrigger(unsafeCppPointer: Long) :
    SMIInput(unsafeCppPointer) {
    private external fun cppFire(cppPointer: Long)

    internal fun fire() {
        cppFire(cppPointer)
    }

    override fun toString(): String {
        return "SMITrigger $name\n"
    }
}

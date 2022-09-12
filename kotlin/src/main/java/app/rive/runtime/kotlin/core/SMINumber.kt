package app.rive.runtime.kotlin.core

/**
 * [SMINumber]s represents a boolean input for State Machines
 */
class SMINumber(unsafeCppPointer: Long) : SMIInput(unsafeCppPointer) {
    private external fun cppValue(cppPointer: Long): Float
    private external fun cppSetValue(cppPointer: Long, value: Float)

    var value: Float
        get() = cppValue(cppPointer)
        set(value) {
            cppSetValue(cppPointer, value)
        }

    override fun toString(): String {
        return "SMINumber $name\n"
    }
}

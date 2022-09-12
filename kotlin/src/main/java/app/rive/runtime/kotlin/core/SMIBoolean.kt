package app.rive.runtime.kotlin.core

/**
 * [SMIBoolean]s is a boolean state machine input
 */
class SMIBoolean(unsafeCppPointer: Long) : SMIInput(unsafeCppPointer) {
    private external fun cppValue(cppPointer: Long): Boolean
    private external fun cppSetValue(cppPointer: Long, newValue: Boolean)

    var value: Boolean
        get() = cppValue(cppPointer)
        set(value) {
            cppSetValue(cppPointer, value)
        }

    override fun toString(): String {
        return "SMIBoolean $name\n"
    }
}

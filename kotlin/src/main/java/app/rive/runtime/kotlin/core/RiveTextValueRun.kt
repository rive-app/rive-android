package app.rive.runtime.kotlin.core

/**
 * A class for text run instances.
 *
 * These instances allow modification of Rive text runs.
 *
 * @param unsafeCppPointer Pointer to the C++ counterpart.
 */
open class RiveTextValueRun(unsafeCppPointer: Long) : NativeObject(unsafeCppPointer) {
    private external fun cppText(cppPointer: Long): String
    private external fun cppSetText(cppPointer: Long, name: String)

    var text: String
        get() = cppText(cppPointer)
        set(name) {
            cppSetText(cppPointer, name)
        }

    override fun toString(): String {
        return "TextValueRun: $text\n"
    }
}

package app.rive.runtime.kotlin.core

import java.util.concurrent.locks.ReentrantLock

/**
 * A class for text run instances.
 *
 * These instances allow modification of Rive text runs.
 *
 * @param unsafeCppPointer Pointer to the C++ counterpart.
 * @param fileLock Lock shared by the [File] and native graph this text run belongs to.
 */
open class RiveTextValueRun internal constructor(
    unsafeCppPointer: Long,
    private val fileLock: ReentrantLock,
) : NativeObject(unsafeCppPointer) {
    private external fun cppText(cppPointer: Long): String
    private external fun cppSetText(cppPointer: Long, name: String)

    /** The current text value. */
    var text: String
        get() = synchronized(fileLock) { cppText(cppPointer) }
        set(value) {
            synchronized(fileLock) {
                // Changing text dirties layout state that advance() reads and clears on the worker.
                cppSetText(cppPointer, value)
            }
        }

    override fun toString(): String {
        return "TextValueRun: $text\n"
    }
}

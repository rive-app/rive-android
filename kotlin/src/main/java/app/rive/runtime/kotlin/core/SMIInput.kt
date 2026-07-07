package app.rive.runtime.kotlin.core

import java.util.concurrent.locks.ReentrantLock

/**
 * [SMIInput]s are a base class for state machine input instances.
 *
 * These instances allow modification of the state of the attached state machine.
 *
 * @param unsafeCppPointer Pointer to the C++ counterpart.
 * @param fileLock Lock shared by the [File] and native graph this input belongs to.
 */
open class SMIInput internal constructor(
    unsafeCppPointer: Long,
    protected val fileLock: ReentrantLock,
) : NativeObject(unsafeCppPointer) {
    // SMIInput cpp objects are tied to the lifecycle of the StateMachineInstances in cpp
    // Therefore we do not have a cppDelete implementation
    private external fun cppName(cppPointer: Long): String
    private external fun cppIsBoolean(cppPointer: Long): Boolean
    private external fun cppIsTrigger(cppPointer: Long): Boolean
    private external fun cppIsNumber(cppPointer: Long): Boolean

    /** The input name. */
    val name: String
        get() = synchronized(fileLock) { cppName(cppPointer) }

    /** Whether this input a boolean input. */
    val isBoolean: Boolean
        get() = synchronized(fileLock) { cppIsBoolean(cppPointer) }

    /** Whether this input a trigger input. */
    val isTrigger: Boolean
        get() = synchronized(fileLock) { cppIsTrigger(cppPointer) }

    /** Whether this input a number input. */
    val isNumber: Boolean
        get() = synchronized(fileLock) { cppIsNumber(cppPointer) }

    override fun toString(): String = "SMIInput $name\n"
}

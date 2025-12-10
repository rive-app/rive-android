package app.rive.core

import app.rive.RiveInitializationException

/**
 * Abstraction of calls to the native command queue.
 *
 * Allows for mocking in tests.
 */
interface CommandQueueBridge {
    @Throws(RiveInitializationException::class)
    fun cppConstructor(renderContextPointer: Long): Long
    fun cppDelete(pointer: Long)
    fun cppCreateListeners(pointer: Long, receiver: CommandQueue): Listeners

    fun cppLoadFile(pointer: Long, requestID: Long, bytes: ByteArray)
    fun cppDeleteFile(pointer: Long, requestID: Long, fileHandle: Long)
}

/** Concrete JNI bridge implementation of [CommandQueueBridge]. */
internal class CommandQueueJNIBridge : CommandQueueBridge {
    external override fun cppConstructor(renderContextPointer: Long): Long
    external override fun cppDelete(pointer: Long)
    external override fun cppCreateListeners(pointer: Long, receiver: CommandQueue): Listeners

    external override fun cppLoadFile(pointer: Long, requestID: Long, bytes: ByteArray)
    external override fun cppDeleteFile(
        pointer: Long,
        requestID: Long,
        fileHandle: Long
    )
}

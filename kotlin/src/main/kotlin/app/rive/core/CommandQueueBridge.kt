package app.rive.core

internal interface CommandQueueBridge {
    fun loadFile(requestID: Long, bytes: ByteArray): FileHandle
}

//internal class CommandQueueBridgeImpl : CommandQueueBridge {
//}
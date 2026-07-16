package app.rive.core

/**
 * Handle to the native listeners that deliver [CommandQueue] callbacks.
 *
 * Created by [CommandQueueBridge.cppCreateListeners]; closed on the shutdown path after the
 * command server has drained.
 */
interface NativeListeners : AutoCloseable

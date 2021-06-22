package app.rive.runtime.kotlin.core.errors

/**
 * A Custom Exception signifying the current file does not support this runtime version.
 *
 * Any issue should be described in the [message].
 */
class UnsupportedRuntimeVersionException(message: String) : RiveException(message)

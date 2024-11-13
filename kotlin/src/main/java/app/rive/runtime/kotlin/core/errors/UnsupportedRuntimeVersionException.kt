package app.rive.runtime.kotlin.core.errors

/**
 * A custom exception signifying the current file does not support this runtime version.
 *
 * @param message A description of the issue.
 */
class UnsupportedRuntimeVersionException(message: String) : RiveException(message)

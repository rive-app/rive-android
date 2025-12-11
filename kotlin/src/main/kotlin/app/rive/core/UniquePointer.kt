package app.rive.core

import app.rive.RiveLog

/**
 * A unique pointer to a native C++ object.
 *
 * Assumes ownership of the native resource. Use [close] to dispose the native pointer and release
 * its resources. This will invoke [onDispose]. Uses [CloseOnce] to ensure that disposal only
 * happens once.
 *
 * @param cppPointer The native pointer address.
 * @param label A label for the object pointed to, used for logging purposes, e.g. "Artboard".
 * @param onDispose A callback invoked when the pointer is closed to clean up the native resource.
 */
data class UniquePointer(
    private val cppPointer: Long,
    val label: String,
    private val onDispose: (Long) -> Unit
) : CheckableAutoCloseable by CloseOnce("$label (UniquePointer)", {
    RiveLog.d(TAG) { "Disposing $label" }
    onDispose(cppPointer)
}) {
    companion object {
        private const val TAG = "Rive/UniquePointer"
    }

    /**
     * The native pointer address.
     *
     * @throws IllegalStateException If the pointer has been closed.
     */
    val pointer: Long
        get() {
            check(!closed) { "Attempting to access a disposed UniquePointer ($label)" }
            return cppPointer
        }
}

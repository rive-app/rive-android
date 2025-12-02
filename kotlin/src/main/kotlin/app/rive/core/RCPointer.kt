package app.rive.core

import app.rive.RiveLog
import java.util.concurrent.atomic.AtomicInteger

/**
 * A reference-counted pointer to a native C++ object.
 *
 * Begins with a reference count of 1 upon creation. Call [acquire] to increment the reference
 * count, and [release] to decrement it. When the reference count reaches zero, the [onDelete]
 * callback is invoked to clean up the native resource.
 *
 * @param cppPointer The native pointer address.
 * @param onDelete A callback invoked when the reference count reaches zero to clean up the native
 *    resource.
 * @param label A label for the object pointed to, used for logging purposes, e.g. "Artboard".
 */
class RCPointer(private val cppPointer: Long, val onDelete: (Long) -> Unit, val label: String) {
    companion object {
        private const val TAG = "Rive/RCPointer"
    }

    /** The reference count for this pointer. Starts at 1. */
    private var referenceCount: AtomicInteger = AtomicInteger(1)

    /**
     * The native pointer address.
     *
     * @throws IllegalStateException If the reference count is zero (i.e. the pointer has been
     *    released).
     */
    val pointer: Long
        get() {
            check(referenceCount.get() > 0) { "Attempting to access a released RCPointer ($label)." }
            return cppPointer
        }

    /**
     * Acquires a reference to this pointer.
     *
     * @param source A string indicating the source of the acquire call for logging purposes, e.g.
     *    "MyActivity"
     * @throws IllegalStateException If the reference count is zero (i.e. the pointer has been
     *    released).
     */
    @Throws(IllegalStateException::class)
    fun acquire(source: String) {
        val refCount = referenceCount.get()
        check(refCount > 0) { "Attempting to acquire a null RCPointer ($label)." }

        RiveLog.v(TAG) {
            "Acquiring $label (source: $source; ref count before acquire: ${refCount})"
        }
        referenceCount.incrementAndGet()
    }

    /**
     * Releases a reference to this pointer. When the reference count reaches zero, the [onDelete]
     * callback is invoked to clean up the native resource.
     *
     * @param source A string indicating the source of the release call for logging purposes, e.g.
     *    "MyActivity"
     * @param reason An optional reason for the release, for logging purposes.
     * @throws IllegalStateException If the reference count is already zero.
     */
    @Throws(IllegalStateException::class)
    fun release(source: String, reason: String = "") {
        val reasonLog = if (reason.isEmpty()) "" else "reason: $reason; "
        RiveLog.v(TAG) {
            "Releasing $label (source: $source; $reasonLog" +
                    "ref count before release: ${referenceCount.get()})"
        }
        val count = referenceCount.decrementAndGet()
        check(count >= 0) { "RCPointer ($label) released too many times." }
        // Dispose
        if (count == 0) {
            onDelete(cppPointer)
        }
    }
}

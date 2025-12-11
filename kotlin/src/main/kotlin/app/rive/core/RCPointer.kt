package app.rive.core

import app.rive.RiveLog
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** An interface for reference-counted objects. Can be used with [RCPointer] by delegation. */
interface RefCounted {
    /** The current reference count. */
    val refCount: Int

    /** Whether this object has been disposed due to the reference count reaching 0. */
    val isDisposed: Boolean

    /**
     * Acquire a reference. Must be balanced with a call to [release].
     *
     * @param source A string indicating the source of the acquisition, for logging purposes, e.g.
     *    "MyActivity".
     * @throws IllegalStateException If the object has already been disposed.
     */
    fun acquire(source: String)

    /**
     * Release a reference. When the last reference is released, the object is disposed.
     *
     * @param source A string indicating the source of the acquisition, for logging purposes, e.g.
     *    "MyActivity".
     * @param reason An optional string indicating the reason for the release, for logging purposes.
     * @throws IllegalStateException If the object has already been disposed.
     */
    fun release(source: String, reason: String = "")
}

/**
 * A reference-counted pointer to a native C++ object.
 *
 * Begins with a reference count of 1 upon creation. Call [acquire] to increment the reference
 * count, and [release] to decrement it. When the reference count reaches zero, the [onDispose]
 * callback is invoked to clean up the native resource.
 *
 * @param cppPointer The native pointer address.
 * @param label A label for the object pointed to, used for logging purposes, e.g. "Artboard".
 * @param onDispose A callback invoked when the reference count reaches zero to clean up the native
 *    resource.
 */
class RCPointer(
    private val cppPointer: Long,
    val label: String,
    private val onDispose: (Long) -> Unit
) : RefCounted {
    companion object {
        private const val TAG = "Rive/RCPointer"
    }

    /** The reference count for this pointer. Starts at 1. */
    private var referenceCount: AtomicInteger = AtomicInteger(1)
    override val refCount: Int
        get() = referenceCount.get()

    /** Whether this pointer has been disposed. */
    private var disposed: AtomicBoolean = AtomicBoolean(false)
    override val isDisposed: Boolean
        get() = disposed.get()

    /**
     * The native pointer address.
     *
     * @throws IllegalStateException If the reference count is zero (i.e. the pointer has been
     *    disposed).
     */
    val pointer: Long
        get() {
            check(referenceCount.get() > 0) { "Attempting to access a disposed RCPointer ($label)" }
            return cppPointer
        }

    @Throws(IllegalStateException::class)
    override fun acquire(source: String) {
        // This loop prevents a Time of Check/Time of Use (TOCTOU) race by checking the first
        // retrieved value again in the CAS operation. If another thread has modified the value in
        // the meantime, the CAS will fail and we retry.
        while (true) {
            val current = referenceCount.get()
            check(current > 0) { "Attempting to acquire a null RCPointer ($label)." }

            if (referenceCount.compareAndSet(current, current + 1)) {
                RiveLog.v(TAG) {
                    "Acquiring $label (source: $source; ref count before acquire: $current)"
                }
                return
            }
            // If we're here, some other thread changed the count. Retry.
        }
    }

    /**
     * Releases a reference to this pointer. When the reference count reaches zero, the [onDispose]
     * callback is invoked to clean up the native resource.
     *
     * @see [RefCounted.acquire]
     */
    @Throws(IllegalStateException::class)
    override fun release(source: String, reason: String) {
        val reasonLog = if (reason.isEmpty()) "" else "; reason: $reason"
        RiveLog.v(TAG) {
            "Releasing $label (source: $source$reasonLog; " +
                    "ref count before release: ${referenceCount.get()})"
        }
        val count = referenceCount.decrementAndGet()
        check(count >= 0) { "RCPointer $label (source: $source$reasonLog) released too many times." }
        // Dispose
        if (count == 0) {
            RiveLog.d(TAG) { "Disposing $label" }
            onDispose(cppPointer)
            disposed.set(true)
        }
    }
}

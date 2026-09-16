package app.rive.core

import app.rive.RiveLog
import app.rive.RiveResourceClosedException
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
    private val onDispose: (Long) -> Unit,
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
     * @throws RiveResourceClosedException If the reference count is zero and the pointer has been
     *    disposed.
     */
    val pointer: Long
        get() {
            if (referenceCount.get() <= 0) {
                throw RiveResourceClosedException("RCPointer $label is closed")
            }
            return cppPointer
        }

    @Throws(RiveResourceClosedException::class)
    override fun acquire(source: String) {
        if (!tryAcquire(source)) {
            throw RiveResourceClosedException("RCPointer $label is closed")
        }
    }

    /**
     * Atomically acquires a reference only while this pointer still has an owner.
     *
     * The compare-and-set competes with final release, so a zero count is never resurrected,
     * including while the disposal callback is running and [isDisposed] is still false.
     *
     * @param source The owner acquiring the temporary reference, for logging.
     * @return true if acquired and requiring a matching [release], false after final release.
     */
    internal fun tryAcquire(source: String): Boolean {
        while (true) {
            val current = referenceCount.get()
            if (current <= 0) return false
            if (referenceCount.compareAndSet(current, current + 1)) {
                RiveLog.v(TAG) {
                    "Acquiring $label (source: $source; ref count before acquire: $current)"
                }
                return true
            }
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
        check(count >= 0) {
            "RCPointer $label (source: $source$reasonLog) released too many times."
        }
        // Dispose
        if (count == 0) {
            RiveLog.d(TAG) { "Disposing $label" }
            onDispose(cppPointer)
            disposed.set(true)
        }
    }
}

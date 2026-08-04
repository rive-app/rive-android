package app.rive.core

import app.rive.RiveLog
import app.rive.RiveResourceClosedException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Utility to make [CheckableAutoCloseable] idempotent.
 *
 * If everything required for disposal is available from the constructor, you can implement via
 * delegation: `class Foo(...) : CheckableAutoCloseable by CloseOnce({ ... })`. Otherwise, use as a
 * member variable, e.g.: `private val closer = CloseOnce { ... }`, and forward [close] and [closed]
 * to it.
 *
 * Thread-safe, performing a no-op after the first close.
 *
 * Use [checkOpen] before operations that require the resource to still be open.
 *
 * @param label A precise description of the resource, used in logging and exceptions.
 * @param onClose The function to invoke on the first call to [close].
 */
class CloseOnce(private val label: String, private val onClose: () -> Unit) :
    CheckableAutoCloseable {
    private val _closed = AtomicBoolean(false)
    override val closed: Boolean
        get() = _closed.get()

    override fun close() {
        if (_closed.getAndSet(true)) {
            RiveLog.w("CloseOnce") {
                "Attempted to close already closed resource ($label). " +
                        "While safe, this may represent a mistake in ownership. " +
                        "The resource should only be closed once."
            }
            return
        }
        RiveLog.v("CloseOnce") { "Closing resource: $label" }
        onClose()
    }

    /**
     * Ensures this resource has not been closed.
     *
     * @throws RiveResourceClosedException If this resource has already been closed.
     */
    @Throws(RiveResourceClosedException::class)
    fun checkOpen() {
        if (closed) {
            throw RiveResourceClosedException("$label is closed")
        }
    }
}

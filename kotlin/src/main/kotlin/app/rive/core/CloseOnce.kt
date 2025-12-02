package app.rive.core

import app.rive.RiveLog
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Utility to make `AutoCloseable` idempotent. Compose via delegation: `class Foo(...) :
 * AutoCloseable by CloseOnce({ ... })`. Thread-safe, performing a no-op after the first close.
 *
 * @param onClose The function to invoke on the first call to [close].
 */
class CloseOnce(private val onClose: () -> Unit) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.getAndSet(true)) {
            RiveLog.w("CloseOnce") { "Attempted to close already closed resource. Is this a mistake?" }
            return
        }
        onClose()
    }
}

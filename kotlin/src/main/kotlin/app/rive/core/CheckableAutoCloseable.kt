package app.rive.core

import app.rive.RiveShutdownException

/**
 * An [AutoCloseable] that can report whether it has been closed.
 *
 * Subclasses must implement the [closed] property to indicate whether the resource has been closed.
 * This may be done with [CloseOnce] to ensure idempotency.
 *
 * As this is Rive specific, [close] may also throw [RiveShutdownException] on failure.
 */
interface CheckableAutoCloseable : AutoCloseable {
    /** Whether this resource has been closed. */
    val closed: Boolean

    /**
     * Closes this resource, releasing any underlying resources.
     *
     * @throws RiveShutdownException If an error occurs while closing the resource.
     */
    @Throws(RiveShutdownException::class)
    override fun close()
}

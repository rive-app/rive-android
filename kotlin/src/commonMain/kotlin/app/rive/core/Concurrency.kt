package app.rive.core

/**
 * A minimal thread-safe map, mirroring the subset of `java.util.concurrent.ConcurrentHashMap`
 * used by [CommandQueue].
 *
 * On platforms without threads it is a plain map.
 */
internal expect class ConcurrentMap<K : Any, V : Any>() {
    fun put(key: K, value: V): V?
    fun remove(key: K): V?
    val values: Collection<V>
    fun clear()
}

/**
 * A one-shot completion latch used to observe asynchronous native shutdown.
 *
 * On platforms without threads, [await] simply reports whether [signal] already ran.
 */
internal expect class ShutdownSignal() {
    fun signal()

    /** @return `true` if the signal fired before [timeoutMillis] elapsed. */
    fun await(timeoutMillis: Long): Boolean
}

/**
 * Runs [block] on a named daemon thread, or inline on platforms without threads.
 *
 * Used for blocking native shutdown work that must not stall the calling thread.
 */
internal expect fun runOnDaemonThread(name: String, block: () -> Unit)

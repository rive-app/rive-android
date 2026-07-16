package app.rive.core

// iOS has no working Rive runtime yet (worker creation throws before any of
// these are used), so plain single-threaded implementations suffice.

internal actual class ConcurrentMap<K : Any, V : Any> {
    private val map = HashMap<K, V>()

    actual fun put(key: K, value: V): V? = map.put(key, value)
    actual fun remove(key: K): V? = map.remove(key)
    actual val values: Collection<V> get() = map.values
    actual fun clear() = map.clear()
}

internal actual class ShutdownSignal {
    private var signaled = false

    actual fun signal() {
        signaled = true
    }

    actual fun await(timeoutMillis: Long): Boolean = signaled
}

internal actual fun runOnDaemonThread(name: String, block: () -> Unit) = block()

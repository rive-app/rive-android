package app.rive.core

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal actual class ConcurrentMap<K : Any, V : Any> {
    private val map = ConcurrentHashMap<K, V>()

    actual fun put(key: K, value: V): V? = map.put(key, value)
    actual fun remove(key: K): V? = map.remove(key)
    actual val values: Collection<V> get() = map.values
    actual fun clear() = map.clear()
}

internal actual class ShutdownSignal {
    private val latch = CountDownLatch(1)

    actual fun signal() = latch.countDown()
    actual fun await(timeoutMillis: Long): Boolean =
        latch.await(timeoutMillis, TimeUnit.MILLISECONDS)
}

internal actual fun runOnDaemonThread(name: String, block: () -> Unit) {
    Thread(block, name).also {
        it.isDaemon = true
        it.start()
    }
}

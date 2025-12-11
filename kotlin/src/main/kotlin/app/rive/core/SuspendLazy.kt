package app.rive.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Represents the three states of a deferred computation. */
private sealed interface DeferredResult<out T> {
    data object Uninitialized : DeferredResult<Nothing>
    data class Success<T>(val value: T) : DeferredResult<T>
    data class Failure(val error: Throwable) : DeferredResult<Nothing>
}

/**
 * A suspendable, lazy, memoized value. Computes the [block] once on the caller's coroutine scope
 * and caches the successful result (or the failure) for subsequent awaiters.
 *
 * @param T The type of the value being computed.
 * @property block The suspend function that computes the value.
 */
internal class SuspendLazy<T>(private val block: suspend () -> T) {
    @Volatile
    private var result: DeferredResult<T> = DeferredResult.Uninitialized
    private val mutex = Mutex()

    /**
     * Awaits the value, computing it if necessary.
     *
     * @return The computed value.
     * @throws Throwable If the computation fails.
     */
    suspend fun await(): T {
        return when (val res = result) {
            is DeferredResult.Success -> res.value
            is DeferredResult.Failure -> throw res.error
            DeferredResult.Uninitialized -> mutex.withLock {
                when (val lockedRes = result) {
                    // Need to check again inside the lock on the small chance another coroutine
                    // initialized the value while we were waiting for the lock.
                    is DeferredResult.Success -> lockedRes.value
                    is DeferredResult.Failure -> throw lockedRes.error
                    // Non-cached path, compute the value.
                    DeferredResult.Uninitialized -> try {
                        val v = block()
                        result = DeferredResult.Success(v)
                        v
                    } catch (t: Throwable) {
                        result = DeferredResult.Failure(t)
                        throw t
                    }
                }
            }
        }
    }
}

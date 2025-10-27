package app.rive.runtime.kotlin.core

import androidx.annotation.CallSuper
import java.util.concurrent.atomic.AtomicInteger

interface RefCount {
    var refs: AtomicInteger
    val refCount: Int
        get() = refs.get()

    @CallSuper
    fun acquire(): Int {
        return refs.incrementAndGet()
    }

    @CallSuper
    fun release(): Int {
        return refs.decrementAndGet()
    }
}

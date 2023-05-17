package app.rive.runtime.kotlin.core

import androidx.annotation.CallSuper
import java.util.concurrent.atomic.AtomicInteger


interface RefCount {
    var refs: AtomicInteger
    val refCount: Int
        get() = refs.get()

    @CallSuper
    fun acquire() {
        refs.incrementAndGet()
    }

    @CallSuper
    fun release() {
        refs.decrementAndGet()
    }
}
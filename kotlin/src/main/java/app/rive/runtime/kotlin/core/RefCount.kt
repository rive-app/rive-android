package app.rive.runtime.kotlin.core

import androidx.annotation.CallSuper


interface RefCount {
    var refs: Int

    @CallSuper
    fun acquire() {
        refs++
    }

    @CallSuper
    fun release() {
        refs--
    }
}
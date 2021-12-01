package app.rive.runtime.kotlin.controllers

abstract class RiveController<T> {
    var isActive = false
        protected set

    open fun initialize(core: T): Boolean {
        return true
    }

    abstract fun apply(core: T, elapsed: Float)
    protected open fun onActivate() {}
    protected open fun onDeactivate() {}
    open fun dispose() {}
}

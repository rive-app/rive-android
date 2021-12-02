package app.rive.runtime.kotlin.controllers

abstract class RiveController<T> {
    var isActive = false
        protected set

    open fun initialize(artboard: T): Boolean {
        return true
    }

    abstract fun apply(artboard: T, elapsed: Float)
    protected open fun onActivate() {}
    protected open fun onDeactivate() {}
    open fun dispose() {}
}

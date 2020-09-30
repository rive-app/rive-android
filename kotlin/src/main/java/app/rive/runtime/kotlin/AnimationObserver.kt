package app.rive.runtime.kotlin

abstract class AnimationObserver {
    val address: Long

    external private fun constructor(): Long

    constructor() {
        address = constructor()
    }

    abstract fun onFinished(animation: String)
    abstract fun onLoop(animation: String)
    abstract fun onPingPong(animation: String)
}
package app.rive.runtime.kotlin

class AnimationObserver {
    val address: Long

    external private fun constructor(): Long

    constructor() {
        address = constructor()
    }

    fun onFinished() {
        println("Android done!")
    }

    fun onLoop() {
        println("Android loop!")
    }

    fun onPingPong() {
        println("Android ping-pong!")
    }
}
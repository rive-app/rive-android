package app.rive.runtime.kotlin.core

abstract class Playable {
    abstract val name: String
}

abstract class PlayableInstance(var isPlaying: Boolean = true) {
    abstract val playable: Playable
    abstract fun apply(artboard: Artboard, elapsed: Float): Boolean
}
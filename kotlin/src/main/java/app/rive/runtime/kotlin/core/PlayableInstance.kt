package app.rive.runtime.kotlin.core

abstract class PlayableInstance(var isPlaying: Boolean = true) {
    abstract val name: String
}
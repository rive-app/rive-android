package app.rive.runtime.kotlin.core

abstract class Playable {
   abstract val name: String
}

abstract class PlayableInstance {
   abstract val playable: Playable
   abstract fun apply(artboard: Artboard, elapsed: Float): Boolean
}
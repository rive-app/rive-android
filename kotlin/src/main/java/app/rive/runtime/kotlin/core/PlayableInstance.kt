package app.rive.runtime.kotlin.core

abstract class PlayableInstance {
   abstract fun apply(artboard: Artboard, elapsed: Float): Boolean
}
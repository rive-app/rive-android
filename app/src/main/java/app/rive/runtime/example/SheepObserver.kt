package app.rive.runtime.example

import app.rive.runtime.kotlin.AnimationObserver
import app.rive.runtime.kotlin.Artboard
import app.rive.runtime.kotlin.LinearAnimationInstance

class SheepObserver : AnimationObserver {
    val artboard: Artboard
    val playingAnimations: ArrayList<LinearAnimationInstance>
    val start: LinearAnimationInstance
    val end: LinearAnimationInstance
    val vibration: LinearAnimationInstance
    val movement: LinearAnimationInstance

    private var loops = 0

    constructor(
        ab: Artboard,
        playing: ArrayList<LinearAnimationInstance>
    ) : super() {
        artboard = ab
        playingAnimations = playing

        start = LinearAnimationInstance(ab.animation("start"))
        start.addObserver(this)

        end = LinearAnimationInstance(ab.animation("end"))
        end.addObserver(this)

        vibration = LinearAnimationInstance(ab.animation("sheep_vibration"))
        vibration.addObserver(this)

        movement = LinearAnimationInstance(ab.animation("sheep_movement"))
        movement.addObserver(this)

        playingAnimations.clear()
        playingAnimations.add(start)
    }

    override fun onFinished(animation: String) {
        when (animation) {
            "start" -> {
                playingAnimations.clear()
                playingAnimations.add(vibration)
                playingAnimations.add(movement)
            }
        }
    }

    override fun onLoop(animation: String) {
        when (animation) {
            "sheep_movement" -> {
                println("Looping movement!")
                loops++
                if (loops > 1) {
                    playingAnimations.clear()
                    playingAnimations.add(end)
                }
            }
            "end" -> playingAnimations.clear()
        }
    }

    override fun onPingPong(animation: String) {}

}
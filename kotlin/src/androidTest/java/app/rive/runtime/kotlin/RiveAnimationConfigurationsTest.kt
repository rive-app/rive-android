package app.rive.runtime.kotlin

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RiveAnimationConfigurationsTest {

    @Test
    fun loop() {
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.animationconfigurations).readBytes())
        var animation = file.artboard.animation("loop")
        assertEquals(animation.loop, Loop.LOOP)
    }

    @Test
    fun pingpong() {
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.animationconfigurations).readBytes())
        var animation = file.artboard.animation("pingpong")
        assertEquals(animation.loop, Loop.PINGPONG)
    }

    @Test
    fun oneshot() {
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.animationconfigurations).readBytes())
        var animation = file.artboard.animation("oneshot")
        assertEquals(animation.loop, Loop.ONESHOT)
    }

    @Test
    fun checkdurations1sec60fps () {
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.animationconfigurations).readBytes())
        var animation = file.artboard.animation("1sec60fps")
        assertEquals(animation.duration, 60)
        assertEquals(animation.effectiveDuration, 60)
        assertEquals(animation.fps, 60)
        assertEquals(animation.workStart, -1)
        assertEquals(animation.workEnd, -1)
    }

    @Test
    fun checkdurations1sec120fps () {
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.animationconfigurations).readBytes())
        var animation = file.artboard.animation("1sec120fps")
        assertEquals(animation.duration, 120)
        assertEquals(animation.effectiveDuration, 120)
        assertEquals(animation.fps, 120)
        assertEquals(animation.workStart, -1)
        assertEquals(animation.workEnd, -1)
    }

    @Test
    fun checkdurations1sec60fps_f30f50 () {
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.animationconfigurations).readBytes())
        var animation = file.artboard.animation("1sec60fps_f30f50")
        assertEquals(animation.duration, 60)
        assertEquals(animation.effectiveDuration, 20)
        assertEquals(animation.fps, 60)
        assertEquals(animation.workStart, 30)
        assertEquals(animation.workEnd, 50)
    }

    @Test
    fun checkdurations1sec120fps_f30f50 () {
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.animationconfigurations).readBytes())
        var animation = file.artboard.animation("1sec120fps_f50f80")
        assertEquals(animation.duration, 120)
        assertEquals(animation.effectiveDuration, 30)
        assertEquals(animation.fps, 120)
        assertEquals(animation.workStart, 50)
        assertEquals(animation.workEnd, 80)
    }

}
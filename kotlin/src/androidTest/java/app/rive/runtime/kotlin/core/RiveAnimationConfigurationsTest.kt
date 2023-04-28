package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RiveAnimationConfigurationsTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context
    private lateinit var file: File

    @Before
    fun setup() {
        file = File(appContext.resources.openRawResource(R.raw.animationconfigurations).readBytes())
    }

    @Test
    fun loop() {
        val animation = file.firstArtboard.animation("loop")
        assertEquals(Loop.LOOP, animation.loop)
    }

    @Test
    fun pingpong() {
        val animation = file.firstArtboard.animation("pingpong")
        assertEquals(Loop.PINGPONG, animation.loop)
    }

    @Test
    fun oneshot() {
        val animation = file.firstArtboard.animation("oneshot")
        assertEquals(Loop.ONESHOT, animation.loop)
    }

    @Test
    fun checkdurations1sec60fps() {
        val animation = file.firstArtboard.animation("1sec60fps")
        assertEquals(60, animation.duration)
        assertEquals(60, animation.effectiveDuration)
        assertEquals(60, animation.fps)
        assertEquals(-1, animation.workStart)
        assertEquals(-1, animation.workEnd)
    }

    @Test
    fun checkdurations1sec120fps() {
        val animation = file.firstArtboard.animation("1sec120fps")
        assertEquals(120, animation.duration)
        assertEquals(120, animation.effectiveDuration)
        assertEquals(120, animation.fps)
        assertEquals(-1, animation.workStart)
        assertEquals(-1, animation.workEnd)
    }

    @Test
    fun checkdurations1sec60fps_f30f50() {
        val animation = file.firstArtboard.animation("1sec60fps_f30f50")
        assertEquals(60, animation.duration)
        assertEquals(20, animation.effectiveDuration)
        assertEquals(60, animation.fps)
        assertEquals(30, animation.workStart)
        assertEquals(50, animation.workEnd)
    }

    @Test
    fun checkdurations1sec120fps_f30f50() {
        val animation = file.firstArtboard.animation("1sec120fps_f50f80")
        assertEquals(120, animation.duration)
        assertEquals(30, animation.effectiveDuration)
        assertEquals(120, animation.fps)
        assertEquals(50, animation.workStart)
        assertEquals(80, animation.workEnd)
    }

}
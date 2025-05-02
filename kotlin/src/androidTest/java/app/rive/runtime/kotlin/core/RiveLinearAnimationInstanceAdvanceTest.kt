package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RiveLinearAnimationInstanceAdvanceTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context
    private lateinit var file: File
    private lateinit var artboard: Artboard

    private val animDurationFrames = 60
    private val animFps = 60
    private val animDurationSecs = animDurationFrames.toFloat() / animFps.toFloat() // 1.0f

    @Before
    fun setup() {
        file = File(appContext.resources.openRawResource(R.raw.multiple_animations).readBytes())
        artboard = file.firstArtboard // Should load 'New Artboard'
    }

    @Test
    fun testAdvanceResultAdvanced() {
            val anim = artboard.animation("one")
            anim.loop = Loop.LOOP

            // Advance less than full duration
            val resultNew = anim.advanceAndGetResult(animDurationSecs * 0.5f)
            assertEquals(AdvanceResult.ADVANCED, resultNew)
            assertEquals(animDurationSecs * 0.5f, anim.time, 0.001f)

            // Check deprecated method compatibility
            anim.time(0f)
            val resultOld = anim.advance(animDurationSecs * 0.5f)
            assertNull(resultOld)
    }

    @Test
    fun testAdvanceResultOneShot() {
            val anim = artboard.animation("one")
            anim.loop = Loop.ONESHOT

            // Advance just before the end
            val resultPre = anim.advanceAndGetResult(animDurationSecs * 0.9f)
            assertEquals(AdvanceResult.ADVANCED, resultPre)

            // Advance past the end
            val resultNew = anim.advanceAndGetResult(animDurationSecs * 0.2f)
            assertEquals(AdvanceResult.ONESHOT, resultNew)

            // Check deprecated method compatibility
            anim.time(0f)
            val resultOldPre = anim.advance(animDurationSecs * 0.9f)
            assertNull(resultOldPre)
            val resultOldPost = anim.advance(animDurationSecs * 0.2f)
            assertEquals(Loop.ONESHOT, resultOldPost)
    }

    @Test
    fun testAdvanceResultLoop() {
            val anim = artboard.animation("one")
            anim.loop = Loop.LOOP
            // Past the end
            val resultNew = anim.advanceAndGetResult(animDurationSecs * 1.2f)
            assertEquals(AdvanceResult.LOOP, resultNew)

            anim.time(0f)
            val resultOld = anim.advance(animDurationSecs * 1.2f)
            assertEquals(Loop.LOOP, resultOld)
    }

    @Test
    fun testAdvanceResultPingPongForwardToEnd() {
            val anim = artboard.animation("one")
            anim.loop = Loop.PINGPONG
            anim.direction = Direction.FORWARDS

            // Past the end
            val resultNew = anim.advanceAndGetResult(animDurationSecs * 1.2f)
            assertEquals(AdvanceResult.PINGPONG, resultNew)
            assertEquals(Direction.BACKWARDS, anim.direction)

            anim.time(0f)
            anim.direction = Direction.FORWARDS
            val resultOld = anim.advance(animDurationSecs * 1.2f)
            assertEquals(Loop.PINGPONG, resultOld)
            assertEquals(Direction.BACKWARDS, anim.direction)
    }

    @Test
    fun testAdvanceResultPingPongBackwardToStart() {
            val anim = artboard.animation("one")
            anim.loop = Loop.PINGPONG
            anim.direction = Direction.BACKWARDS
            anim.time(anim.endTime)

            val resultNew = anim.advanceAndGetResult(animDurationSecs * 1.2f)
            assertEquals(AdvanceResult.PINGPONG, resultNew)
            assertEquals(Direction.FORWARDS, anim.direction)

            anim.time(anim.endTime)
            anim.direction = Direction.BACKWARDS
            val resultOld = anim.advance(animDurationSecs * 1.2f)
            assertEquals(Loop.PINGPONG, resultOld)
            assertEquals(Direction.FORWARDS, anim.direction)
    }

    @Test
    fun testAdvanceResultNoneWhenFinished() {
        val anim = artboard.animation("one")
        anim.loop = Loop.ONESHOT

        // Past the end to finish it
        val resultFinish = anim.advanceAndGetResult(animDurationSecs * 1.2f)
        assertEquals(AdvanceResult.ONESHOT, resultFinish)
        assertEquals(animDurationSecs, anim.time)

        anim.time(0f)
        val resultOldFinish = anim.advance(animDurationSecs * 1.2f)
        assertEquals(Loop.ONESHOT, resultOldFinish)
    }

    @Test
    fun testAdvanceResultWithZeroDelta() {
            val anim = artboard.animation("one")

            anim.loop = Loop.LOOP
            val resultNewPlaying = anim.advanceAndGetResult(0.01f)
            assertEquals(AdvanceResult.ADVANCED, resultNewPlaying)

            anim.loop = Loop.ONESHOT
            anim.advanceAndGetResult(animDurationSecs * 1.2f)
            assertEquals(animDurationSecs, anim.time)

            val resultNewStopped = anim.advanceAndGetResult(0.0f)
            assertEquals(AdvanceResult.NONE, resultNewStopped)
            assertEquals(animDurationSecs, anim.time)
    }
}
package app.rive.runtime.kotlin.core

import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RiveStateMachineTouchEventTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context
    private lateinit var mockView: RiveAnimationView

    /*
    State Machine overview:

    StateMachine name: main

    two squares of 25x25 one starting at x=25 and one at x=75 
    both squares have touchUp events bound to them.
     */

    @Before
    fun init() {
        mockView = TestUtils.MockNoopRiveAnimationView(appContext)
    }

    @Test
    fun touchASpotWithoutListener() {
        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.Observer()
            mockView.registerListener(observer)
            mockView.setRiveResource(
                R.raw.touchevents,
                stateMachineName = "main",
                autoplay = true
            )
            assert(mockView.artboardRenderer != null)
            val renderer = mockView.artboardRenderer!!
            renderer.advance(0f)

            val downTime = SystemClock.uptimeMillis()
            val eventTime = SystemClock.uptimeMillis() + 100
            mockView.dispatchTouchEvent(
                MotionEvent.obtain(
                    downTime,
                    eventTime,
                    MotionEvent.ACTION_UP,
                    0f,
                    0f,
                    0
                )
            )
            renderer.advance(0f)
            assertEquals(0, observer.states.size)
        }
    }

    @Test
    fun touchSpotForX25Y25() {
        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.Observer()
            mockView.registerListener(observer)
            mockView.setRiveResource(
                R.raw.touchevents,
                stateMachineName = "main",
                autoplay = true
            )
            assert(mockView.artboardRenderer != null)
            val renderer = mockView.artboardRenderer!!
            renderer.advance(0f)

            val downTime = SystemClock.uptimeMillis()
            val eventTime = SystemClock.uptimeMillis() + 100
            mockView.dispatchTouchEvent(
                MotionEvent.obtain(
                    downTime,
                    eventTime,
                    MotionEvent.ACTION_UP,
                    25f,
                    25f,
                    0
                )
            )
            renderer.advance(0f)

            assertEquals(1, observer.states.size)

            assertEquals("x25updown", observer.states[0].stateName)
        }
    }

    @Test
    fun touchMultipleSpots() {
        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.Observer()
            mockView.registerListener(observer)
            mockView.setRiveResource(
                R.raw.touchevents,
                stateMachineName = "main",
                autoplay = true
            )
            assert(mockView.artboardRenderer != null)
            val renderer = mockView.artboardRenderer!!
            renderer.advance(0f)

            val downTime = SystemClock.uptimeMillis()
            val eventTime = SystemClock.uptimeMillis() + 100
            mockView.dispatchTouchEvent(
                MotionEvent.obtain(
                    downTime,
                    eventTime,
                    MotionEvent.ACTION_UP,
                    20f,
                    20f,
                    0
                )
            )
            renderer.advance(1f)

            mockView.dispatchTouchEvent(
                MotionEvent.obtain(
                    downTime,
                    eventTime,
                    MotionEvent.ACTION_UP,
                    75f,
                    25f,
                    0
                )
            )
            renderer.advance(1f)

            assertEquals(2, observer.states.size)
            assertEquals("x75updown", observer.states[1].stateName)
        }
    }

    @Test
    fun touchHittingX25Y25WithScalingMiss() {
        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.Observer()
//            overall artboard's been upscaled 10x, so touching at x=25 y=25 should miss
            (mockView as TestUtils.MockNoopRiveAnimationView).setBounds(1000f, 1000f)
            mockView.registerListener(observer)
            mockView.setRiveResource(
                R.raw.touchevents,
                stateMachineName = "main",
                autoplay = true
            )

            assert(mockView.artboardRenderer != null)
            val renderer = mockView.artboardRenderer!!
            renderer.advance(0f)

            val downTime = SystemClock.uptimeMillis()
            val eventTime = SystemClock.uptimeMillis() + 100
            mockView.dispatchTouchEvent(
                MotionEvent.obtain(
                    downTime,
                    eventTime,
                    MotionEvent.ACTION_UP,
                    25f,
                    25f,
                    0
                )
            )
            renderer.advance(0f)

            assertEquals(0, observer.states.size)
        }
    }

    @Test
    fun touchHittingX25Y25WithScalingHit() {
        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.Observer()
//            overall artboard's been upscaled 10x, so touching at x=25 y=25 should miss
            (mockView as TestUtils.MockNoopRiveAnimationView).setBounds(1000f, 1000f)
            mockView.registerListener(observer)
            mockView.setRiveResource(
                R.raw.touchevents,
                stateMachineName = "main",
                autoplay = true
            )

            assert(mockView.artboardRenderer != null)
            val renderer = mockView.artboardRenderer!!
            renderer.advance(0f)

            val downTime = SystemClock.uptimeMillis()
            val eventTime = SystemClock.uptimeMillis() + 100
            mockView.dispatchTouchEvent(
                MotionEvent.obtain(
                    downTime,
                    eventTime,
                    MotionEvent.ACTION_UP,
                    250f,
                    250f,
                    0
                )
            )
            renderer.advance(0f)

            assertEquals(1, observer.states.size)

            assertEquals("x25updown", observer.states[0].stateName)
        }
    }

    /**
     * Tests that we do not miss VMI state changes that happen when touch down and up happen on the
     * same frame. This was an edge case that was fixed by advancing by 0 after touch down and up
     * events.
     */
    @Test
    fun rapidDownUpProcessesDownVMIState() = UiThreadStatement.runOnUiThread {
        (mockView as TestUtils.MockNoopRiveAnimationView).setBounds(500f, 500f)
        mockView.setRiveResource(
            R.raw.rapid_pointer_events,
            autoplay = true,
            autoBind = true
        )
        assert(mockView.artboardRenderer != null)
        val renderer = mockView.artboardRenderer!!
        val hasReached = mockView.controller.stateMachines.first()
            .viewModelInstance!!
            .getBooleanProperty("hasReached")
        renderer.advance(0f)
        assertFalse(hasReached.value)

        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            250f,
            250f,
            0
        )
        val up = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_UP,
            250f,
            250f,
            0
        )
        try {
            mockView.dispatchTouchEvent(down)
            assertTrue(hasReached.value)
            mockView.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }

        assertTrue(hasReached.value)
    }

    /**
     * Verifies touch-driven state notifications retain the file lock through listener callbacks.
     */
    @Test
    fun touchStateNotificationHoldsFileLock() = UiThreadStatement.runOnUiThread {
        mockView.setRiveResource(
            R.raw.touchevents,
            stateMachineName = "main",
            autoplay = true
        )
        val renderer = requireNotNull(mockView.artboardRenderer)
        renderer.advance(0f)

        val controller = mockView.controller
        val fileLock = requireNotNull(controller.file).fileLock
        var notifiedStateName: String? = null
        var notificationHeldFileLock = false
        mockView.registerListener(object : RiveFileController.Listener {
            override fun notifyPlay(animation: PlayableInstance) = Unit
            override fun notifyPause(animation: PlayableInstance) = Unit
            override fun notifyStop(animation: PlayableInstance) = Unit
            override fun notifyLoop(animation: PlayableInstance) = Unit

            override fun notifyStateChanged(stateMachineName: String, stateName: String) {
                notifiedStateName = stateName
                notificationHeldFileLock = Thread.holdsLock(fileLock)
            }
        })

        val eventTime = SystemClock.uptimeMillis()
        val up = MotionEvent.obtain(
            eventTime,
            eventTime,
            MotionEvent.ACTION_UP,
            25f,
            25f,
            0
        )
        try {
            mockView.dispatchTouchEvent(up)
        } finally {
            up.recycle()
        }

        assertEquals("x25updown", notifiedStateName)
        assertTrue(notificationHeldFileLock)
    }
}

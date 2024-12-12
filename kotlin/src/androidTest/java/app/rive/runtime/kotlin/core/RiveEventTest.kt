package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class RiveEventTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context
    private lateinit var view: TestUtils.MockRiveAnimationView

    @Before
    fun init() {
        view = TestUtils.MockRiveAnimationView(appContext)
    }

    @Test
    fun fire_a_single_general_event() {
        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.EventObserver()
            view.setRiveResource(R.raw.events_test, stateMachineName = "State Machine 1")
            view.addEventListener(observer)
            view.fireState("State Machine 1", "FireGeneralEvent")
            // This advance processes the fire but because we internally grab the latest events
            // before advancing the artboard, we don't catch "this frame's" events until the next
            // advance, which is why we advance twice here.
            view.artboardRenderer?.advance(0.016f)

            // No events yet.
            assertEquals(0, observer.events.size)

            // Second advance reports the event triggered by fireState.
            view.artboardRenderer?.advance(0.016f)
            assertEquals(1, observer.events.size)
            val event = observer.events[0]
            assertIs<RiveGeneralEvent>(event)
            assertEquals("SomeGeneralEvent", event.name)
            assertEquals(EventType.GeneralEvent, event.type)
            val expectedProperties =
                hashMapOf("SomeNumber" to 11.0f, "SomeString" to "Something", "SomeBoolean" to true)
            assertEquals(expectedProperties, event.properties)
        }
    }

    @Test
    fun fire_a_single_open_url_event() {
        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.EventObserver()
            view.setRiveResource(R.raw.events_test, stateMachineName = "State Machine 1")
            view.addEventListener(observer)
            view.play()
            view.fireState("State Machine 1", "FireOpenUrlEvent")
            view.artboardRenderer?.advance(0.016f)
            // Events caught on second advance.
            view.artboardRenderer?.advance(0.016f)
            assertEquals(1, observer.events.size)
            val event = observer.events[0]
            assertIs<RiveOpenURLEvent>(event)
            assertEquals("SomeOpenUrlEvent", event.name)
            assertEquals(EventType.OpenURLEvent, event.type)
            assertEquals("https://rive.app", (event as RiveOpenURLEvent).url)
            assertEquals("_parent", (event).target)
            val expectedProperties = hashMapOf<String, Any>()
            assertEquals(expectedProperties, event.properties)
        }
    }

    @Test
    fun fire_multiple_events() {
        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.EventObserver()
            view.setRiveResource(R.raw.events_test, stateMachineName = "State Machine 1")
            view.addEventListener(observer)
            view.play()
            view.fireState("State Machine 1", "FireBothEvents")
            view.artboardRenderer?.advance(0.016f)
            view.artboardRenderer?.advance(0.016f)
            assertEquals(2, observer.events.size)
        }
    }
}

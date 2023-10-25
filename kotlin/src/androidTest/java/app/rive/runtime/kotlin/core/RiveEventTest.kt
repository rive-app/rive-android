package app.rive.runtime.kotlin.core

import app.rive.runtime.kotlin.test.R
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
            view.addEventListener(observer);
            view.play()
            view.fireState("State Machine 1", "FireGeneralEvent")
            view.fireState("State Machine 1", "FireGeneralEvent") // TODO: (gordon) calling twice to step around event issue
            view.artboardRenderer?.advance(0.016f);
            Assert.assertEquals(1, observer.events.size)
            val event = observer.events[0];
            Assert.assertTrue(event is RiveGeneralEvent)
            Assert.assertEquals("SomeGeneralEvent", event.name)
            Assert.assertEquals(EventType.GeneralEvent, event.type)
            val expectedProperties =  hashMapOf("SomeNumber" to 11.0f, "SomeString" to "Something", "SomeBoolean" to true)
            Assert.assertEquals(expectedProperties, event.properties)
            view.artboardRenderer?.advance(0.016f);

        }
    }

    @Test
    fun fire_a_single_open_url_event() {
        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.EventObserver()
            view.setRiveResource(R.raw.events_test, stateMachineName = "State Machine 1")
            view.addEventListener(observer);
            view.play()
            view.fireState("State Machine 1", "FireOpenUrlEvent")
            view.fireState("State Machine 1", "FireOpenUrlEvent") // TODO: (gordon) calling twice to step around event issue
            view.artboardRenderer?.advance(0.016f);
            Assert.assertEquals(1, observer.events.size)
            val event = observer.events[0];
            Assert.assertTrue(event is RiveOpenURLEvent)
            Assert.assertEquals("SomeOpenUrlEvent", event.name)
            Assert.assertEquals(EventType.OpenURLEvent, event.type)
            Assert.assertEquals("https://rive.app", (event as RiveOpenURLEvent).url)
            Assert.assertEquals("_parent", (event).target)
            val expectedProperties = hashMapOf<String, Any>();
            Assert.assertEquals(expectedProperties, event.properties)
        }
    }

    @Test
    fun fire_multiple_events() {
        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.EventObserver()
            view.setRiveResource(R.raw.events_test, stateMachineName = "State Machine 1")
            view.addEventListener(observer);
            view.play()
            view.fireState("State Machine 1", "FireBothEvents")
            view.fireState("State Machine 1", "FireBothEvents") // TODO: (gordon) calling twice to step around event issue
            view.artboardRenderer?.advance(0.016f);
            Assert.assertEquals(2, observer.events.size)
        }
    }
}
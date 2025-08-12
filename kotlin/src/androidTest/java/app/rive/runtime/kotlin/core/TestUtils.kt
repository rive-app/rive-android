package app.rive.runtime.kotlin.core

import android.content.Context
import android.graphics.RectF
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.fonts.FontBytes
import app.rive.runtime.kotlin.renderers.RiveArtboardRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration


class TestUtils {

    val context: Context by lazy {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("app.rive.runtime.kotlin.test", appContext.packageName)

        Rive.init(appContext)

        appContext
    }

    companion object {
        @Suppress("unused")
        fun waitUntil(
            atMost: Duration,
            condition: () -> Boolean,
        ) {
            val maxTime = atMost.inWholeMilliseconds

            val interval: Long = 50
            var elapsed: Long = 0
            do {
                elapsed += interval
                Thread.sleep(interval)

                if (elapsed > maxTime) {
                    throw TimeoutException("Took too long.")
                }
            } while (!condition())
        }
    }


    class MockArtboardRenderer(controller: RiveFileController, val latch: CountDownLatch) :
        RiveArtboardRenderer(controller = controller) {
        /**
         * Instead of scheduling a new frame via the Choreographer (which uses a native C++ thread)
         * force an advance cycle. (We don't need to draw in tests either).
         */
        init {
            // lets just pretend we have a surface..
            isAttached = true
        }

        override fun scheduleFrame() {
            advance(0f)
        }

        /** NOP */
        override fun draw() {}

        override fun disposeDependencies() {
            super.disposeDependencies()
            latch.countDown()
        }
    }

    /**
     * This RiveAnimationView uses a custom [MockArtboardRenderer] to prevent tests from using the
     * Choreographer API which would be calling native threading primitives.
     */
    class MockRiveAnimationView(
        context: Context,
        attachOnInit: Boolean = true,
        val latchCount: Int = 1,
    ) :
        RiveAnimationView(context) {
        init {
            // Simulate this lifecycle method which the test harness wouldn't trigger otherwise.
            if (attachOnInit) {
                mockAttach()
            }
        }

        override fun createRenderer(): MockArtboardRenderer {
            return MockArtboardRenderer(controller, CountDownLatch(latchCount))
        }

        fun mockAttach(isReinit: Boolean = false) {
            onAttachedToWindow()
        }

        fun mockDetach(destroy: Boolean = true) {
            // Grab the reference before it's nullified.
            val mockRendererLatch = (renderer as MockArtboardRenderer).latch
            onDetachedFromWindow()

            // Make sure that the background thread cleaned everything up
            val released = mockRendererLatch.await(2, TimeUnit.SECONDS)
            assertTrue("Renderer was not released", released)

            if (destroy) {
                mockOnDestroy()
            }
        }

        fun mockOnDestroy() {
            controller.release()
        }

        public override fun createObserver(): LifecycleObserver {
            return super.createObserver()
        }
    }

    class MockNoopArtboardRenderer(controller: RiveFileController, val latch: CountDownLatch) :
        RiveArtboardRenderer(controller = controller) {
        /** NOP */
        override fun scheduleFrame() {}

        /** NOP */
        override fun draw() {}

        override fun disposeDependencies() {
            super.disposeDependencies()
            latch.countDown()
        }
    }

    /**
     * This RiveAnimationView uses a custom [MockNoopArtboardRenderer] to noop any drawing interactions.
     */
    class MockNoopRiveAnimationView(
        context: Context,
        val latch: CountDownLatch = CountDownLatch(1),
    ) : RiveAnimationView(context) {
        init {
            // Simulate this lifecycle method which the test harness wouldn't trigger otherwise.
            mockAttach()
        }

        override fun createRenderer(): MockNoopArtboardRenderer {
            return MockNoopArtboardRenderer(controller, latch)
        }


        fun setBounds(width: Float, height: Float) {
            controller.targetBounds = RectF(0f, 0f, width, height)
        }

        private fun mockAttach() {
            onAttachedToWindow()
        }

        fun mockDetach() {
            onDetachedFromWindow()
            // Let's wait until the background thread cleans everything up.
            val released = latch.await(2, TimeUnit.SECONDS)
            assertTrue("Renderer was not released", released)
            // Mimics onDestroy() but these tests don't have lifecycle observers.
            controller.release()
        }
    }

    data class StateChanged(var stateMachineName: String, var stateName: String)

    class EventObserver : RiveFileController.RiveEventListener {
        var events = mutableListOf<RiveEvent>()
        override fun notifyEvent(event: RiveEvent) {
            events.add(event)
        }
    }

    class Observer : RiveFileController.Listener {
        var plays = mutableListOf<PlayableInstance>()
        var pauses = mutableListOf<PlayableInstance>()
        var stops = mutableListOf<PlayableInstance>()
        var loops = mutableListOf<PlayableInstance>()
        var states = mutableListOf<StateChanged>()
        var elapsed: Float = 0f

        override fun notifyPlay(animation: PlayableInstance) {
            plays.add(animation)
        }

        override fun notifyPause(animation: PlayableInstance) {
            pauses.add(animation)
        }

        override fun notifyStop(animation: PlayableInstance) {
            stops.add(animation)
        }

        override fun notifyLoop(animation: PlayableInstance) {
            loops.add(animation)
        }

        override fun notifyStateChanged(stateMachineName: String, stateName: String) {
            states.add(StateChanged(stateMachineName, stateName))
        }

        override fun notifyAdvance(elapsed: Float) {
            this.elapsed = 0.016f;
        }
    }

    // A mock LifecycleOwner that allows us to manually control its state.
    class MockLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle
            get() = registry

        fun moveToState(state: Lifecycle.State) {
            registry.currentState = state
        }
    }
}


object NativeFontTestHelper {
    external fun cppGetSystemFontBytes(): ByteArray
    external fun cppFindFontFallback(missingCodePoint: Int, fontBytes: FontBytes): Int
    external fun cppCleanupFallbacks()
}
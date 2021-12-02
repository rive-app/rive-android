package app.rive.runtime.kotlin.core

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.RiveArtboardRenderer
import app.rive.runtime.kotlin.renderers.RendererSkia
import org.junit.Assert.assertEquals
import java.util.concurrent.TimeoutException


class TestUtils {

    val context: Context by lazy {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("app.rive.runtime.kotlin.test", appContext.packageName)

        Rive.init(appContext)

        appContext
    }


    class MockArtboardRenderer : RiveArtboardRenderer() {
        /**
         * Instead of scheduling a new frame via the Choreographer (which uses a native C++ thread)
         * force an advance cycle. (We don't need to draw in tests either).
         */
        override fun scheduleFrame() {
            advance(0f)
        }

        /** NOP */
        override fun draw() {}
    }

    /**
     * This RiveAnimationView uses a custom [MockArtboardRenderer] to prevent tests from using the
     * Choreographer API which would be calling native threading primitives.
     */
    class MockRiveAnimationView(context: Context) : RiveAnimationView(context) {
        override fun makeRenderer(): MockArtboardRenderer {
            return MockArtboardRenderer()
        }
    }


    companion object {
        @Throws(TimeoutException::class)
        fun waitOnFrame(
            rendererSkia: RendererSkia,
            condition: () -> Boolean,
            timeoutMs: Long = 500
        ) {
            rendererSkia.doFrame(System.nanoTime())
            waitUntil(condition, timeoutMs)
        }

        @Throws(TimeoutException::class)
        fun waitUntil(condition: () -> Boolean, timeoutMs: Long) {
            val start = System.currentTimeMillis()
            while (!condition()) {
                if (System.currentTimeMillis() - start > timeoutMs) {
                    throw TimeoutException("Condition not met within ${timeoutMs}ms")
                }
                Thread.sleep(1)
            }
        }

    }
}


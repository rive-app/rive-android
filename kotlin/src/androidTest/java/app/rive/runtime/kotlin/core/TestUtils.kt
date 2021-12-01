package app.rive.runtime.kotlin.core

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.runtime.kotlin.renderers.RendererSkia
import org.junit.Assert.assertEquals
import java.util.concurrent.TimeoutException


class TestUtils {
    private lateinit var testRenderer: MockRenderer

    val context: Context by lazy {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("app.rive.runtime.kotlin.test", appContext.packageName)

        Rive.init(appContext)
        testRenderer = MockRenderer()

        appContext
    }


    private class MockRenderer : RendererSkia() {
        init {
            println("Got this mock initialized!")
        }

        override fun draw() {}
        override fun advance(elapsed: Float) {}
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

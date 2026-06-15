package app.rive

import android.content.Context
import app.rive.core.assertDisposed
import app.rive.core.RiveWorker
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.runtime.kotlin.core.Rive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/**
 * Base class for androidTest cases that require the native Rive runtime to be initialized.
 *
 * JUnit runs superclass `@Before` methods before subclass `@Before` methods, so individual tests
 * can still add their own setup without repeating [Rive.init].
 */
abstract class RiveAndroidTest {
    protected val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private var worker: RiveWorker? = null

    /** Lazily-created worker for tests that only need one command queue. */
    protected val riveWorker: RiveWorker
        get() = worker ?: RiveWorker().also { worker = it }

    @BeforeTest
    fun initRiveRuntime() {
        Rive.init(context)
    }

    @AfterTest
    fun releaseRiveWorker() {
        worker?.let { activeWorker ->
            if (!activeWorker.isDisposed) {
                activeWorker.release(javaClass.simpleName, "Test cleanup")
            }
            assertDisposed(activeWorker)
        }
        worker = null
    }
}

package app.rive.runtime.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.controllers.ControllerStateManagement
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.Rive
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds


@ControllerStateManagement
@RunWith(AndroidJUnit4::class)
class RiveActivityLifecycleTest {
    /** Temporarily copy-pasted from TestUtils as we iterate on these tests */
    private val context: Context by lazy {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("app.rive.runtime.example", appContext.packageName)
        Rive.init(appContext)
        appContext
    }

    private fun waitUntil(
        atMost: Duration,
        condition: () -> Boolean
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

    @Test
    fun activityWithRiveView() {
        val activityScenario = ActivityScenario.launch(SingleActivity::class.java);
        lateinit var riveView: RiveAnimationView
        lateinit var controller: RiveFileController
        // Start the Activity.
        activityScenario.onActivity {
            riveView = it.findViewById(R.id.rive_single)
            controller = riveView.controller

            assertEquals(2, controller.refCount)
            assertTrue(controller.isActive)
            assertNotNull(controller.file)
            assertNotNull(controller.activeArtboard)
        }
        // Close it down.
        activityScenario.close()
        // Background thread deallocates asynchronously.
        waitUntil(500.milliseconds) { controller.refCount == 0 }
        assertFalse(controller.isActive)
        assertNull(controller.file)
        assertNull(controller.activeArtboard)
    }
}
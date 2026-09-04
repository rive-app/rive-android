package app.rive.runtime.example

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises filesystem source handling in the scripting sample activity. */
@RunWith(AndroidJUnit4::class)
class ScriptingActivityTest {
    /** Verifies an invalid adb-supplied path is displayed as an error instead of crashing. */
    @Test
    fun missingDemoPath_displaysError() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val missingFileName = "missing-deferred-renderer-test.riv"
        val intent = Intent(context, ScriptingActivity::class.java).apply {
            putExtra(DEMO_RIV_EXTRA, "/does/not/exist/$missingFileName")
        }

        val scenario = ActivityScenario.launch<ScriptingActivity>(intent)
        try {
            val device = UiDevice.getInstance(instrumentation)
            assertTrue(
                "Timed out waiting for the missing-file error",
                device.wait(
                    Until.hasObject(By.textContains(missingFileName)),
                    TIMEOUT_MILLIS,
                ),
            )
        } finally {
            scenario.close()
        }
    }

    private companion object {
        const val DEMO_RIV_EXTRA = "demoRiv"
        const val TIMEOUT_MILLIS = 10_000L
    }
}

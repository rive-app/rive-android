package app.rive.runtime.example

import RunOnDevice
import TestUtils.Companion.waitUntil
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.RendererType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds


// Don't run on the Emulator: Rive renderer can't initialize there.
@RunWith(RunOnDevice::class)
class RiveRendererActivityTest {
    @Test
    fun activityWithRiveRenderer() {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), SingleActivity::class.java).apply {
                putExtra("renderer", "Rive")
            }
        val activityScenario = ActivityScenario.launch<SingleActivity>(intent);
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
            // Specified Rive renderer.
            assertEquals(RendererType.Rive, riveView.rendererAttributes.rendererType)
            assertEquals(riveView.rendererAttributes.rendererType, controller.file?.rendererType)
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
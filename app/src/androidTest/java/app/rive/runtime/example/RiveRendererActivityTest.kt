package app.rive.runtime.example

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.example.TestUtils.Companion.waitUntil
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.RendererType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class RiveRendererActivityTest {
    @Test
    fun activityWithRiveRenderer() {
        val activityScenario = ActivityScenario.launch(SingleActivity::class.java)
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

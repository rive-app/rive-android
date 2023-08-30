package app.rive.runtime.example

import TestUtils.Companion.waitUntil
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.File
import app.rive.runtime.kotlin.core.RendererType
import app.rive.runtime.kotlin.core.errors.RiveException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds


@RunWith(AndroidJUnit4::class)
class RiveActivityLifecycleTest {
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
            // Defaults to Skia.
            assertEquals(RendererType.Skia, riveView.rendererAttributes.rendererType)
            assertEquals(riveView.rendererAttributes.rendererType, controller.file?.rendererType)
        }
        // Close it down.
        activityScenario.close()
        // Background thread deallocates asynchronously.
        waitUntil(1500.milliseconds) { controller.refCount == 0 }
        assertFalse(controller.isActive)
        assertNull(controller.file)
        assertNull(controller.activeArtboard)
    }

    @Test
    fun activityWithRiveViewSetsWrongFileType() {
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
            // Defaults to Skia.
            assertEquals(RendererType.Skia, riveView.rendererAttributes.rendererType)
            assertEquals(riveView.rendererAttributes.rendererType, controller.file?.rendererType)
            // Set wrong file type throws!
            val customRendererFile = File(
                it.resources.openRawResource(R.raw.off_road_car_blog).readBytes(),
                RendererType.Rive
            )
            assertEquals(RendererType.Rive, customRendererFile.rendererType)
            val wrongFileTypeException = assertThrows(RiveException::class.java) {
                // Boom!
                riveView.setRiveFile(customRendererFile)
            }
            assertEquals(
                "Incompatible Renderer types: file initialized with ${customRendererFile.rendererType.name}" +
                        " but View is set up for ${riveView.rendererAttributes.rendererType.name}",
                wrongFileTypeException.message
            )
        }
        // Close it down.
        activityScenario.close()
        // Background thread deallocates asynchronously.
        waitUntil(1500.milliseconds) { controller.refCount == 0 }
        assertFalse(controller.isActive)
        assertNull(controller.file)
        assertNull(controller.activeArtboard)
    }
}
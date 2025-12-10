package app.rive.runtime.example

import android.view.ViewGroup
import android.widget.Button
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.example.TestUtils.Companion.waitUntil
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.ContextAssetLoader
import app.rive.runtime.kotlin.core.File
import app.rive.runtime.kotlin.core.FileAsset
import app.rive.runtime.kotlin.core.FileAssetLoader
import app.rive.runtime.kotlin.core.RendererType
import app.rive.runtime.kotlin.core.errors.RiveException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class RiveActivityLifecycleTest {
    @Test
    fun activityWithRiveView() {
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
            // Defaults to Rive Renderer.
            assertEquals(RendererType.Rive, riveView.rendererAttributes.rendererType)
            assertEquals(riveView.rendererAttributes.rendererType, controller.file?.rendererType)
        }
        // Close it down.
        activityScenario.close()
        // Background thread deallocates asynchronously.
        waitUntil(1500.milliseconds) {
            controller.refCount == 0 && !controller.isActive && controller.file == null && controller.activeArtboard == null
        }
        assertFalse(controller.isActive)
        assertNull(controller.file)
        assertNull(controller.activeArtboard)
    }

    @Test
    fun withCustomLoader() {
        val activityScenario = ActivityScenario.launch(SingleActivity::class.java)
        lateinit var riveView: RiveAnimationView
        lateinit var controller: RiveFileController
        lateinit var ogAssetLoader: FileAssetLoader
        lateinit var replacedAssetLoader: FileAssetLoader
        // Start the Activity.
        activityScenario.onActivity {
            riveView = it.findViewById(R.id.rive_single)
            controller = riveView.controller

            ogAssetLoader = riveView.rendererAttributes.assetLoader!!
            assertTrue(ogAssetLoader.hasCppObject)

            replacedAssetLoader = object : ContextAssetLoader(it) {
                override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean = true
            }
            assertEquals(2, ogAssetLoader.refCount)
            riveView.setAssetLoader(replacedAssetLoader)
            // The old asset loader is still referenced by the old file
            assertEquals(1, ogAssetLoader.refCount)
            assertEquals(
                replacedAssetLoader,
                riveView.rendererAttributes.assetLoader
            )
            // One ref from the owner (this test) and one from the View.
            assertEquals(2, replacedAssetLoader.refCount)
        }
        // Close it down.
        activityScenario.close()
        // Background thread deallocates asynchronously.
        waitUntil(1500.milliseconds) {
            controller.refCount == 0 &&
                    !controller.isActive &&
                    controller.file == null &&
                    controller.activeArtboard == null &&
                    !ogAssetLoader.hasCppObject
        }
        assertFalse(controller.isActive)
        assertNull(controller.file)
        assertNull(controller.activeArtboard)
        assertFalse(ogAssetLoader.hasCppObject)

        // New assetLoader needs to be freed by the creator
        assertTrue(replacedAssetLoader.hasCppObject)
        replacedAssetLoader.release()
        assertFalse(replacedAssetLoader.hasCppObject)
    }

    @Test
    fun activityWithRiveViewSetsWrongFileType() {
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
            // Defaults to Rive Renderer.
            assertEquals(RendererType.Rive, riveView.rendererAttributes.rendererType)
            assertEquals(riveView.rendererAttributes.rendererType, controller.file?.rendererType)
            // Set wrong file type throws!
            val customRendererFile = File(
                it.resources.openRawResource(R.raw.off_road_car_blog).readBytes(),
                RendererType.Canvas
            )
            assertEquals(RendererType.Canvas, customRendererFile.rendererType)
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
        waitUntil(1500.milliseconds) { controller.refCount == 0 && !controller.isActive && controller.file == null && controller.activeArtboard == null }
        assertFalse(controller.isActive)
        assertNull(controller.file)
        assertNull(controller.activeArtboard)
    }

    @Test
    fun resizeRiveView() {
        val activityScenario = ActivityScenario.launch(SingleActivity::class.java)
        lateinit var riveView: RiveAnimationView
        lateinit var controller: RiveFileController
        var ogWidth = 0f

        val layoutCompleteLatch = CountDownLatch(1)
        // Start the Activity.
        activityScenario.onActivity { activity ->
            riveView = activity.findViewById(R.id.rive_single)
            controller = riveView.controller

            assertEquals(2, controller.refCount)
            assertTrue(controller.isActive)
            assertNotNull(controller.file)
            assertNotNull(controller.activeArtboard)

            ogWidth = riveView.artboardRenderer!!.width

            // This block runs after the initial layout
            riveView.doOnLayout {
                ogWidth = riveView.artboardRenderer!!.width

                val button = Button(activity).apply {
                    text = "RESIZE"
                    setOnClickListener {
                        riveView.updateLayoutParams { width = (ogWidth - 1).toInt() }
                    }

                    // After requesting resize, wait for the next layout pass to confirm it
                    riveView.doOnLayout { layoutCompleteLatch.countDown() }
                }

                (riveView.parent as ViewGroup).addView(button)
                button.performClick()
            }
        }

        assertTrue(
            "Timed out waiting for view to be re-laid out.",
            layoutCompleteLatch.await(3, TimeUnit.SECONDS)
        )

        var finalWidth: Float? = null
        activityScenario.onActivity { finalWidth = riveView.artboardRenderer?.width }

        assertEquals(ogWidth - 1f, finalWidth)

        // Close it down.
        activityScenario.close()

        // Background thread deallocates asynchronously, so let's wait for it.
        waitUntil(1500.milliseconds) {
            controller.refCount == 0 &&
                    controller.file == null &&
                    controller.activeArtboard == null
        }
        assertFalse(controller.isActive)
        assertNull(controller.file)
        assertNull(controller.activeArtboard)
    }
}

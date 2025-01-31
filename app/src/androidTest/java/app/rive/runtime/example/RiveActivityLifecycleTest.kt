package app.rive.runtime.example

import TestUtils.Companion.waitUntil
import android.view.ViewGroup
import android.widget.Button
import androidx.core.view.updateLayoutParams
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.Ignore
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
            // Defaults to Rive Renderer.
            assertEquals(RendererType.Rive, riveView.rendererAttributes.rendererType)
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
    fun withCustomLoader() {
        val activityScenario = ActivityScenario.launch(SingleActivity::class.java);
        lateinit var riveView: RiveAnimationView
        lateinit var controller: RiveFileController
        var assetLoader: FileAssetLoader? = null
        // Start the Activity.
        activityScenario.onActivity {
            riveView = it.findViewById(R.id.rive_single)
            controller = riveView.controller

            assetLoader = riveView.rendererAttributes.assetLoader
            assertTrue(assetLoader!!.hasCppObject)

            val nopAssetLoader = object : ContextAssetLoader(it) {
                override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean = true
            }
            riveView.setAssetLoader(nopAssetLoader)
            // release()'d the old one
            assertFalse(assetLoader!!.hasCppObject)
            assertNotNull(riveView.rendererAttributes.assetLoader)
            assetLoader = riveView.rendererAttributes.assetLoader
        }
        // Close it down.
        activityScenario.close()
        // Background thread deallocates asynchronously.
        waitUntil(1500.milliseconds) { controller.refCount == 0 }
        assertFalse(controller.isActive)
        assertNull(controller.file)
        assertNull(controller.activeArtboard)

        // New assetLoader needs to be freed by the creator
        assertTrue(assetLoader!!.hasCppObject)
        assetLoader?.release()
        assertFalse(assetLoader!!.hasCppObject)
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
        waitUntil(1500.milliseconds) { controller.refCount == 0 }
        assertFalse(controller.isActive)
        assertNull(controller.file)
        assertNull(controller.activeArtboard)
    }


    @Ignore("(umberto) disabling this test for now since it seems to be way too flaky for our bots.")
    @Test
    fun resizeRiveView() {
        val activityScenario = ActivityScenario.launch(SingleActivity::class.java);
        lateinit var riveView: RiveAnimationView
        lateinit var controller: RiveFileController
        var ogWidth = 0f
        // Start the Activity.
        activityScenario.onActivity {
            riveView = it.findViewById(R.id.rive_single)
            controller = riveView.controller

            assertEquals(2, controller.refCount)
            assertTrue(controller.isActive)
            assertNotNull(controller.file)
            assertNotNull(controller.activeArtboard)

            ogWidth = riveView.artboardRenderer!!.width

            var isResized = false

            Button(it).apply {
                text = "RESIZE"
                setOnClickListener {
                    riveView.updateLayoutParams {
                        width = (ogWidth - 1).toInt()
                    }
                    isResized = true
                }
                (riveView.parent as ViewGroup).addView(this)
                performClick()
            }
            assert(isResized)
        }

        // Close it down.
        activityScenario.close()
        // This assert is not very robust...
        assertEquals(ogWidth - 1f, riveView.artboardRenderer?.width)
        // Background thread deallocates asynchronously.
        waitUntil(1500.milliseconds) { controller.refCount == 0 }
        assertFalse(controller.isActive)
        assertNull(controller.file)
        assertNull(controller.activeArtboard)
    }

}
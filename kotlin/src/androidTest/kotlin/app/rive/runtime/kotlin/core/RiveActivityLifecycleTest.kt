package app.rive.runtime.kotlin.core

import androidx.core.view.doOnLayout
import androidx.core.view.doOnNextLayout
import androidx.core.view.updateLayoutParams
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.test.R
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises legacy Rive views through a real Android Activity lifecycle. */
@RunWith(AndroidJUnit4::class)
class RiveActivityLifecycleTest {
    /** Verifies XML inflation, renderer defaults, and cleanup when the Activity is destroyed. */
    @Test
    fun activityWithRiveView() {
        val activityScenario = ActivityScenario.launch(LegacyRiveTestActivity::class.java)
        lateinit var riveView: RiveAnimationView
        lateinit var controller: RiveFileController
        try {
            activityScenario.onActivity {
                riveView = it.findViewById(R.id.rive_test_view)
                controller = riveView.controller

                assertEquals(2, controller.refCount)
                assertTrue(controller.isActive)
                assertNotNull(controller.file)
                assertNotNull(controller.activeArtboard)
                assertEquals(RendererType.Rive, riveView.rendererAttributes.rendererType)
                assertEquals(
                    riveView.rendererAttributes.rendererType,
                    controller.file?.rendererType,
                )
            }
        } finally {
            activityScenario.close()
        }

        awaitControllerCleanup(controller)
    }

    /** Verifies asset-loader replacement ownership and cleanup through Activity destruction. */
    @Test
    fun withCustomLoader() {
        val activityScenario = ActivityScenario.launch(LegacyRiveTestActivity::class.java)
        lateinit var controller: RiveFileController
        lateinit var originalAssetLoader: FileAssetLoader
        lateinit var replacementAssetLoader: FileAssetLoader
        try {
            activityScenario.onActivity { activity ->
                val riveView = activity.findViewById<RiveAnimationView>(R.id.rive_test_view)
                controller = riveView.controller

                originalAssetLoader = checkNotNull(riveView.rendererAttributes.assetLoader)
                assertTrue(originalAssetLoader.hasCppObject)

                replacementAssetLoader = object : ContextAssetLoader(activity) {
                    override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean = true
                }
                assertEquals(2, originalAssetLoader.refCount)
                riveView.setAssetLoader(replacementAssetLoader)
                assertEquals(1, originalAssetLoader.refCount)
                assertEquals(replacementAssetLoader, riveView.rendererAttributes.assetLoader)
                assertEquals(2, replacementAssetLoader.refCount)
            }
        } finally {
            activityScenario.close()
        }

        TestUtils.waitUntil(CLEANUP_TIMEOUT) {
            controller.refCount == 0 &&
                !controller.isActive &&
                controller.file == null &&
                controller.activeArtboard == null &&
                !originalAssetLoader.hasCppObject
        }
        assertFalse(controller.isActive)
        assertNull(controller.file)
        assertNull(controller.activeArtboard)
        assertFalse(originalAssetLoader.hasCppObject)

        assertTrue(replacementAssetLoader.hasCppObject)
        replacementAssetLoader.release()
        assertFalse(replacementAssetLoader.hasCppObject)
    }

    /** Verifies a real layout pass updates the legacy artboard renderer's width. */
    @Test
    fun resizeRiveView() {
        val activityScenario = ActivityScenario.launch(LegacyRiveTestActivity::class.java)
        lateinit var riveView: RiveAnimationView
        lateinit var controller: RiveFileController
        var originalWidth = 0f
        val layoutCompleteLatch = CountDownLatch(1)

        try {
            activityScenario.onActivity { activity ->
                riveView = activity.findViewById(R.id.rive_test_view)
                controller = riveView.controller

                assertEquals(2, controller.refCount)
                assertTrue(controller.isActive)
                assertNotNull(controller.file)
                assertNotNull(controller.activeArtboard)

                riveView.doOnLayout {
                    originalWidth = checkNotNull(riveView.artboardRenderer).width
                    riveView.doOnNextLayout { layoutCompleteLatch.countDown() }
                    riveView.updateLayoutParams { width = (originalWidth - 1).toInt() }
                }
            }

            assertTrue(
                "Timed out waiting for view to be re-laid out.",
                layoutCompleteLatch.await(3, TimeUnit.SECONDS),
            )

            var finalWidth: Float? = null
            activityScenario.onActivity { finalWidth = riveView.artboardRenderer?.width }
            assertEquals(originalWidth - 1f, finalWidth)
        } finally {
            activityScenario.close()
        }

        awaitControllerCleanup(controller)
    }

    /**
     * Waits for a controller and its native resources to be released.
     *
     * @param controller The controller owned by the destroyed Activity's Rive view.
     */
    private fun awaitControllerCleanup(controller: RiveFileController) {
        TestUtils.waitUntil(CLEANUP_TIMEOUT) {
            controller.refCount == 0 &&
                !controller.isActive &&
                controller.file == null &&
                controller.activeArtboard == null
        }
        assertFalse(controller.isActive)
        assertNull(controller.file)
        assertNull(controller.activeArtboard)
    }

    private companion object {
        val CLEANUP_TIMEOUT = 1500.milliseconds
    }
}

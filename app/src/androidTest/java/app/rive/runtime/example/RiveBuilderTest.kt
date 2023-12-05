package app.rive.runtime.example

import TestUtils.Companion.waitUntil
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.CDNAssetLoader
import app.rive.runtime.kotlin.core.FallbackAssetLoader
import app.rive.runtime.kotlin.core.File
import app.rive.runtime.kotlin.core.FileAsset
import app.rive.runtime.kotlin.core.FileAssetLoader
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.Loop
import app.rive.runtime.kotlin.core.RendererType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds


@RunWith(AndroidJUnit4::class)
class RiveBuilderTest {
    @Test
    fun withIdResource() {
        val activityScenario = ActivityScenario.launch(EmptyActivity::class.java)
        lateinit var riveView: RiveAnimationView
        lateinit var controller: RiveFileController
        activityScenario.onActivity {
            riveView = RiveAnimationView.Builder(it)
                .setResource(R.raw.off_road_car_blog)
                .build()
            it.container.addView(riveView)
            controller = riveView.controller
            assertTrue(controller.isActive)
            assertEquals("New Artboard", controller.activeArtboard?.name)
            assertEquals(
                listOf("idle"),
                controller.playingAnimations.toList().map { anim -> anim.name })

        }
        activityScenario.close()
        // Background thread deallocates asynchronously.
        waitUntil(1500.milliseconds) { controller.refCount == 0 }
        assertFalse(controller.isActive)
        assertNull(controller.file)
        assertNull(controller.activeArtboard)
        // Asset loader was deallocated.
        assert(riveView.rendererAttributes.assetLoader?.hasCppObject == false)
    }

    @Test
    fun withFileResource() {
        val activityScenario = ActivityScenario.launch(EmptyActivity::class.java)
        lateinit var riveView: RiveAnimationView
        lateinit var controller: RiveFileController
        activityScenario.onActivity { activity ->
            val file = activity
                .resources
                .openRawResource(R.raw.basketball)
                .use { res -> File(res.readBytes()) }

            riveView = RiveAnimationView.Builder(activity)
                .setResource(file)
                .build()
            activity.container.addView(riveView)
            controller = riveView.controller
            assertTrue(controller.isActive)
            assertEquals("New Artboard", controller.activeArtboard?.name)
            assertEquals(
                listOf("idle"),
                controller.playingAnimations.toList().map { anim -> anim.name })
        }
        activityScenario.close()
        // Background thread deallocates asynchronously.
        waitUntil(1500.milliseconds) { controller.refCount == 0 }
        assertFalse(controller.isActive)
        assertNull(controller.file)
        assertNull(controller.activeArtboard)
        // Asset loader was deallocated.
        assert(riveView.rendererAttributes.assetLoader?.hasCppObject == false)
    }

    @Test
    fun withBytesResource() {
        val activityScenario = ActivityScenario.launch(EmptyActivity::class.java)
        lateinit var riveView: RiveAnimationView
        lateinit var controller: RiveFileController
        activityScenario.onActivity { activity ->
            val file = activity
                .resources
                .openRawResource(R.raw.basketball)
                .use { res -> res.readBytes() }
            riveView = RiveAnimationView.Builder(activity)
                .setResource(file)
                .build()
            activity.container.addView(riveView)
            controller = riveView.controller
            assertTrue(controller.isActive)
            assertEquals("New Artboard", controller.activeArtboard?.name)
            assertEquals(
                listOf("idle"),
                controller.playingAnimations.toList().map { anim -> anim.name })
        }
        activityScenario.close()
        // Background thread deallocates asynchronously.
        waitUntil(1500.milliseconds) { controller.refCount == 0 }
        assertFalse(controller.isActive)
        assertNull(controller.file)
        assertNull(controller.activeArtboard)
        // Asset loader was deallocated.
        assert(riveView.rendererAttributes.assetLoader?.hasCppObject == false)
    }

    @Test
    fun manyParameters() {
        val activityScenario = ActivityScenario.launch(EmptyActivity::class.java)
        lateinit var riveView: RiveAnimationView
        lateinit var controller: RiveFileController
        activityScenario.onActivity { activity ->
            riveView = RiveAnimationView.Builder(activity)
                .setAlignment(Alignment.BOTTOM_CENTER)
                .setFit(Fit.FIT_HEIGHT)
                .setLoop(Loop.PINGPONG)
                .setAutoplay(false)
                .setTraceAnimations(true)
                .setArtboardName("artboard2")
                .setAnimationName("artboard2animation1")
                .setResource(R.raw.multipleartboards)
                .build()
            activity.container.addView(riveView)
            controller = riveView.controller
            assertTrue(controller.isActive)
            assertFalse(controller.autoplay)
            assertEquals(Alignment.BOTTOM_CENTER, controller.alignment)
            assertEquals(Fit.FIT_HEIGHT, controller.fit)
            assertEquals(Loop.PINGPONG, controller.loop)
            assertTrue(riveView.artboardRenderer!!.trace)
            assertEquals("artboard2", controller.activeArtboard?.name)
            assertEquals(
                emptyList<String>(), // autoplay = false
                controller.playingAnimations.toList().map { anim -> anim.name })
        }
        activityScenario.close()
        // Background thread deallocates asynchronously.
        waitUntil(1500.milliseconds) { controller.refCount == 0 }
        assertFalse(controller.isActive)
        assertNull(controller.file)
        assertNull(controller.activeArtboard)
        // Asset loader was deallocated.
        assert(riveView.rendererAttributes.assetLoader?.hasCppObject == false)
    }

    @Test
    fun assetLoader() {
        val activityScenario = ActivityScenario.launch(EmptyActivity::class.java)
        lateinit var riveView: RiveAnimationView
        lateinit var controller: RiveFileController
        val assetStore = mutableListOf<FileAsset>()
        val customLoader = object : FileAssetLoader() {
            override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean {
                return assetStore.add(asset)
            }
        }

        activityScenario.onActivity { activity ->
            riveView = RiveAnimationView.Builder(activity)
                .setResource(R.raw.walle)
                .setAssetLoader(customLoader)
                .build()
            activity.container.addView(riveView)
            controller = riveView.controller
            val actualLoader = riveView.rendererAttributes.assetLoader
            assert(actualLoader is FallbackAssetLoader)
            val fallbackLoader = actualLoader as FallbackAssetLoader
            assertEquals(2, fallbackLoader.loaders.size)
            assertEquals(customLoader as FileAssetLoader, fallbackLoader.loaders.first())
            assert(fallbackLoader.loaders.last() is CDNAssetLoader)
            assertEquals(2, assetStore.size)
        }
        activityScenario.close()
        // Background thread deallocates asynchronously.
        waitUntil(1500.milliseconds) { controller.refCount == 0 }
        assertFalse(controller.isActive)
        assertNull(controller.file)
        assertNull(controller.activeArtboard)
        assertFalse(customLoader.hasCppObject)
    }

    @Test
    fun noCDNLoader() {
        val activityScenario = ActivityScenario.launch(EmptyActivity::class.java)
        lateinit var riveView: RiveAnimationView
        lateinit var controller: RiveFileController
        activityScenario.onActivity { activity ->
            riveView = RiveAnimationView.Builder(activity)
                .setResource(R.raw.walle)
                .setShouldLoadCDNAssets(false)
                .build()
            activity.container.addView(riveView)
            controller = riveView.controller

            val actualLoader = riveView.rendererAttributes.assetLoader
            assert(actualLoader is FallbackAssetLoader)
            val fallbackLoader = actualLoader as FallbackAssetLoader
            assertTrue(fallbackLoader.loaders.isEmpty())
        }
        activityScenario.close()
        // Background thread deallocates asynchronously.
        waitUntil(1500.milliseconds) { controller.refCount == 0 }
        assertFalse(controller.isActive)
        assertNull(controller.file)
        assertNull(controller.activeArtboard)
    }

    @Test
    fun withRendererType() {
        val activityScenario = ActivityScenario.launch(EmptyActivity::class.java)
        lateinit var riveView: RiveAnimationView
        lateinit var controller: RiveFileController
        activityScenario.onActivity { activity ->
            riveView = RiveAnimationView.Builder(activity)
                .setResource(R.raw.basketball)
                .setRendererType(RendererType.Canvas)
                .build()
            activity.container.addView(riveView)
            controller = riveView.controller
            assertNotNull(riveView.artboardRenderer)
            assertEquals(RendererType.Canvas, riveView.artboardRenderer?.type)
        }
        activityScenario.close()
        // Background thread deallocates asynchronously.
        waitUntil(1500.milliseconds) { controller.refCount == 0 }
        assertFalse(controller.isActive)
        assertNull(controller.file)
        assertNull(controller.activeArtboard)
    }

    @Test
    fun withStateMachineName() {
        val activityScenario = ActivityScenario.launch(EmptyActivity::class.java)
        lateinit var riveView: RiveAnimationView
        lateinit var controller: RiveFileController
        activityScenario.onActivity { activity ->
            riveView = RiveAnimationView.Builder(activity)
                .setResource(R.raw.what_a_state)
                .setStateMachineName("State Machine 2")
                .build()
            activity.container.addView(riveView)
            controller = riveView.controller
            assertEquals(1, controller.playingStateMachines.size)
            assertEquals("State Machine 2", controller.playingStateMachines.first().name)
        }
        activityScenario.close()
        // Background thread deallocates asynchronously.
        waitUntil(1500.milliseconds) { controller.refCount == 0 }
        assertFalse(controller.isActive)
        assertNull(controller.file)
        assertNull(controller.activeArtboard)
    }
}
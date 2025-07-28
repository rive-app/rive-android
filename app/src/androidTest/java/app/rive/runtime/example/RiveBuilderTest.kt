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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.milliseconds


@RunWith(AndroidJUnit4::class)
class RiveBuilderTest {

    private val cleanupTimeout = 1500.milliseconds

    @Test
    fun withIdResource() {
        val activityScenario = ActivityScenario.launch(EmptyActivity::class.java)
        lateinit var riveView: RiveAnimationView
        lateinit var controller: RiveFileController
        var capturedAssetLoader: FileAssetLoader? = null

        activityScenario.onActivity {
            riveView = RiveAnimationView.Builder(it)
                .setResource(R.raw.off_road_car_blog)
                .build()
            it.container.addView(riveView)
            controller = riveView.controller
            capturedAssetLoader = riveView.rendererAttributes.assetLoader
            assertTrue(controller.isActive)
            assertEquals("New Artboard", controller.activeArtboard?.name)
            assertEquals(
                listOf("idle"),
                controller.playingAnimations.toList().map { anim -> anim.name })
        }
        activityScenario.close()

        try {
            waitUntil(cleanupTimeout) {
                val refCountZero = controller.refCount == 0
                val isInactive = !controller.isActive
                val artboardIsNull = controller.activeArtboard == null
                val fileIsNull = controller.file == null
                val assetLoaderCppObjectGone = capturedAssetLoader?.hasCppObject == false

                refCountZero && isInactive && artboardIsNull && fileIsNull && assetLoaderCppObjectGone
            }
        } catch (e: TimeoutException) {
            throw AssertionError(
                "Cleanup conditions not met for withIdResource within $cleanupTimeout. Current state: " +
                        "controller.refCount=${controller.refCount}, controller.isActive=${controller.isActive}, " +
                        "controller.file=${controller.file}, controller.activeArtboard=${controller.activeArtboard}, " +
                        "assetLoader?.hasCppObject=${capturedAssetLoader?.hasCppObject}", e
            )
        }
    }

    @Test
    fun withFileResource() {
        val activityScenario = ActivityScenario.launch(EmptyActivity::class.java)
        lateinit var riveView: RiveAnimationView
        lateinit var controller: RiveFileController
        var capturedAssetLoader: FileAssetLoader? = null

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
            capturedAssetLoader = riveView.rendererAttributes.assetLoader
            assertTrue(controller.isActive)
            assertEquals("New Artboard", controller.activeArtboard?.name)
            assertEquals(
                listOf("idle"),
                controller.playingAnimations.toList().map { anim -> anim.name })
        }
        activityScenario.close()

        try {
            waitUntil(cleanupTimeout) {
                val refCountZero = controller.refCount == 0
                val isInactive = !controller.isActive
                val artboardIsNull = controller.activeArtboard == null
                val fileIsNull = controller.file == null
                val assetLoaderCppObjectGone = capturedAssetLoader?.hasCppObject == false

                refCountZero && isInactive && artboardIsNull && fileIsNull && assetLoaderCppObjectGone
            }
        } catch (e: TimeoutException) {
            throw AssertionError(
                "Cleanup conditions not met for withFileResource within $cleanupTimeout. Current state: " +
                        "controller.refCount=${controller.refCount}, controller.isActive=${controller.isActive}, " +
                        "controller.file=${controller.file}, controller.activeArtboard=${controller.activeArtboard}, " +
                        "assetLoader?.hasCppObject=${capturedAssetLoader?.hasCppObject}", e
            )
        }
    }

    @Test
    fun withBytesResource() {
        val activityScenario = ActivityScenario.launch(EmptyActivity::class.java)
        lateinit var riveView: RiveAnimationView
        lateinit var controller: RiveFileController
        var capturedAssetLoader: FileAssetLoader? = null

        activityScenario.onActivity { activity ->
            val fileBytes = activity
                .resources
                .openRawResource(R.raw.basketball)
                .use { res -> res.readBytes() }
            riveView = RiveAnimationView.Builder(activity)
                .setResource(fileBytes)
                .build()
            activity.container.addView(riveView)
            controller = riveView.controller
            capturedAssetLoader = riveView.rendererAttributes.assetLoader
            assertTrue(controller.isActive)
            assertEquals("New Artboard", controller.activeArtboard?.name)
            assertEquals(
                listOf("idle"),
                controller.playingAnimations.toList().map { anim -> anim.name })
        }
        activityScenario.close()

        try {
            waitUntil(cleanupTimeout) {
                val refCountZero = controller.refCount == 0
                val isInactive = !controller.isActive
                val artboardIsNull = controller.activeArtboard == null
                val fileIsNull = controller.file == null
                val assetLoaderCppObjectGone = capturedAssetLoader?.hasCppObject == false

                refCountZero && isInactive && artboardIsNull && fileIsNull && assetLoaderCppObjectGone
            }
        } catch (e: TimeoutException) {
            throw AssertionError(
                "Cleanup conditions not met for withBytesResource within $cleanupTimeout. Current state: " +
                        "controller.refCount=${controller.refCount}, controller.isActive=${controller.isActive}, " +
                        "controller.file=${controller.file}, controller.activeArtboard=${controller.activeArtboard}, " +
                        "assetLoader?.hasCppObject=${capturedAssetLoader?.hasCppObject}", e
            )
        }
    }

    @Test
    fun manyParameters() {
        val activityScenario = ActivityScenario.launch(EmptyActivity::class.java)
        lateinit var riveView: RiveAnimationView
        lateinit var controller: RiveFileController
        var capturedAssetLoader: FileAssetLoader? = null

        activityScenario.onActivity { activity ->
            riveView = RiveAnimationView.Builder(activity)
                .setAlignment(Alignment.BOTTOM_CENTER)
                .setFit(Fit.FIT_HEIGHT)
                .setLoop(Loop.PINGPONG)
                .setAutoplay(false)
                .setAutoBind(true)
                .setTraceAnimations(true)
                .setArtboardName("artboard2")
                .setAnimationName("artboard2animation1")
                .setResource(R.raw.multipleartboards)
                .build()
            activity.container.addView(riveView)
            controller = riveView.controller
            capturedAssetLoader = riveView.rendererAttributes.assetLoader
            assertTrue(controller.isActive)
            assertFalse(controller.autoplay)
            assertNotNull(controller.activeArtboard?.viewModelInstance)
            assertEquals(Alignment.BOTTOM_CENTER, controller.alignment)
            assertEquals(Fit.FIT_HEIGHT, controller.fit)
            assertEquals(Loop.PINGPONG, controller.loop)
            assertTrue(riveView.artboardRenderer!!.trace)
            assertEquals("artboard2", controller.activeArtboard?.name)
            assertEquals(
                emptyList<String>(),
                controller.playingAnimations.toList().map { anim -> anim.name })
        }
        activityScenario.close()

        try {
            waitUntil(cleanupTimeout) {
                val refCountZero = controller.refCount == 0
                val isInactive = !controller.isActive
                val artboardIsNull = controller.activeArtboard == null
                val fileIsNull = controller.file == null
                val assetLoaderCppObjectGone = capturedAssetLoader?.hasCppObject == false
                refCountZero && isInactive && artboardIsNull && fileIsNull && assetLoaderCppObjectGone
            }
        } catch (e: TimeoutException) {
            throw AssertionError(
                "Cleanup conditions not met for manyParameters within $cleanupTimeout. Current state: " +
                        "controller.refCount=${controller.refCount}, controller.isActive=${controller.isActive}, " +
                        "controller.file=${controller.file}, controller.activeArtboard=${controller.activeArtboard}, " +
                        "assetLoader?.hasCppObject=${capturedAssetLoader?.hasCppObject}", e
            )
        }
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
            assertTrue(actualLoader is FallbackAssetLoader)
            val fallbackLoader = actualLoader as FallbackAssetLoader
            assertEquals(2, fallbackLoader.loaders.size)
            assertEquals(customLoader, fallbackLoader.loaders.first())
            assertTrue(fallbackLoader.loaders.last() is CDNAssetLoader)
            assertEquals(2, assetStore.size)
        }
        activityScenario.close()

        try {
            waitUntil(cleanupTimeout) {
                val refCountZero = controller.refCount == 0
                val isInactive = !controller.isActive
                val artboardIsNull = controller.activeArtboard == null
                val fileIsNull = controller.file == null
                val customLoaderCppObjectGone = !customLoader.hasCppObject

                refCountZero && isInactive && artboardIsNull && fileIsNull && customLoaderCppObjectGone
            }
        } catch (e: TimeoutException) {
            throw AssertionError(
                "Cleanup conditions not met for assetLoader within $cleanupTimeout. Current state: " +
                        "controller.refCount=${controller.refCount}, controller.isActive=${controller.isActive}, " +
                        "controller.file=${controller.file}, controller.activeArtboard=${controller.activeArtboard}, " +
                        "customLoader.hasCppObject=${customLoader.hasCppObject}", e
            )
        }
    }

    @Test
    fun noCDNLoader() {
        val activityScenario = ActivityScenario.launch(EmptyActivity::class.java)
        lateinit var riveView: RiveAnimationView
        lateinit var controller: RiveFileController
        var capturedAssetLoader: FileAssetLoader? = null

        activityScenario.onActivity { activity ->
            riveView = RiveAnimationView.Builder(activity)
                .setResource(R.raw.walle)
                .setShouldLoadCDNAssets(false)
                .build()
            activity.container.addView(riveView)
            controller = riveView.controller
            capturedAssetLoader = riveView.rendererAttributes.assetLoader

            val actualLoader = riveView.rendererAttributes.assetLoader
            assertTrue(actualLoader is FallbackAssetLoader)
            val fallbackLoader = actualLoader as FallbackAssetLoader
            assertTrue(fallbackLoader.loaders.isEmpty())
        }
        activityScenario.close()

        try {
            waitUntil(cleanupTimeout) {
                val refCountZero = controller.refCount == 0
                val isInactive = !controller.isActive
                val artboardIsNull = controller.activeArtboard == null
                val fileIsNull = controller.file == null
                val assetLoaderCppObjectGone = capturedAssetLoader?.hasCppObject == false

                refCountZero && isInactive && artboardIsNull && fileIsNull && assetLoaderCppObjectGone
            }
        } catch (e: TimeoutException) {
            throw AssertionError(
                "Cleanup conditions not met for noCDNLoader within $cleanupTimeout. Current state: " +
                        "controller.refCount=${controller.refCount}, controller.isActive=${controller.isActive}, " +
                        "controller.file=${controller.file}, controller.activeArtboard=${controller.activeArtboard}, " +
                        "assetLoader?.hasCppObject=${capturedAssetLoader?.hasCppObject}", e
            )
        }
    }

    @Test
    fun withRendererType() {
        val activityScenario = ActivityScenario.launch(EmptyActivity::class.java)
        lateinit var riveView: RiveAnimationView
        lateinit var controller: RiveFileController
        var capturedAssetLoader: FileAssetLoader? = null

        activityScenario.onActivity { activity ->
            riveView = RiveAnimationView.Builder(activity)
                .setResource(R.raw.basketball)
                .setRendererType(RendererType.Canvas)
                .build()
            activity.container.addView(riveView)
            controller = riveView.controller
            capturedAssetLoader = riveView.rendererAttributes.assetLoader
            assertNotNull(riveView.artboardRenderer)
            assertEquals(RendererType.Canvas, riveView.artboardRenderer?.type)
        }
        activityScenario.close()

        try {
            waitUntil(cleanupTimeout) {
                val refCountZero = controller.refCount == 0
                val isInactive = !controller.isActive
                val artboardIsNull = controller.activeArtboard == null
                val fileIsNull = controller.file == null
                val assetLoaderCppObjectGone = capturedAssetLoader?.hasCppObject == false

                refCountZero && isInactive && artboardIsNull && fileIsNull && assetLoaderCppObjectGone
            }
        } catch (e: TimeoutException) {
            throw AssertionError(
                "Cleanup conditions not met for withRendererType within $cleanupTimeout. Current state: " +
                        "controller.refCount=${controller.refCount}, controller.isActive=${controller.isActive}, " +
                        "controller.file=${controller.file}, controller.activeArtboard=${controller.activeArtboard}, " +
                        "assetLoader?.hasCppObject=${capturedAssetLoader?.hasCppObject}", e
            )
        }
    }

    @Test
    fun withStateMachineName() {
        val activityScenario = ActivityScenario.launch(EmptyActivity::class.java)
        lateinit var riveView: RiveAnimationView
        lateinit var controller: RiveFileController
        var capturedAssetLoader: FileAssetLoader? = null

        activityScenario.onActivity { activity ->
            riveView = RiveAnimationView.Builder(activity)
                .setResource(R.raw.what_a_state)
                .setStateMachineName("State Machine 2")
                .build()
            activity.container.addView(riveView)
            controller = riveView.controller
            capturedAssetLoader = riveView.rendererAttributes.assetLoader
            assertEquals(1, controller.playingStateMachines.size)
            assertEquals("State Machine 2", controller.playingStateMachines.first().name)
        }
        activityScenario.close()

        try {
            waitUntil(cleanupTimeout) {
                val refCountZero = controller.refCount == 0
                val isInactive = !controller.isActive
                val artboardIsNull = controller.activeArtboard == null
                val fileIsNull = controller.file == null
                val assetLoaderCppObjectGone = capturedAssetLoader?.hasCppObject == false
                
                refCountZero && isInactive && artboardIsNull && fileIsNull && assetLoaderCppObjectGone
            }
        } catch (e: TimeoutException) {
            throw AssertionError(
                "Cleanup conditions not met for withStateMachineName within $cleanupTimeout. Current state: " +
                        "controller.refCount=${controller.refCount}, controller.isActive=${controller.isActive}, " +
                        "controller.file=${controller.file}, controller.activeArtboard=${controller.activeArtboard}, " +
                        "assetLoader?.hasCppObject=${capturedAssetLoader?.hasCppObject}", e
            )
        }
    }
}

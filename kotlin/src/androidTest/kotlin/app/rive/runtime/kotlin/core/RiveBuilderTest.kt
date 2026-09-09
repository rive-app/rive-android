package app.rive.runtime.kotlin.core

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.test.R
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the legacy Rive view builder through a real Android Activity. */
@RunWith(AndroidJUnit4::class)
class RiveBuilderTest {
    /** Initializes the native runtime without depending on another test having run first. */
    @Before
    fun initializeRive() {
        Rive.init(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    /** Verifies the builder loads an Android raw resource ID. */
    @Test
    fun withIdResource() {
        runBuilderTest(
            testName = "withIdResource",
            buildView = { activity ->
                RiveAnimationView.Builder(activity)
                    .setResource(R.raw.off_road_car_blog)
                    .build()
            },
        ) { _, controller ->
            assertDefaultResourceLoaded(controller)
        }
    }

    /** Verifies the builder loads an existing legacy Rive file. */
    @Test
    fun withFileResource() {
        var file: File? = null
        try {
            runBuilderTest(
                testName = "withFileResource",
                buildView = { activity ->
                    val loadedFile = activity.resources
                        .openRawResource(R.raw.off_road_car_blog)
                        .use { stream -> File(stream.readBytes()) }
                    file = loadedFile
                    RiveAnimationView.Builder(activity)
                        .setResource(loadedFile)
                        .build()
                },
            ) { _, controller ->
                assertDefaultResourceLoaded(controller)
            }
        } finally {
            // The view releases its acquired reference, not the caller's original reference.
            file?.release()
        }
        assertEquals(0, checkNotNull(file).refCount)
        assertFalse(checkNotNull(file).hasCppObject)
    }

    /** Verifies the builder loads Rive file bytes. */
    @Test
    fun withBytesResource() {
        runBuilderTest(
            testName = "withBytesResource",
            buildView = { activity ->
                val bytes = activity.resources
                    .openRawResource(R.raw.off_road_car_blog)
                    .use { stream -> stream.readBytes() }
                RiveAnimationView.Builder(activity)
                    .setResource(bytes)
                    .build()
            },
        ) { _, controller ->
            assertDefaultResourceLoaded(controller)
        }
    }

    /** Verifies builder playback, layout, tracing, artboard, and data-binding options. */
    @Test
    fun manyParameters() {
        runBuilderTest(
            testName = "manyParameters",
            buildView = { activity ->
                RiveAnimationView.Builder(activity)
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
            },
        ) { riveView, controller ->
            assertTrue(controller.isActive)
            assertFalse(controller.autoplay)
            // This fixture has no default VMI; actual binding is covered by RiveDataBindingTest.
            assertTrue(riveView.rendererAttributes.autoBind)
            assertEquals(Alignment.BOTTOM_CENTER, controller.alignment)
            assertEquals(Fit.FIT_HEIGHT, controller.fit)
            assertEquals(Loop.PINGPONG, controller.loop)
            assertTrue(checkNotNull(riveView.artboardRenderer).trace)
            assertEquals("artboard2", controller.activeArtboard?.name)
            assertTrue(controller.playingAnimations.isEmpty())
        }
    }

    /** Verifies a custom loader precedes the default CDN loader and is released on teardown. */
    @Test
    fun assetLoader() {
        val loadedAssets = mutableListOf<FileAsset>()
        val customLoader = object : FileAssetLoader() {
            override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean = loadedAssets.add(asset)
        }

        runBuilderTest(
            testName = "assetLoader",
            buildView = { activity ->
                RiveAnimationView.Builder(activity)
                    .setResource(R.raw.walle)
                    .setAssetLoader(customLoader)
                    .build()
            },
        ) { riveView, _ ->
            val fallbackLoader = riveView.rendererAttributes.assetLoader as FallbackAssetLoader
            assertEquals(2, fallbackLoader.loaders.size)
            assertEquals(customLoader, fallbackLoader.loaders.first())
            assertTrue(fallbackLoader.loaders.last() is CDNAssetLoader)
            assertEquals(2, loadedAssets.size)
        }

        assertFalse(customLoader.hasCppObject)
    }

    /** Verifies disabling CDN loading produces an empty fallback loader chain. */
    @Test
    fun noCDNLoader() {
        runBuilderTest(
            testName = "noCDNLoader",
            buildView = { activity ->
                RiveAnimationView.Builder(activity)
                    .setResource(R.raw.walle)
                    .setShouldLoadCDNAssets(false)
                    .build()
            },
        ) { riveView, _ ->
            val fallbackLoader = riveView.rendererAttributes.assetLoader as FallbackAssetLoader
            assertTrue(fallbackLoader.loaders.isEmpty())
        }
    }

    /** Verifies the builder creates a renderer of the requested legacy type. */
    @Test
    fun withRendererType() {
        runBuilderTest(
            testName = "withRendererType",
            buildView = { activity ->
                RiveAnimationView.Builder(activity)
                    .setResource(R.raw.off_road_car_blog)
                    .setRendererType(RendererType.Canvas)
                    .build()
            },
        ) { riveView, _ ->
            assertEquals(RendererType.Canvas, checkNotNull(riveView.artboardRenderer).type)
        }
    }

    /** Verifies the builder starts the requested state machine. */
    @Test
    fun withStateMachineName() {
        runBuilderTest(
            testName = "withStateMachineName",
            buildView = { activity ->
                RiveAnimationView.Builder(activity)
                    .setResource(R.raw.what_a_state)
                    .setStateMachineName("State Machine 2")
                    .build()
            },
        ) { _, controller ->
            assertEquals(1, controller.playingStateMachines.size)
            assertEquals("State Machine 2", controller.playingStateMachines.first().name)
        }
    }

    /**
     * Builds and attaches a legacy view, verifies it, and checks asynchronous native cleanup.
     *
     * @param testName Name included in cleanup failures.
     * @param buildView Creates the view for the launched Activity.
     * @param verify Assertions to run after the view is attached.
     */
    private fun runBuilderTest(
        testName: String,
        buildView: (LegacyEmptyTestActivity) -> RiveAnimationView,
        verify: (RiveAnimationView, RiveFileController) -> Unit,
    ) {
        val activityScenario = ActivityScenario.launch(LegacyEmptyTestActivity::class.java)
        lateinit var controller: RiveFileController
        lateinit var assetLoader: FileAssetLoader
        try {
            activityScenario.onActivity { activity ->
                val riveView = buildView(activity)
                activity.container.addView(riveView)
                controller = riveView.controller
                assetLoader = checkNotNull(riveView.rendererAttributes.assetLoader)
                verify(riveView, controller)
            }
        } finally {
            activityScenario.close()
        }

        try {
            TestUtils.waitUntil(CLEANUP_TIMEOUT) {
                controller.refCount == 0 &&
                    !controller.isActive &&
                    controller.file == null &&
                    controller.activeArtboard == null &&
                    !assetLoader.hasCppObject
            }
        } catch (exception: TimeoutException) {
            throw AssertionError(
                "Cleanup conditions not met for $testName within $CLEANUP_TIMEOUT. " +
                    "Current state: " +
                    "controller.refCount=${controller.refCount}, " +
                    "controller.isActive=${controller.isActive}, " +
                    "controller.file=${controller.file}, " +
                    "controller.activeArtboard=${controller.activeArtboard}, " +
                    "assetLoader.hasCppObject=${assetLoader.hasCppObject}",
                exception,
            )
        }
    }

    /**
     * Verifies the default test asset is loaded and playing.
     *
     * @param controller Controller initialized by the attached view.
     */
    private fun assertDefaultResourceLoaded(controller: RiveFileController) {
        assertTrue(controller.isActive)
        assertEquals("New Artboard", controller.activeArtboard?.name)
        assertEquals(
            listOf("idle"),
            controller.playingAnimations.map { animation -> animation.name },
        )
    }

    private companion object {
        val CLEANUP_TIMEOUT = 1500.milliseconds
    }
}

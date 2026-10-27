package app.rive.compose

import android.graphics.SurfaceTexture
import android.os.Build
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.Rive
import app.rive.RiveAndroidTest
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.core.CommandQueuePoller
import app.rive.core.DefaultRiveResources
import app.rive.core.RiveWorker
import app.rive.core.assertDisposed
import app.rive.core.loadDefaultRiveResources
import app.rive.runtime.kotlin.test.R
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith

/** Tests surface creation and destruction owned by the Rive composable. */
@RunWith(AndroidJUnit4::class)
class RiveSurfaceComposeTest : RiveAndroidTest() {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Verifies invalid dimensions defer ownership until a positive size callback arrives. */
    @Test
    fun rive_invalidInitialDimensions_createsSurfaceOnValidResize() =
        withSurfaceCallbacks(expectedReleaseByAndroid = false) { listener, texture, worker ->
            val initialReferences = worker.refCount
            listener.onSurfaceTextureAvailable(texture, 0, 16)
            assertEquals(initialReferences, worker.refCount)

            for ((width, height) in listOf(16 to 0, -1 to 16, 16 to -1)) {
                listener.onSurfaceTextureSizeChanged(texture, width, height)
                assertEquals(initialReferences, worker.refCount)
            }

            listener.onSurfaceTextureSizeChanged(texture, 16, 16)
            assertEquals(initialReferences + 1, worker.refCount)
            // Subsequent valid sizes resize the existing surface instead of acquiring another.
            listener.onSurfaceTextureSizeChanged(texture, 32, 16)
            assertEquals(initialReferences + 1, worker.refCount)
        }

    /** Verifies destruction before any valid size leaves texture release to Android. */
    @Test
    fun rive_invalidInitialDimensions_destroyedBeforeValidResize_retainsAndroidOwnership() =
        withSurfaceCallbacks(expectedReleaseByAndroid = true) { listener, texture, worker ->
            val initialReferences = worker.refCount
            listener.onSurfaceTextureAvailable(texture, 16, 0)
            assertEquals(initialReferences, worker.refCount)
        }

    /** Verifies invalid resizes preserve ownership and allow a later valid resize. */
    @Test
    fun rive_invalidResize_preservesExistingSurface() =
        withSurfaceCallbacks(expectedReleaseByAndroid = false) { listener, texture, worker ->
            val initialReferences = worker.refCount
            listener.onSurfaceTextureAvailable(texture, 16, 16)
            assertEquals(initialReferences + 1, worker.refCount)

            for ((width, height) in listOf(0 to 16, 16 to 0, -1 to 16, 16 to -1)) {
                listener.onSurfaceTextureSizeChanged(texture, width, height)
                assertEquals(initialReferences + 1, worker.refCount)
            }

            listener.onSurfaceTextureSizeChanged(texture, 32, 16)
            assertEquals(initialReferences + 1, worker.refCount)
        }

    /** Verifies destruction releases a surface even before Compose observes its creation. */
    @Test
    fun rive_surfaceDestroyedBeforeRecomposition_releasesSurface() = runBlocking<Unit> {
        val worker = RiveWorker()
        val poller = CommandQueuePoller(worker)
        val file = RiveFile.load(RiveFileSource.RawRes(R.raw.empty, context.resources), worker)
        lateinit var showContent: MutableState<Boolean>
        val texture = SurfaceTexture(false)

        try {
            composeRule.setContent {
                showContent = remember { mutableStateOf(true) }
                if (showContent.value) {
                    // Keep the real host from acquiring a surface while we drive its callbacks.
                    Rive(file = file, modifier = Modifier.size(0.dp), playing = false)
                }
            }
            composeRule.awaitWithWallClock(
                timeoutMessage = { "The Rive texture view was not created" },
            ) {
                currentTextureView() != null
            }
            composeRule.waitForIdle()

            composeRule.runOnUiThread {
                val listener = checkNotNull(currentTextureView()?.surfaceTextureListener)
                val referencesBeforeCreation = worker.refCount
                // Both callbacks run before Compose can observe the intermediate surface state.
                listener.onSurfaceTextureAvailable(texture, 16, 16)
                assertEquals(referencesBeforeCreation + 1, worker.refCount)
                assertFalse(listener.onSurfaceTextureDestroyed(texture))
                assertEquals(referencesBeforeCreation, worker.refCount)
            }
            // Ownership assertions above run on every supported API level.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                composeRule.awaitWithWallClock(
                    timeoutMessage = { "The Rive worker did not release the destroyed texture" },
                ) {
                    texture.isReleased
                }
            }
        } finally {
            composeRule.runOnUiThread { showContent.value = false }
            composeRule.waitForIdle()
            file.close()
            poller.close()
            worker.release(javaClass.simpleName, "Test cleanup")
            assertDisposed(worker)
        }
    }

    /** Verifies replacing a file from another worker creates and renders to a fresh surface. */
    @Test
    fun rive_recreatesSurface_whenWorkerChanges() = runBlocking<Unit> {
        val firstResources = loadDefaultRiveResources(R.raw.empty)
        val secondWorker = RiveWorker()
        val secondPoller = CommandQueuePoller(secondWorker)
        val secondResources = secondWorker.loadDefaultRiveResources(R.raw.empty)
        lateinit var activeResources: MutableState<DefaultRiveResources>
        var showContent: MutableState<Boolean>? = null
        val renderedFrameCount = AtomicInteger()

        // Handles are worker-local, so this also exercises a worker replacement whose native
        // effect keys would otherwise appear unchanged.
        assertEquals(
            firstResources.artboard.artboardHandle,
            secondResources.artboard.artboardHandle
        )
        assertEquals(
            firstResources.stateMachine.stateMachineHandle,
            secondResources.stateMachine.stateMachineHandle,
        )

        try {
            composeRule.setContent {
                activeResources = remember { mutableStateOf(firstResources) }
                val activeShowContent = remember { mutableStateOf(true) }
                showContent = activeShowContent
                if (activeShowContent.value) {
                    val resources = activeResources.value
                    Rive(
                        file = resources.file,
                        modifier = Modifier.size(100.dp),
                        playing = false,
                        artboard = resources.artboard,
                        stateMachine = resources.stateMachine,
                        onBitmapAvailable = { renderedFrameCount.incrementAndGet() },
                    )
                }
            }

            composeRule.awaitWithWallClock(
                timeoutMessage = { "The first worker did not render a frame" },
            ) {
                renderedFrameCount.get() >= 1
            }

            composeRule.runOnUiThread {
                activeResources.value = secondResources
            }

            composeRule.awaitWithWallClock(
                timeoutMessage = { "The replacement worker did not render to a fresh surface" },
            ) {
                renderedFrameCount.get() >= 2
            }
        } finally {
            showContent?.let { activeShowContent ->
                composeRule.runOnUiThread { activeShowContent.value = false }
                composeRule.waitForIdle()
            }
            secondResources.close()
            secondPoller.close()
            secondWorker.release(javaClass.simpleName, "Test cleanup")
            assertDisposed(secondWorker)
        }
    }

    /** Verifies same-worker resource loading does not remove the active rendering surface. */
    @Test
    fun rive_preservesSurface_whileReplacementResourcesLoad() = runBlocking<Unit> {
        val worker = RiveWorker()
        val poller = CommandQueuePoller(worker)
        val source = RiveFileSource.RawRes(R.raw.empty, context.resources)
        val firstFile = RiveFile.load(source, worker)
        val secondFile = RiveFile.load(source, worker)
        lateinit var activeFile: MutableState<RiveFile>
        var showContent: MutableState<Boolean>? = null
        val renderedFrameCount = AtomicInteger()

        try {
            composeRule.setContent {
                activeFile = remember { mutableStateOf(firstFile) }
                val activeShowContent = remember { mutableStateOf(true) }
                showContent = activeShowContent
                if (activeShowContent.value) {
                    Rive(
                        file = activeFile.value,
                        modifier = Modifier.size(100.dp),
                        playing = false,
                        onBitmapAvailable = { renderedFrameCount.incrementAndGet() },
                    )
                }
            }

            composeRule.awaitWithWallClock(
                timeoutMessage = { "The initial file did not create a surface and render" },
            ) {
                renderedFrameCount.get() >= 1
            }
            val initialTextureView = checkNotNull(currentTextureView())
            assertTrue(initialTextureView.isAvailable)
            val initialSurfaceTexture = checkNotNull(initialTextureView.surfaceTexture)

            poller.withPollingPaused {
                composeRule.runOnUiThread {
                    activeFile.value = secondFile
                }
                composeRule.waitForIdle()

                val loadingTextureView = checkNotNull(currentTextureView())
                assertSame(initialTextureView, loadingTextureView)
                assertSame(initialSurfaceTexture, loadingTextureView.surfaceTexture)
                assertEquals(1, renderedFrameCount.get())
            }
        } finally {
            showContent?.let { activeShowContent ->
                composeRule.runOnUiThread { activeShowContent.value = false }
                composeRule.waitForIdle()
            }
            firstFile.close()
            secondFile.close()
            poller.close()
            worker.release(javaClass.simpleName, "Test cleanup")
            assertDisposed(worker)
        }
    }

    /**
     * Drives a zero-size host's callbacks with a synthetic texture, then verifies destruction.
     *
     * @param expectedReleaseByAndroid Whether destruction should leave release to Android.
     * @param test Callback sequence run on the UI thread before destroying the texture.
     */
    private fun withSurfaceCallbacks(
        expectedReleaseByAndroid: Boolean,
        test: (TextureView.SurfaceTextureListener, SurfaceTexture, RiveWorker) -> Unit,
    ) = runBlocking<Unit> {
        val worker = RiveWorker()
        val poller = CommandQueuePoller(worker)
        val file = RiveFile.load(RiveFileSource.RawRes(R.raw.empty, context.resources), worker)
        var showContent: MutableState<Boolean>? = null

        try {
            composeRule.setContent {
                val activeShowContent = remember { mutableStateOf(true) }
                showContent = activeShowContent
                if (activeShowContent.value) {
                    // The actual host must not acquire a surface while callbacks are driven manually.
                    Rive(file = file, modifier = Modifier.size(0.dp), playing = false)
                }
            }
            composeRule.awaitWithWallClock(
                timeoutMessage = { "The Rive texture view was not created" },
            ) {
                currentTextureView() != null
            }
            composeRule.waitForIdle()
            val listener = checkNotNull(currentTextureView()?.surfaceTextureListener)
            val texture = SurfaceTexture(false)

            composeRule.runOnUiThread {
                val initialReferences = worker.refCount
                var releaseByAndroid: Boolean
                try {
                    test(listener, texture, worker)
                } finally {
                    releaseByAndroid = listener.onSurfaceTextureDestroyed(texture)
                    if (releaseByAndroid) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                assertFalse(texture.isReleased)
                            }
                        } finally {
                            // Manual callback dispatch bypasses TextureView's automatic release.
                            texture.release()
                        }
                    }
                }
                assertEquals(expectedReleaseByAndroid, releaseByAndroid)
                assertEquals(initialReferences, worker.refCount)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                composeRule.awaitWithWallClock(
                    timeoutMessage = { "The destroyed texture was not released" },
                ) {
                    texture.isReleased
                }
            }
        } finally {
            showContent?.let { activeShowContent ->
                composeRule.runOnUiThread { activeShowContent.value = false }
                composeRule.waitForIdle()
            }
            file.close()
            poller.close()
            worker.release(javaClass.simpleName, "Test cleanup")
            assertDisposed(worker)
        }
    }

    /** Returns the TextureView currently hosted by the Rive composable, if one exists. */
    private fun currentTextureView(): TextureView? {
        val textureView = AtomicReference<TextureView?>()
        composeRule.activityRule.scenario.onActivity { activity ->
            textureView.set(activity.window.decorView.findTextureView())
        }
        return textureView.get()
    }
}

/** Returns the first TextureView in this view subtree, if present. */
private fun View.findTextureView(): TextureView? {
    if (this is TextureView) {
        return this
    }
    if (this !is ViewGroup) {
        return null
    }
    for (index in 0 until childCount) {
        getChildAt(index).findTextureView()?.let { return it }
    }
    return null
}

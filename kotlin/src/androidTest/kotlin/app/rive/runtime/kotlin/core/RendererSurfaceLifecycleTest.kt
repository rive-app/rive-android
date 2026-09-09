package app.rive.runtime.kotlin.core

import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.SharedSurface
import app.rive.runtime.kotlin.renderers.Renderer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RendererSurfaceLifecycleTest {
    private val testUtils = TestUtils()

    /**
     * Verifies that replacing a surface does not release it while a queued frame still owns its
     * native worker implementation.
     */
    @Test
    fun replacedSurfaceIsRetainedUntilNativeTeardownCompletes() {
        assertSurfaceRetainedUntilTeardown { renderer, replacement ->
            renderer.stop()
            renderer.setSurface(replacement)
        }
    }

    /** Verifies the TextureView destruction path retains a surface through an in-flight frame. */
    @Test
    fun destroyedSurfaceIsRetainedUntilNativeTeardownCompletes() {
        assertSurfaceRetainedUntilTeardown { renderer, _ -> renderer.destroySurfaceAsync() }
    }

    /** Verifies deferred ownership also covers the Rive renderer's native window and EGL surface. */
    @Test
    fun riveSurfaceIsRetainedUntilNativeTeardownCompletes() {
        assertSurfaceRetainedUntilTeardown(RendererType.Rive) { renderer, _ ->
            renderer.destroySurfaceAsync()
        }
    }

    /**
     * Blocks a native frame while the test queues a surface lifecycle change.
     *
     * @param rendererType Backend whose native surface ownership is exercised.
     * @param teardown Operation that queues teardown of the first surface.
     */
    private fun assertSurfaceRetainedUntilTeardown(
        rendererType: RendererType = RendererType.Canvas,
        teardown: (TestSurfaceRenderer, SharedSurface) -> Unit,
    ) {
        // Initialize JNI before constructing the renderer.
        testUtils.context
        val firstTexture = SurfaceTexture(101)
        val secondTexture = SurfaceTexture(102)
        val firstSurface = SharedSurface(Surface(firstTexture))
        val secondSurface = SharedSurface(Surface(secondTexture))
        val drawStarted = CountDownLatch(1)
        val finishDraw = CountDownLatch(1)
        val drawFinishedInTime = AtomicBoolean(false)
        val drawFailure = AtomicReference<Throwable?>()
        val renderer =
            TestSurfaceRenderer(rendererType).apply {
                drawAction = {
                    drawStarted.countDown()
                    try {
                        drawFinishedInTime.set(finishDraw.await(2, TimeUnit.SECONDS))
                    } catch (failure: Throwable) {
                        // Never throw through the native worker's JNI draw callback.
                        drawFailure.set(failure)
                    }
                }
                make()
                setSurface(firstSurface)
                doFrame(System.nanoTime())
            }

        try {
            // A Rive test must not silently pass through the Canvas fallback.
            assertEquals("Unexpected renderer fallback", rendererType, renderer.type)
            assertTrue(drawStarted.await(2, TimeUnit.SECONDS))

            renderer.drawAction = {}
            teardown(renderer, secondSurface)
            firstSurface.release() // Release the creator's reference, leaving only the renderer's.

            assertEquals(1, firstSurface.refCount)
            assertTrue(firstSurface.surface.isValid)

            finishDraw.countDown()
            TestUtils.waitUntil(2.seconds) { firstSurface.refCount == 0 }
            drawFailure.get()?.let { throw AssertionError("Worker draw wait failed", it) }
            assertTrue("Worker draw wait timed out", drawFinishedInTime.get())

            assertFalse(firstSurface.surface.isValid)
        } finally {
            finishDraw.countDown()
            renderer.delete()
            assertTrue(renderer.disposed.await(2, TimeUnit.SECONDS))
            if (firstSurface.refCount > 0) {
                firstSurface.release()
            }
            secondSurface.release()
            firstTexture.release()
            secondTexture.release()
        }
    }

    /**
     * Verifies that released-Surface exceptions from both Canvas frame boundaries abort only their
     * current frames and leave the renderer worker able to draw a subsequent valid frame.
     */
    @Test
    fun canvasSurfaceExceptionsAbortFramesWithoutDetachingWorker() {
        // Initialize JNI before constructing the renderer.
        testUtils.context
        val lockTexture = SurfaceTexture(103)
        val postTexture = SurfaceTexture(104)
        val validTexture = SurfaceTexture(105)
        val lockSurface = SharedSurface(Surface(lockTexture))
        val postSurface = SharedSurface(Surface(postTexture))
        val validSurface = SharedSurface(Surface(validTexture))
        val lockSurfaceDrawn = AtomicBoolean(false)
        val postDrawn = CountDownLatch(1)
        val validDrawn = CountDownLatch(1)
        val validPosted = CountDownLatch(1)
        validTexture.setOnFrameAvailableListener(
            { validPosted.countDown() },
            Handler(Looper.getMainLooper()),
        )
        val renderer =
            TestSurfaceRenderer().apply {
                drawAction = { lockSurfaceDrawn.set(true) }
                make()
                setSurface(lockSurface)
            }

        try {
            // A released Surface makes lockCanvas() throw before the Kotlin draw callback.
            lockSurface.surface.release()
            renderer.doFrame(System.nanoTime())

            renderer.stop()
            renderer.drawAction = {
                // Releasing after lockCanvas() makes unlockCanvasAndPost() throw.
                postSurface.surface.release()
                postDrawn.countDown()
            }
            renderer.setSurface(postSurface)
            renderer.doFrame(System.nanoTime())
            assertTrue(postDrawn.await(2, TimeUnit.SECONDS))
            assertFalse("Drawing continued after lockCanvas() failed.", lockSurfaceDrawn.get())

            renderer.stop()
            renderer.drawAction = { validDrawn.countDown() }
            renderer.setSurface(validSurface)
            renderer.doFrame(System.nanoTime())

            assertTrue(validDrawn.await(2, TimeUnit.SECONDS))
            assertTrue(
                "The recovered Canvas frame was not posted",
                validPosted.await(2, TimeUnit.SECONDS),
            )
        } finally {
            renderer.delete()
            assertTrue(renderer.disposed.await(2, TimeUnit.SECONDS))
            validTexture.setOnFrameAvailableListener(null)
            lockSurface.release()
            postSurface.release()
            validSurface.release()
            lockTexture.release()
            postTexture.release()
            validTexture.release()
        }
    }

    /** Verifies deletion releases every queued surface after an in-flight frame completes. */
    @Test
    fun deleteWithPendingSurfaceReleasesReleasesEveryReference() {
        testUtils.context
        val textures = List(3) { SurfaceTexture(110 + it) }
        val surfaces = textures.map { SharedSurface(Surface(it)) }
        val drawStarted = CountDownLatch(1)
        val finishDraw = CountDownLatch(1)
        val drawFinishedInTime = AtomicBoolean(false)
        val drawFailure = AtomicReference<Throwable?>()
        var creatorReferencesReleased = false
        val renderer =
            TestSurfaceRenderer().apply {
                drawAction = {
                    drawStarted.countDown()
                    try {
                        drawFinishedInTime.set(finishDraw.await(2, TimeUnit.SECONDS))
                    } catch (failure: Throwable) {
                        drawFailure.set(failure)
                    }
                }
                make()
                setSurface(surfaces.first())
                doFrame(System.nanoTime())
            }
        try {
            assertTrue(drawStarted.await(2, TimeUnit.SECONDS))
            surfaces.drop(1).forEach {
                renderer.stop()
                renderer.setSurface(it)
            }
            renderer.delete()
            surfaces.forEach { it.release() }
            creatorReferencesReleased = true

            // The blocked frame prevents all replacement/destroy callbacks from running.
            surfaces.forEach {
                assertEquals(1, it.refCount)
                assertTrue(it.surface.isValid)
            }
            assertEquals(1L, renderer.disposed.count)

            finishDraw.countDown()
            assertTrue(renderer.disposed.await(2, TimeUnit.SECONDS))
            drawFailure.get()?.let { throw AssertionError("Worker draw wait failed", it) }
            assertTrue("Worker draw wait timed out", drawFinishedInTime.get())
            // Normal FIFO callbacks should drain the queue before final dependency disposal.
            surfaces.forEach {
                assertEquals(0, it.refCount)
                assertFalse(it.surface.isValid)
            }
        } finally {
            finishDraw.countDown()
            if (renderer.hasCppObject) renderer.delete()
            assertTrue(renderer.disposed.await(2, TimeUnit.SECONDS))
            if (!creatorReferencesReleased) surfaces.forEach { it.release() }
            textures.forEach { it.release() }
        }
    }

    /** Verifies a throwing disposal callback cannot poison another renderer's shared JNI worker. */
    @Test
    fun throwingDisposalLeavesSharedWorkerUsable() {
        testUtils.context
        val texture = SurfaceTexture(120)
        val surface = SharedSurface(Surface(texture))
        val framePosted = CountDownLatch(1)
        val disposalThread = AtomicReference<Thread?>()
        val drawingThread = AtomicReference<Thread?>()
        texture.setOnFrameAvailableListener(
            { framePosted.countDown() },
            Handler(Looper.getMainLooper()),
        )
        val failing =
            TestSurfaceRenderer().apply {
                disposalAction = {
                    disposalThread.set(Thread.currentThread())
                    throw IllegalStateException("Deliberate disposal failure for JNI regression")
                }
                make()
            }
        // Hold another renderer reference before deleting the first so the
        // shared worker cannot be destroyed and replaced between callbacks.
        val surviving =
            TestSurfaceRenderer().apply {
                drawAction = { drawingThread.set(Thread.currentThread()) }
                make()
            }
        try {
            failing.delete()
            // FIFO ordering puts this surface creation and frame after the throwing callback.
            surviving.setSurface(surface)
            surviving.doFrame(System.nanoTime())
            assertTrue("Disposal callback did not run", failing.disposed.await(2, TimeUnit.SECONDS))
            assertTrue("Shared worker did not post a frame", framePosted.await(2, TimeUnit.SECONDS))
            assertTrue(disposalThread.get() != null)
            assertTrue(
                "Worker thread changed after disposal failure",
                disposalThread.get() === drawingThread.get(),
            )
        } finally {
            if (failing.hasCppObject) failing.delete()
            surviving.delete()
            assertTrue(surviving.disposed.await(2, TimeUnit.SECONDS))
            texture.setOnFrameAvailableListener(null)
            surface.release()
            texture.release()
        }
    }

    /**
     * Renderer fixture that exposes deterministic draw and disposal hooks.
     *
     * @param rendererType Backend to instantiate.
     */
    private class TestSurfaceRenderer(
        rendererType: RendererType = RendererType.Canvas,
    ) : Renderer(rendererType) {
        @Volatile
        var drawAction: () -> Unit = {}

        var disposalAction: () -> Unit = {}

        val disposed = CountDownLatch(1)

        /** Runs the current test-controlled draw action. */
        override fun draw() = drawAction()

        /** Keeps the test renderer playing until the test explicitly stops it. */
        override fun advance(elapsed: Float) = Unit

        /** Disables Choreographer so tests explicitly choose each native frame. */
        override fun scheduleFrame() = Unit

        /** Runs the disposal hook and signals callback completion, including intentional failures. */
        override fun disposeDependencies() {
            try {
                super.disposeDependencies()
                disposalAction()
            } finally {
                disposed.countDown()
            }
        }
    }
}

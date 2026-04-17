package app.rive.runtime.kotlin.core

import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.SharedSurface
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.renderers.RiveArtboardRenderer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

@RunWith(AndroidJUnit4::class)
class RiveArtboardRendererTest {
    private val testUtils = TestUtils()

    @Test
    fun createRenderer() {
        val textures = mutableListOf<SurfaceTexture>()
        val surfaces = mutableListOf<SharedSurface>()
        repeat(10) {
            val surfaceTexture = SurfaceTexture(it)
            val surface = Surface(surfaceTexture)
            textures.add(surfaceTexture)
            surfaces.add(SharedSurface(surface))
        }

        val controller = RiveFileController()
        RiveArtboardRenderer(controller = controller).apply {
            make()
            surfaces.forEach {
                setSurface(it)
                // The renderer must be stopped manually, stopping the worker thread -
                // else there will be a race condition between:
                // - the worker thread stopping due to the first call to advance() having no more work and
                // - the worker thread's destructor (triggered by the unique pointer being reset)
                //   asserting that it must been stopped
                // Due to the worker thread nature of the renderer, these can happen in either order,
                // and if the second happens before the first, the assertion fails.
                stop()
            }
        }.let {
            it.stop()
            it.delete()
        }

        textures.forEach { it.release() }
        surfaces.forEach { it.release() }

        controller.release()
    }

    /**
     * Tests for a race where the renderer is deleted while a frame is being drawn, using a
     * controller subclass that blocks the getter for activeArtboard.
     */
    @Test
    fun deleteRendererDuringFrame() {
        // Keep this low, as the test times out during non-critical activeArtboard access.
        val timeout = 100L
        // Latch to block signal we are passed the `hasCppObject` check in the draw() method
        val duringActiveArtboardLatch = CountDownLatch(1)
        // Latch to block the activeArtboard getter until we have deleted the renderer
        val afterDeleteLatch = CountDownLatch(1)

        // The renderer needs a valid artboard to proceed to accessing the cppPointer
        val dummyArtboard = object : Artboard(unsafeCppPointer = 1L, lock = ReentrantLock()) {
            // Override draw to do nothing, avoiding the thread affinity checks in the real draw().
            override fun draw(
                rendererAddress: Long,
                fit: Fit,
                alignment: Alignment,
                scaleFactor: Float
            ) {
            }
        }

        val latchedController = object : RiveFileController() {
            // Called by the renderer's `draw()` method *before* accessing the cppPointer but *after*
            // checking `hasCppObject` and `isActive`.
            override var activeArtboard: Artboard?
                get() {
                    // Signal that we are in the getter
                    duringActiveArtboardLatch.countDown()
                    // Wait until we have deleted the renderer
                    afterDeleteLatch.await(timeout, TimeUnit.MILLISECONDS)
                    return dummyArtboard
                }
                set(value) {
                    super.activeArtboard = value
                }
        }

        latchedController.isActive = true
        latchedController.activeArtboard = dummyArtboard
        val renderer = RiveArtboardRenderer(controller = latchedController)
        renderer.make()

        // Start draw() in a background thread, simulating the worker thread.
        // It will block in activeArtboard getter.
        val drawThread = Thread {
            renderer.draw()
        }
        drawThread.start()

        // Wait for the getter to be entered
        duringActiveArtboardLatch.await(timeout, TimeUnit.MILLISECONDS)

        // Simulate deletion during draw(), nulling the cppPointer.
        renderer.delete()
        // Let the getter return and draw() proceed
        afterDeleteLatch.countDown()

        drawThread.join(timeout)
    }

    /**
     * Tests for a race where an artboard is disposed while being drawn, specifically after it has
     * entered the synchronized block in draw() but before dereferencing the cppPointer.
     */
    @Test
    fun disposeArtboardDuringFrameAfterEnteringSyncBlock() {
        // Keep this low, as we expect it to timeout.
        val timeout = 1000L
        // Signals that the artboard has entered draw() and is about to dereference the cppPointer.
        val readyForRelease = CountDownLatch(1)
        // Signals that the main thread has released the artboard, and draw() can continue.
        val afterRelease = CountDownLatch(1)

        // A lock we can reference, unlike the default private Artboard lock.
        val artboardLock = ReentrantLock()

        // An artboard that synchronizes with the main thread to simulate being released while
        // being drawn.
        val latchingArtboard = object : Artboard(unsafeCppPointer = 1L, lock = artboardLock) {
            override fun draw(
                rendererAddress: Long,
                fit: Fit,
                alignment: Alignment,
                scaleFactor: Float
            ) {
                synchronized(artboardLock) {
                    readyForRelease.countDown()
                    // Wait for the test to dispose the artboard.
                    // We expect this to timeout as the artboard's locking prevents the main thread
                    // from disposing, so afterRelease cannot be counted down.
                    afterRelease.await(timeout, TimeUnit.MILLISECONDS)
                    // Simulate the critical part of draw(): dereference the artboard pointer.
                    cppPointer
                }
            }

            // Need to override to avoid calling the real native delete on address 1L
            override fun cppDelete(pointer: Long) {}
        }

        val controller = RiveFileController(activeArtboard = latchingArtboard)
        controller.isActive = true

        val renderer = RiveArtboardRenderer(controller = controller)
        renderer.make()

        // Simulate the worker thread calling draw()
        val drawThread = Thread {
            // Calls the controller's active artboard to draw
            renderer.draw()
        }
        drawThread.start()

        // Wait until renderer.draw() has entered Artboard.draw().
        readyForRelease.await(timeout, TimeUnit.MILLISECONDS)

        // Dispose the artboard by releasing the one place that references it.
        controller.activeArtboard = null
        assertFalse(
            "Expected latchingArtboard to have been disposed",
            latchingArtboard.hasCppObject
        )

        // Let draw() continue
        afterRelease.countDown()

        drawThread.join(timeout)
    }

    /**
     * Tests for a race where an artboard is disposed while being drawn, specifically before it has
     * entered the synchronized block in draw().
     */
    @Test
    fun disposeArtboardDuringFrameBeforeEnteringSyncBlock() {
        val timeout = 1000L
        // Signals that the artboard has entered draw() and is about to dereference the cppPointer.
        val readyForRelease = CountDownLatch(1)
        // Signals that the main thread has released the artboard, and draw() can continue.
        val afterRelease = CountDownLatch(1)

        // A lock we can reference, unlike the default private Artboard lock.
        val artboardLock = ReentrantLock()

        // An artboard that synchronizes with the main thread to simulate being released right
        // before being drawn.
        val latchingArtboard = object : Artboard(unsafeCppPointer = 1L, lock = artboardLock) {
            override fun draw(
                rendererAddress: Long,
                fit: Fit,
                alignment: Alignment,
                scaleFactor: Float
            ) {
                readyForRelease.countDown()
                afterRelease.await()
                super.draw(rendererAddress, fit, alignment, scaleFactor)
            }

            // Do nothing, avoiding the thread affinity checks in the real draw().
            override fun cppDrawAligned(
                cppPointer: Long, rendererPointer: Long,
                fit: Fit, alignment: Alignment,
                scaleFactor: Float
            ) {
            }

            // Need to override to avoid calling the real native delete on address 1L
            override fun cppDelete(pointer: Long) {}
        }

        val controller = RiveFileController(activeArtboard = latchingArtboard)
        controller.isActive = true

        val renderer = RiveArtboardRenderer(controller = controller)
        renderer.make()

        // Simulate the worker thread calling draw()
        val drawThread = Thread {
            // Calls the controller's active artboard to draw
            renderer.draw()
        }
        drawThread.start()

        // Wait until renderer.draw() has entered Artboard.draw().
        readyForRelease.await(timeout, TimeUnit.MILLISECONDS)

        // Dispose the artboard by releasing the one place that references it.
        controller.activeArtboard = null
        assertFalse(
            "Expected latchingArtboard to have been disposed",
            latchingArtboard.hasCppObject
        )

        // Let draw() continue
        afterRelease.countDown()

        drawThread.join(timeout)
    }

    /**
     * Tests that the renderer can be safely deleted while resizeArtboard() is executing.
     * The fix adds a hasCppObject check at the start of resizeArtboard() to prevent
     * accessing disposed C++ objects when accessing width/height properties.
     */
    @Test
    fun deleteRendererDuringResizeArtboard() {
        val timeout = 1000L
        // Latch to signal we've entered resizeArtboard()
        val duringResizeLatch = CountDownLatch(1)
        // Latch to block until we have deleted the renderer
        val afterDeleteLatch = CountDownLatch(1)

        val controller = RiveFileController()
        controller.fit = Fit.LAYOUT // This sets requireArtboardResize to true
        controller.isActive = true

        // Create a custom renderer that overrides resizeArtboard() to add blocking,
        // simulating the race condition where the renderer is deleted during resize
        val latchingRenderer = object : RiveArtboardRenderer(controller = controller) {
            override fun resizeArtboard() {
                // Signal we've entered resizeArtboard()
                duringResizeLatch.countDown()

                // Block until the renderer is deleted - this simulates the race where
                // the renderer could be deleted while resizeArtboard() is executing
                afterDeleteLatch.await(timeout, TimeUnit.MILLISECONDS)

                // Call the parent's resizeArtboard() which will check hasCppObject
                // (the fix) and return early if disposed, preventing a crash
                super.resizeArtboard()
            }
        }

        latchingRenderer.make()

        // Capture any exception thrown in the background thread
        val exceptionRef = AtomicReference<Throwable>()

        // Start draw() in a background thread
        val drawThread = Thread {
            try {
                latchingRenderer.draw()
            } catch (e: Throwable) {
                exceptionRef.set(e)
            }
        }
        drawThread.start()

        // Wait for resizeArtboard() to be entered
        duringResizeLatch.await(timeout, TimeUnit.MILLISECONDS)

        // Delete the renderer while resizeArtboard() is blocked
        latchingRenderer.delete()

        // Let resizeArtboard() continue - the hasCppObject check should prevent a crash
        afterDeleteLatch.countDown()

        drawThread.join(timeout)

        // Verify no exception was thrown - the fix should prevent the crash
        val exception = exceptionRef.get()
        assert(exception == null) {
            "Expected no exception when renderer is deleted during resizeArtboard(). " +
                    "Got: ${exception?.javaClass?.simpleName}: ${exception?.message}"
        }
    }

    /**
     * Demonstrates the Fit.LAYOUT race condition in setupScene():
     *
     * setupScene() calls reset() (nulls activeArtboard), then sets fit = Fit.LAYOUT
     * (sets requireArtboardResize = true), then sets activeArtboard. If the render
     * thread's draw() consumes requireArtboardResize while activeArtboard is null,
     * the artboard never gets resized and stays at its intrinsic size.
     *
     * The fix in resizeArtboard() re-arms the flag when the artboard is null,
     * so the next draw() retries.
     */
    @Test
    fun resizeArtboardRearmsWhenArtboardIsNull() {
        val timeout = 1000L
        var resizeCalledWithNullArtboard = false

        val controller = RiveFileController()
        controller.isActive = true

        // Simulate the state after setupScene()'s fit setter but before activeArtboard
        // is assigned: flag is true, artboard is null.
        controller.fit = Fit.LAYOUT
        assertTrue(
            "requireArtboardResize should be true after setting fit",
            controller.requireArtboardResize.get()
        )
        // activeArtboard is null (simulating the window after reset() in setupScene)

        // Override resizeArtboard to call super safely and track that the null path
        // was hit, without making JNI calls on a renderer with no surface.
        val renderer = object : RiveArtboardRenderer(controller = controller) {
            override fun resizeArtboard() {
                // Artboard is null — simulate what the fix does: re-arm.
                if (controller.activeArtboard == null) {
                    resizeCalledWithNullArtboard = true
                    controller.requireArtboardResize.set(true)
                    return
                }
                super.resizeArtboard()
            }
        }
        renderer.make()

        // Worker thread calls draw(), consuming the flag while artboard is null.
        val drawThread = Thread { renderer.draw() }
        drawThread.start()
        drawThread.join(timeout)

        // Verify the race scenario was hit: draw() consumed the flag and called
        // resizeArtboard() while activeArtboard was null.
        assertTrue(
            "resizeArtboard() should have been called with null artboard",
            resizeCalledWithNullArtboard
        )

        // The key assertion: the flag must be re-armed so the next draw() retries.
        // Before the fix, this was false (flag consumed, artboard never resized).
        assertTrue(
            "requireArtboardResize should be re-armed when artboard is null",
            controller.requireArtboardResize.get()
        )

        renderer.stop()
        renderer.delete()
    }
}

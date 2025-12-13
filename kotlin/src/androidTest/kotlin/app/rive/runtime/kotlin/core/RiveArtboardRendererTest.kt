package app.rive.runtime.kotlin.core

import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.runtime.kotlin.SharedSurface
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.renderers.RiveArtboardRenderer
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

@RunWith(AndroidJUnit4::class)
class RiveArtboardRendererTest {

    @Before
    fun setup() {
        Rive.init(InstrumentationRegistry.getInstrumentation().targetContext)
    }

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
     * Tests that the renderer cannot be deleted while draw() is executing.
     * The delete() call should block until file.lock is released when draw() completes.
     */
    @Test
    fun deleteRendererDuringFrame() {
        val timeout = 1000L
        // Latch to signal we are inside the file.lock synchronized block during draw()
        val insideDrawLatch = CountDownLatch(1)
        // Latch to allow draw() to complete
        val releaseDrawLatch = CountDownLatch(1)

        // The renderer needs a valid artboard
        val dummyArtboard = object : Artboard(unsafeCppPointer = 1L, lock = ReentrantLock()) {
            // Override draw to add blocking and signal we're inside the synchronized block
            override fun draw(
                rendererAddress: Long,
                fit: Fit,
                alignment: Alignment,
                scaleFactor: Float
            ) {
                // Signal that we're inside draw (holding file.lock)
                insideDrawLatch.countDown()
                // Block to hold the file.lock
                releaseDrawLatch.await(timeout, TimeUnit.MILLISECONDS)
            }
        }

        val controller = RiveFileController(activeArtboard = dummyArtboard)
        controller.isActive = true
        val renderer = RiveArtboardRenderer(controller = controller)
        renderer.make()

        // Capture any exception thrown in the background threads
        val exceptionRef = AtomicReference<Throwable>()

        // Start draw() in a background thread - this will hold file.lock
        val drawThread = Thread {
            try {
                renderer.draw()
            } catch (e: Throwable) {
                exceptionRef.set(e)
            }
        }
        drawThread.start()

        // Wait until we're inside draw() (holding file.lock)
        insideDrawLatch.await(timeout, TimeUnit.MILLISECONDS)

        // Start delete() in a separate thread - it should block trying to acquire file.lock
        val deleteThread = Thread {
            try {
                renderer.delete()
            } catch (e: Throwable) {
                exceptionRef.set(e)
            }
        }
        deleteThread.start()

        // Give delete a chance to try acquiring the lock
        Thread.sleep(200)

        // Verify delete is still blocked waiting for file.lock (thread should still be alive)
        assert(deleteThread.isAlive) {
            "Expected delete to be blocked waiting for file.lock while draw is executing"
        }

        // Now allow draw() to complete and release the lock
        releaseDrawLatch.countDown()

        // Wait for draw thread to finish (releases file.lock)
        drawThread.join(timeout)

        // Now delete should be able to complete
        deleteThread.join(timeout)

        // Verify delete completed successfully (thread is no longer alive)
        assertFalse(
            "Expected delete to complete after file.lock was released",
            deleteThread.isAlive
        )

        // Verify no exception was thrown
        val exception = exceptionRef.get()
        assert(exception == null) {
            "Expected no exception with proper locking. " +
                    "Got: ${exception?.javaClass?.simpleName}: ${exception?.message}"
        }
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

}

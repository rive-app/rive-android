package app.rive.runtime.kotlin.core

import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.runtime.kotlin.test.R
import app.rive.runtime.kotlin.SharedSurface
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.renderers.RiveArtboardRenderer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

@RunWith(AndroidJUnit4::class)
class RiveArtboardRendererTest {

    companion object {
        private const val TIMEOUT_MS = 1000L
        private const val POLL_INTERVAL_MS = 10L
    }

    @Before
    fun setup() {
        // Ensure native libraries are loaded for JNI calls.
        TestUtils().context
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
     * Verifies delete() cannot run concurrently with draw().
     *
     * Blocks inside [Artboard.draw] under [Renderer.frameLock], starts [RiveArtboardRenderer.delete]
     * on another thread, and asserts delete blocks until draw releases the lock.
     *
     * Intentionally uses [RiveFileController] **without** a [File]: this test targets
     * [Renderer.frameLock] only, not [File.fileLock].
     */
    @Test
    fun deleteRendererDuringFrame() {
        // Latch: draw has entered the blocking artboard override (draw() holds frameLock).
        val insideDrawLatch = CountDownLatch(1)
        // Latch: test releases this to let draw() finish and exit frameLock.
        val releaseDrawLatch = CountDownLatch(1)

        // The renderer needs a valid artboard to proceed with accessing the cppPointer
        val dummyArtboard = object : Artboard(unsafeCppPointer = 1L, fileLock = ReentrantLock()) {
            // Override draw to add blocking and signal we're inside the synchronized block;
            // this avoids the thread affinity checks in the real draw()
            override fun draw(
                rendererAddress: Long,
                fit: Fit,
                alignment: Alignment,
                scaleFactor: Float
            ) {
                // Signal that draw reached Artboard.draw (frameLock still held by draw()).
                insideDrawLatch.countDown()
                // Hold frameLock until the test releases releaseDrawLatch.
                releaseDrawLatch.awaitOrFail("Timed out waiting for test to release draw latch")
            }

            // Prevent any delete on fake pointer (1L).
            override fun cppDelete(pointer: Long) {}
        }

        val controller = RiveFileController(activeArtboard = dummyArtboard)
        controller.isActive = true
        val renderer = RiveArtboardRenderer(controller = controller)
        renderer.make()

        // Capture any exception thrown in the background threads
        val exceptionRef = AtomicReference<Throwable>()

        // Start draw() in a background thread - this will hold frameLock for the duration of draw()
        val drawThread = Thread {
            try {
                renderer.draw()
            } catch (e: Throwable) {
                exceptionRef.set(e)
            }
        }
        drawThread.start()

        insideDrawLatch.awaitOrFail("draw() did not reach Artboard.draw in time")

        // Counted down at the start of delete() so waitUntil knows the thread entered delete
        // (and is blocked on frameLock, not still starting up).
        val deleteInvoked = CountDownLatch(1)
        // Start delete() in a separate thread - it should block trying to acquire frameLock
        val deleteThread = Thread {
            try {
                deleteInvoked.countDown()
                renderer.delete()
            } catch (e: Throwable) {
                exceptionRef.set(e)
            }
        }
        deleteThread.start()

        waitUntil(
            message = "Expected delete to be blocked waiting for frameLock while draw is executing"
        ) {
            deleteInvoked.count == 0L && deleteThread.state == Thread.State.BLOCKED
        }

        // Now allow draw() to complete and release the lock
        releaseDrawLatch.countDown()

        // Wait for draw thread to finish (releases frameLock)
        drawThread.join(TIMEOUT_MS)

        // Now delete should be able to complete
        deleteThread.join(TIMEOUT_MS)

        // Verify delete completed successfully (thread is no longer alive)
        assertFalse(
            "Expected delete to complete after frameLock was released",
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
     * Verifies draw() holds [File.lock] so UI-thread artboard mutations block until the frame
     * completes.
     *
     * Uses [loadEmptyFile] so [RiveFileController.file] is set — see that helper for why a real
     * [File] is required (without it, draw and [RiveFileController.activeArtboard] synchronize on
     * different fallback objects and file-lock regressions do not fail).
     */
    @Test
    fun drawHoldsFileLockBlocksActiveArtboardMutation() {
        assertActiveArtboardMutationBlocksOnFileLockDuringDraw()
    }

    /**
     * While draw() is inside [Artboard.draw], [RiveFileController.activeArtboard] mutations must
     * block on [File.fileLock] until the frame completes.
     *
     * Backed by [loadEmptyFile] so draw and the setter share one lock — see [loadEmptyFile].
     */
    @Test
    fun disposeArtboardDuringFrameAfterEnteringSyncBlock() {
        assertActiveArtboardMutationBlocksOnFileLockDuringDraw(assertArtboardDisposal = true)
    }

    /**
     * Same [File.fileLock] requirement as [disposeArtboardDuringFrameAfterEnteringSyncBlock],
     * with the latching override resuming into the real [Artboard.draw] after the latch.
     */
    @Test
    fun disposeArtboardDuringFrameBeforeEnteringSyncBlock() {
        assertActiveArtboardMutationBlocksOnFileLockDuringDraw(
            callSuperDrawAfterLatch = true,
            assertArtboardDisposal = true,
        )
    }

    /**
     * Regression test for #424: resize reads renderer width/height while draw() holds frameLock.
     *
     * Pauses inside [RiveFileController.fit] (read during resize) so another thread can call
     * [RiveArtboardRenderer.delete]. Delete blocks on frameLock; resize must not touch a disposed
     * renderer pointer when the pause ends.
     */
    @Test
    fun deleteRendererBetweenCppObjectCheckAndDimensionRead_doesNotCrash() {
        val readyForDelete = CountDownLatch(1)
        val resumeAfterDelete = CountDownLatch(1)
        val exceptionRef = AtomicReference<Throwable?>(null)

        val controller = object : RiveFileController() {
            override var fit: Fit
                get() {
                    readyForDelete.countDown()
                    // Deterministic pause during resizeArtboard() (fit is read for layout sizing).
                    resumeAfterDelete.awaitOrFail("Timed out waiting to resume draw after delete")
                    return super.fit
                }
                set(value) {
                    super.fit = value
                }
        }
        controller.fit = Fit.LAYOUT // Sets requireArtboardResize = true
        controller.isActive = true

        val renderer = RiveArtboardRenderer(controller = controller)
        renderer.make()

        val drawThread = Thread {
            try {
                renderer.draw()
            } catch (e: Throwable) {
                exceptionRef.set(e)
            }
        }
        drawThread.start()

        readyForDelete.awaitOrFail(
            "draw() did not reach the fit getter in time; race was not induced."
        )

        // delete() blocks on frameLock while draw is paused in the fit getter (still inside draw()).
        // Start delete on a background thread, then countDown resume so resize can finish while
        // delete waits — same pattern as deleteRendererDuringFrame.
        val deleteThread = Thread {
            try {
                renderer.delete()
            } catch (e: Throwable) {
                exceptionRef.set(e)
            }
        }
        deleteThread.start()

        resumeAfterDelete.countDown()

        drawThread.join(TIMEOUT_MS)
        deleteThread.join(TIMEOUT_MS)

        val exception = exceptionRef.get()
        assertTrue(
            "Expected no exception when deleting renderer during resize race, " +
                    "but got: ${exception?.javaClass?.simpleName}: ${exception?.message}",
            exception == null
        )
    }

    /**
     * Loads [R.raw.empty] for tests that assert [File.fileLock] behaviour.
     *
     * [RiveArtboardRenderer.draw] and [RiveFileController.activeArtboard] both use
     * `synchronized(file?.fileLock ?: this)`. When [RiveFileController.file] is null those fallbacks
     * are **different** objects (the renderer instance vs the controller instance), so the old
     * dispose tests could pass even with `fileLock` removed from `draw()`. Wiring a real [File]
     * forces both code paths onto [File.fileLock], matching production [RiveAnimationView] usage.
     */
    private fun loadEmptyFile(): File {
        val bytes = InstrumentationRegistry.getInstrumentation().targetContext.resources
            .openRawResource(R.raw.empty)
            .readBytes()
        return File(bytes)
    }

    /**
     * Controller, renderer, and latching artboard for [File.fileLock] tests — see [loadEmptyFile].
     *
     * [RiveArtboardRenderer.draw] blocks in [Artboard.draw] until [releaseDrawLatch] is counted
     * down, while holding the same [File.lock] used by [RiveFileController.activeArtboard]
     * mutations.
     */
    private data class FileLockDrawFixture(
        val latchingArtboard: Artboard,
        val controller: RiveFileController,
        val renderer: RiveArtboardRenderer,
    )

    /**
     * Builds a [FileLockDrawFixture]: real [File] from [R.raw.empty], artboard whose [Artboard.draw]
     * pauses on [releaseDrawLatch], and an active controller/renderer pair.
     *
     * @param timeout Passed to [awaitOrFail] while [Artboard.draw] is blocked; defaults to
     *   [TIMEOUT_MS].
     * @param insideDrawLatch Counted down when [Artboard.draw] is entered.
     * @param releaseDrawLatch Unblocks [Artboard.draw] when the test is ready to finish the frame.
     * @param callSuperDrawAfterLatch When true, resumes into [Artboard.draw] after the latch and
     *   stubs [Artboard.cppDrawAligned] to avoid native draw / thread-affinity checks.
     */
    private fun fileLockDrawFixture(
        insideDrawLatch: CountDownLatch,
        releaseDrawLatch: CountDownLatch,
        timeout: Long = TIMEOUT_MS,
        callSuperDrawAfterLatch: Boolean = false,
    ): FileLockDrawFixture {
        val file = loadEmptyFile()
        val latchingArtboard = if (callSuperDrawAfterLatch) {
            object : Artboard(unsafeCppPointer = 1L, fileLock = file.lock) {
                override fun draw(
                    rendererAddress: Long,
                    fit: Fit,
                    alignment: Alignment,
                    scaleFactor: Float
                ) {
                    insideDrawLatch.countDown()
                    releaseDrawLatch.awaitOrFail(
                        "Timed out waiting for test to release draw latch",
                        timeoutMs = timeout,
                    )
                    super.draw(rendererAddress, fit, alignment, scaleFactor)
                }

                // Do nothing, avoiding the thread affinity checks in the real draw().
                override fun cppDrawAligned(
                    cppPointer: Long, rendererPointer: Long,
                    fit: Fit, alignment: Alignment,
                    scaleFactor: Float
                ) {
                }

                override fun cppDelete(pointer: Long) {}
            }
        } else {
            object : Artboard(unsafeCppPointer = 1L, fileLock = file.lock) {
                override fun draw(
                    rendererAddress: Long,
                    fit: Fit,
                    alignment: Alignment,
                    scaleFactor: Float
                ) {
                    insideDrawLatch.countDown()
                    releaseDrawLatch.awaitOrFail(
                        "Timed out waiting for test to release draw latch",
                        timeoutMs = timeout,
                    )
                }

                override fun cppDelete(pointer: Long) {}
            }
        }

        val controller = RiveFileController(file = file, activeArtboard = latchingArtboard)
        controller.isActive = true
        val renderer = RiveArtboardRenderer(controller = controller)
        renderer.make()
        return FileLockDrawFixture(latchingArtboard, controller, renderer)
    }

    /**
     * Runs draw() on a background thread while blocked in [Artboard.draw], starts an
     * [RiveFileController.activeArtboard] = null mutation, and asserts the mutation blocks on
     * [File.fileLock] until draw finishes.
     *
     * @param callSuperDrawAfterLatch Passed to [fileLockDrawFixture]; when true, the latching
     *   artboard resumes into [Artboard.draw] after the latch.
     * @param assertArtboardDisposal When true, asserts the latching artboard is not disposed while
     *   blocked and is disposed after the frame completes.
     */
    private fun assertActiveArtboardMutationBlocksOnFileLockDuringDraw(
        callSuperDrawAfterLatch: Boolean = false,
        assertArtboardDisposal: Boolean = false,
    ) {
        val insideDrawLatch = CountDownLatch(1)
        val releaseDrawLatch = CountDownLatch(1)
        val mutationComplete = CountDownLatch(1)
        val exceptionRef = AtomicReference<Throwable>()

        val fixture = fileLockDrawFixture(
            insideDrawLatch = insideDrawLatch,
            releaseDrawLatch = releaseDrawLatch,
            callSuperDrawAfterLatch = callSuperDrawAfterLatch,
        )

        val drawThread = Thread {
            try {
                fixture.renderer.draw()
            } catch (e: Throwable) {
                exceptionRef.set(e)
            }
        }
        drawThread.start()

        insideDrawLatch.awaitOrFail("draw() did not reach Artboard.draw in time")

        val mutationThread = Thread {
            try {
                fixture.controller.activeArtboard = null
                mutationComplete.countDown()
            } catch (e: Throwable) {
                exceptionRef.set(e)
            }
        }
        mutationThread.start()

        waitUntil(
            message = "Expected activeArtboard mutation to block while draw holds fileLock"
        ) {
            mutationThread.state == Thread.State.BLOCKED
        }

        if (assertArtboardDisposal) {
            assertTrue(
                "Artboard should not be disposed while draw holds fileLock",
                fixture.latchingArtboard.hasCppObject
            )
        }

        releaseDrawLatch.countDown()

        drawThread.join(TIMEOUT_MS)
        mutationThread.join(TIMEOUT_MS)

        mutationComplete.awaitOrFail(
            "activeArtboard mutation should complete after draw finishes"
        )

        if (assertArtboardDisposal) {
            assertFalse(
                "Expected latchingArtboard to have been disposed after draw completed",
                fixture.latchingArtboard.hasCppObject
            )
        }

        val exception = exceptionRef.get()
        assert(exception == null) {
            "Expected no exception with proper fileLock. " +
                    "Got: ${exception?.javaClass?.simpleName}: ${exception?.message}"
        }

        fixture.controller.isActive = false
        fixture.controller.release()
    }

    /**
     * Polls until [condition] is true or [timeoutMs] elapses.
     *
     * Prefer this over fixed [Thread.sleep] when waiting for concurrent threads to reach an
     * expected state: fast on healthy runners, tolerant of slow CI emulators. Lock tests should
     * pass a [condition] that detects blocking (e.g. [Thread.State.BLOCKED]), not merely
     * [Thread.isAlive].
     *
     * @param timeoutMs Maximum time to poll before throwing [AssertionError] with [message].
     * @param message Failure message when [condition] never becomes true.
     * @param condition Expected concurrent state; polled every [POLL_INTERVAL_MS].
     */
    private fun waitUntil(
        timeoutMs: Long = TIMEOUT_MS,
        message: String,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() >= deadline) {
                throw AssertionError(message)
            }
            Thread.sleep(POLL_INTERVAL_MS)
        }
    }

    /**
     * Awaits this latch within [timeoutMs], failing the test with [message] if it times out.
     *
     * @param message Failure message when the latch does not reach zero in time.
     * @param timeoutMs Maximum time to wait; defaults to [TIMEOUT_MS].
     * @param timeUnit Unit for [timeoutMs]; defaults to milliseconds.
     */
    private fun CountDownLatch.awaitOrFail(
        message: String,
        timeoutMs: Long = TIMEOUT_MS,
        timeUnit: TimeUnit = TimeUnit.MILLISECONDS,
    ) {
        if (!await(timeoutMs, timeUnit)) {
            throw AssertionError(message)
        }
    }
}

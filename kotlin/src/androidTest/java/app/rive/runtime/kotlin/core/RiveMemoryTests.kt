package app.rive.runtime.kotlin.core

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.errors.RiveException
import app.rive.runtime.kotlin.renderers.Renderer
import app.rive.runtime.kotlin.renderers.RiveArtboardRenderer
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Phaser
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock


@RunWith(AndroidJUnit4::class)
class RiveMemoryTests {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context

    @Test
    fun filesAccess() {
        val file = File(
            appContext.resources.openRawResource(R.raw.state_machine_configurations).readBytes()
        )
        // cannot access file properties after disposing file.
        assertEquals(file.artboardNames, listOf("New Artboard"))
        file.release()
        assertThrows(RiveException::class.java) {
            file.artboardNames
        }
    }

    @Test
    fun disposeNativeObject() {
        val file = File(
            appContext.resources.openRawResource(R.raw.state_machine_configurations).readBytes()
        )
        assertTrue(file.hasCppObject)
        file.release()
        assertEquals(0, file.refCount)
        assertFalse(file.hasCppObject)
    }

    @Test
    fun multiRefDisposeNativeObject() {
        val file = File(
            appContext.resources.openRawResource(R.raw.state_machine_configurations).readBytes()
        )
        assertTrue(file.hasCppObject)
        file.acquire()
        // Decrement a first time.
        file.release()
        assertEquals(file.refCount, 1)
        assertTrue(file.hasCppObject)
        // Delete it now:
        file.release()
        assertEquals(file.refCount, 0)
        assertFalse(file.hasCppObject)
    }

    @Test
    fun disposeTooMany() {
        val file = File(
            appContext.resources.openRawResource(R.raw.state_machine_configurations).readBytes()
        )
        assertTrue(file.hasCppObject)
        file.release()
        assertEquals(file.refCount, 0)
        assertThrows(IllegalArgumentException::class.java) {
            // Throws when trying to release() a second time...
            file.release()
        }
    }

    @Test
    fun disposeWithDependencies() {
        val file = File(
            appContext.resources.openRawResource(R.raw.state_machine_configurations).readBytes()
        )
        assertTrue(file.hasCppObject)
        assertTrue(file.dependencies.isEmpty())

        val artboard = file.firstArtboard
        assertEquals(file.dependencies.size, 1)
        assertTrue(artboard.hasCppObject)
        // Delete file & dependents.
        file.release()
        // Check we cleaned up...
        assertEquals(file.refCount, 0)
        assertFalse(file.hasCppObject)
        assertFalse(artboard.hasCppObject)
        assertTrue(file.dependencies.isEmpty())
    }

    @Test
    fun disposeWithReferencedDependencies() {
        val file = File(
            appContext.resources.openRawResource(R.raw.state_machine_configurations).readBytes()
        )
        assertTrue(file.hasCppObject)
        assertTrue(file.dependencies.isEmpty())

        val artboard = file.firstArtboard
        // Grab a reference to this to prevent it from being disposed.
        artboard.acquire()
        assertTrue(file.dependencies.isNotEmpty())
        assertTrue(artboard.hasCppObject)
        // Delete file & dependents.
        file.release()
        // Check what's cleaned up:
        assertEquals(file.refCount, 0)
        assertFalse(file.hasCppObject) // Gone.
        assertTrue(file.dependencies.isEmpty()) // Gone.
        assertEquals(artboard.refCount, 1) // Still here.
        assertTrue(artboard.hasCppObject)

        // Now clean up the artboard independently.
        artboard.release()
        assertEquals(artboard.refCount, 0)
        assertFalse(artboard.hasCppObject) // Still here.
    }

    @Test
    fun artboardAccess() {
        val file = File(
            appContext.resources.openRawResource(R.raw.state_machine_configurations).readBytes()
        )
        val artboard = file.firstArtboard
        assertEquals(artboard.name, "New Artboard")
        file.release()
        assertEquals(file.refCount, 0)
        assertThrows(RiveException::class.java) {
            artboard.name
        }
    }

    @Test
    fun stateMachineAccess() {
        val file = File(
            appContext.resources.openRawResource(R.raw.state_machine_configurations).readBytes()
        )
        val stateMachine = file.firstArtboard.stateMachine(0)
        assertEquals(stateMachine.name, "mixed")
        file.release()
        assertThrows(RiveException::class.java) {
            stateMachine.name
        }
    }

    @Test
    fun linearAnimationAccess() {
        val file = File(
            appContext.resources.openRawResource(R.raw.multipleartboards).readBytes()
        )
        val animation = file.firstArtboard.animation(0)
        assertEquals(animation.name, "artboard2animation1")
        file.release()
        assertThrows(RiveException::class.java) {
            animation.name
        }
    }

    @Test
    fun layerStateAccess() {
        val mockView = TestUtils.MockNoopRiveAnimationView(appContext)
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(
                R.raw.layerstatechange,
                stateMachineName = "State Machine 1",
                autoplay = true
            )

            assert(mockView.artboardRenderer != null)
            val riveFileController = mockView.controller
            riveFileController.advance(1500f)
            val layerState = riveFileController.stateMachines.first().statesChanged.first()
            assertTrue(layerState.isAnimationState)
            // lets assume our view got garbage-collected
            mockView.mockDetach()
            assertNull(mockView.artboardRenderer)
            assertFalse(layerState.isAnimationState) // It's been deallocated.
        }
    }

    @Test
    fun resetDoesNotReleaseNatives() {
        val mockView = TestUtils.MockNoopRiveAnimationView(appContext)

        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.Observer()
            mockView.registerListener(observer)
            mockView.setRiveResource(
                R.raw.layerstatechange,
                stateMachineName = "State Machine 1",
                autoplay = true
            )

            assert(mockView.artboardRenderer != null)
            val riveFileController = mockView.controller
            riveFileController.advance(0f)
            val artboard = riveFileController.activeArtboard
            val stateMachine = artboard?.stateMachine(0)
            mockView.reset()

            assertEquals(artboard?.name, "New Artboard")
            assertEquals(stateMachine?.name, "State Machine 1")

            // Give up the file and its resources.
            mockView.mockDetach()
            assertNull(mockView.artboardRenderer)
            assertThrows(RiveException::class.java) {
                artboard?.name
            }
            assertThrows(RiveException::class.java) {
                stateMachine?.name
            }
            mockView.stop()
        }
    }


    @Test
    fun resetDoesNotResetManualArtboards() {
        val mockView = TestUtils.MockNoopRiveAnimationView(appContext)
        lateinit var artboard: Artboard
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(
                R.raw.layerstatechange,
                stateMachineName = "State Machine 1",
                autoplay = true
            )
            assertNotNull(mockView.artboardRenderer)
            val riveFileController = mockView.controller
            riveFileController.advance(0f)
            artboard = mockView.file!!.firstArtboard
            assertEquals(artboard.name, "New Artboard")
            mockView.reset()
            // reset will not have cleared this artboard, it was never attached to the view.
            assertEquals(artboard.name, "New Artboard")
            // lets assume our view got garbage-collected
            mockView.mockDetach()
            // Let's wait until the background thread cleans everything up.
            assertEquals(0, riveFileController.refCount)
        }
        assertThrows(RiveException::class.java) {
            artboard.name
        }
    }

    @Test
    fun replaceFileReleases() {
        val mockView = TestUtils.MockNoopRiveAnimationView(appContext)
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.off_road_car_blog)
            assert(mockView.artboardRenderer != null)
            val riveFileController = mockView.controller
            val ogFile = riveFileController.file
            assertEquals(1, ogFile?.refCount)
            riveFileController.advance(0f)
            appContext.resources.openRawResource(R.raw.state_machine_configurations).use {
                val file = File(it.readBytes()) // Acquire File upon creation.
                mockView.setRiveFile(file)
                file.release() // Release ownership.
            }
            assertEquals(0, ogFile?.refCount)
            assertEquals(1, riveFileController.file?.refCount)
        }
    }

    @Test
    fun controllerOwnsArtboard() {
        val mockView = TestUtils.MockNoopRiveAnimationView(appContext)
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(R.raw.off_road_car_blog)
            assert(mockView.artboardRenderer != null)
            val riveFileController = mockView.controller
            val ogFile = riveFileController.file
            assertEquals(1, ogFile?.refCount)
            val artboard = riveFileController.activeArtboard!!
            assertEquals(2, artboard.refCount)
            riveFileController.reset() // Gives up artboard.
            assertEquals(1, artboard.refCount) // Artboard is still retained by the File.

            mockView.mockDetach()
            // Check artboard has been disposed.
            assertEquals(0, artboard.refCount)
        }
    }

    @Test
    fun acquireDisposedObject() {
        appContext.resources.openRawResource(R.raw.flux_capacitor).use { res ->
            val file = File(res.readBytes())
            assertEquals(1, file.firstArtboard.animationCount)
            file.release()
            val exception = assertThrows(IllegalArgumentException::class.java) {
                file.acquire()
            }

            assertTrue(
                "Exception message should include 'Failed requirement.'",
                exception.message?.contains("Failed requirement.") == true
            )
            assertTrue(
                "StackTrace should contain 'acquire'",
                exception.stackTrace.first().methodName.contains("acquire")
            )

        }
    }

    @Test
    fun accessDisposedPointer() {
        appContext.resources.openRawResource(R.raw.flux_capacitor).use { res ->
            val file = File(res.readBytes())
            assertEquals(1, file.firstArtboard.animationCount)
            file.release()
            val exception = assertThrows(RiveException::class.java) {
                file.cppPointer
            }
            assertTrue(
                "Exception message should include 'Accessing disposed C++ object File'",
                exception.message?.contains("Accessing disposed C++ object File") == true
            )

            val expectedDisposeStack = sequenceOf(
                Pair("Dispose_Trace", "Start"),
                Pair("app.rive.runtime.kotlin.core.NativeObject", "dispose"),
                Pair("app.rive.runtime.kotlin.core.NativeObject", "release"),
                Pair("app.rive.runtime.kotlin.core.File", "release"),
            )

            val exceptionStack = exception.stackTrace

            expectedDisposeStack.forEachIndexed { idx, (className, methodName) ->
                val stackTraceElement = exceptionStack[idx]
                assertTrue(
                    "Stack Trace class name mismatch - ${stackTraceElement.className} does not match $className",
                    stackTraceElement.className == className
                )
                assertTrue(
                    "Stack Trace method name mismatch - ${stackTraceElement.methodName} does not match $methodName",
                    stackTraceElement.methodName == methodName
                )
            }

            val cppPointerStack = exceptionStack
                .dropWhile { it.className != "Current_Trace" }
                .drop(1) // Skip also "Current_Trace"
            assertTrue(
                "Stack Trace did not match",
                cppPointerStack.first().className == "app.rive.runtime.kotlin.core.NativeObject"
                        && cppPointerStack.first().methodName == "getCppPointer"
            )
        }
    }

    /**
     * This test is designed to ensure that the file being replaced while rendering does not cause a
     * crash. It uses a Phaser to synchronize the main thread and the render thread.
     *
     * The test creates two files and sets the first one to the view. It then waits for the render
     * thread to draw its artboard before attempting to replace the file with the second one. The
     * test ensures that the render thread can complete its render without crashing.
     *
     * Originally this caused a crash when accessing the C++ pointer, but this is now handled by
     * using synchronization blocks on the file lock, ensuring the render thread finishes drawing
     * before the main thread releases the file.
     *
     * The phaser has three phases:
     * 1. Initial sync: The main thread waits for the render thread to start drawing.
     * 2. Post-release: The render thread waits for the main thread to release the file.
     * 3. Post-draw: The main thread waits for the render thread to finish drawing, allowing the
     *    test to conclude.
     */
    @Test
    fun fileReplacedWhileRendering() {
        val initialSync = 0
        val postRelease = 1
        val postDraw = 2

        // Allows override of the draw method to synchronize with the main thread
        class PhasedArtboard(
            unsafeCppPointer: Long,
            lock: ReentrantLock,
            private val phaser: Phaser,
        ) : Artboard(unsafeCppPointer, lock) {
            override fun draw(
                cppPointer: Long,
                fit: Fit,
                alignment: Alignment,
                scaleFactor: Float
            ) {
                // Initial sync spot with main
                phaser.arrive()
                phaser.awaitAdvance(initialSync)

                // Awaiting main to release the file
                phaser.arrive()
                // Try/catch rather than assertion so that the test fails with its original error
                // if this doesn't timeout.
                try {
                    phaser.awaitAdvanceInterruptibly(postRelease, 200L, TimeUnit.MILLISECONDS)
                } catch (_: TimeoutException) {
                    // This is expected, as the main thread cannot set the file while the render
                    // thread is drawing, due to the synchronization on the lock.
                }

                // Only now do we draw, at which point the file would have been replaced
                super.draw(cppPointer, fit, alignment, scaleFactor)
                // Arrive at postDraw - we're done
                phaser.arrive()
            }
        }

        // Simple mock to ensure we're creating a PhasedArtboard
        class PhasedFile(bytes: ByteArray, private val phaser: Phaser) : File(bytes) {
            override fun artboard(index: Int): Artboard {
                val artboardPointer = cppArtboardByIndex(cppPointer, index)
                val ab = PhasedArtboard(artboardPointer, lock, phaser)
                dependencies.add(ab)
                return ab
            }
        }

        // Mock for manual rendering and render thread setup
        class MockAnimationView(appContext: Context) : RiveAnimationView(appContext) {
            init {
                // Simulate immediate attachment
                super.onAttachedToWindow()

                // Required to setup the JNIRenderer's WorkerImpl
                // This will create warnings and errors in the logs, but they can be ignored.
                val surface = Surface(SurfaceTexture(0))
                renderer?.setSurface(surface)
            }

            // Fills the role of the Choreographer, allowing for manual frame advance
            fun render() = artboardRenderer?.doFrame(0)

            override fun createRenderer(): Renderer =
                object : RiveArtboardRenderer(controller = controller) {
                    // Because doFrame calls scheduleFrame to recurse, we want it to no-op
                    override fun scheduleFrame() {}
                }
        }

        // A synchronizing primitive to ensure that the main thread and the render thread are in
        // lockstep
        val phaser = Phaser(2)

        // File bytes are arbitrary
        val (file1, file2) = appContext.resources.openRawResource(R.raw.flux_capacitor).use { res ->
            val fileBytes = res.readBytes()
            val file1 = PhasedFile(fileBytes, phaser)
            val file2 = PhasedFile(fileBytes, phaser)
            Pair(file1, file2)
        }

        // Create subclasses of the view and renderer which are more unit-test friendly,
        // substituting lifecycle, surface, and rendering operations.
        val view = MockAnimationView(appContext)

        view.setRiveFile(file1)
        // Account for the acquisition in the File's constructor
        file1.release()

        // Start the render thread
        view.render()

        // Initial sync - wait for render thread to arrive
        phaser.arrive()
        phaser.awaitAdvance(initialSync)

        // Setting file2 will release file1
        view.setRiveFile(file2)
        // Arrive at postRelease
        phaser.arrive()

        // Arrive and wait for postDraw
        phaser.arrive()
        phaser.awaitAdvance(postDraw)
    }
}

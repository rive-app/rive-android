package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import app.rive.runtime.kotlin.core.errors.RiveException
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith


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
            assertEquals(2, ogFile?.refCount)
            riveFileController.advance(0f)
            appContext.resources.openRawResource(R.raw.state_machine_configurations).use {
                val file = File(it.readBytes()) // Acquire File upon creation.
                mockView.setRiveFile(file)
                file.release() // Release ownership.
            }
            assertEquals(0, ogFile?.refCount)
            assertEquals(2, riveFileController.file?.refCount)
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
            assertEquals(2, ogFile?.refCount)
            val artboard = riveFileController.activeArtboard!!
            assertEquals(2, artboard.refCount)
            riveFileController.reset() // Gives up artboard.
            assertEquals(1, artboard.refCount) // Artboard is still retained by the File.

            mockView.mockDetach()
            // Check artboard has been disposed.
            assertEquals(0, artboard.refCount)
        }
    }
}

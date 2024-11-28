package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.ChangedInput
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread


@RunWith(AndroidJUnit4::class)
class RiveControllerTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context

    @Test
    fun initEmpty() {
        val fileController = RiveFileController()
        assert(!fileController.isAdvancing)
        fileController.play()
        // Without file nor artboard there's nothing to play...
        assert(!fileController.isAdvancing)
    }

    @Test
    fun initEmptyAddFile() {
        val fileController = RiveFileController()
        assertFalse(fileController.isAdvancing)
        val bytes = appContext.resources
            .openRawResource(R.raw.off_road_car_blog)
            .use {
                it.readBytes()
            }
        val file = File(bytes)
        assertNull(fileController.file)
        assertNull(fileController.activeArtboard)

        fileController.setRiveFile(file)
        assertNotNull(fileController.activeArtboard)
        // Setting the File with autoplay (default) starts the controller.
        assertTrue(fileController.isAdvancing)
    }

    @Test
    fun initEmptyAddFilePlayNoArtboard() {
        val file = appContext.resources
            .openRawResource(R.raw.off_road_car_blog)
            .use {
                File(it.readBytes())
            }
        val fileController = RiveFileController(file = file, autoplay = false)
        assertNull(fileController.activeArtboard)
        assertFalse(fileController.isAdvancing)
        // Cannot play without an active artboard.
        fileController.play()
        assertFalse(fileController.isAdvancing)
    }

    @Test
    fun initEmptyAddFilePlayAnimation() {
        val file = appContext.resources
            .openRawResource(R.raw.off_road_car_blog)
            .use {
                File(it.readBytes())
            }
        val fileController = RiveFileController(file = file, autoplay = false)
        assertNull(fileController.activeArtboard)
        assertFalse(fileController.isAdvancing)

        fileController.apply {
            // Select the first artboard.
            selectArtboard()
            // Play the first animation
            play()
        }
        assertTrue(fileController.isAdvancing)
        assertEquals("idle", fileController.animations.first().name)
    }

    @Test
    fun controllerOnStart() {
        var hasStarted = false
        val controller = RiveFileController {
            // onStart() callback
            hasStarted = true
        }
        val file = appContext.resources
            .openRawResource(R.raw.off_road_car_blog)
            .use {
                File(it.readBytes())
            }
        assertFalse(hasStarted)
        controller.setRiveFile(file)
        assertTrue(hasStarted)
    }

    @Test
    fun multipleControllersSameFile() {
        val file = appContext.resources
            .openRawResource(R.raw.off_road_car_blog)
            .use {
                File(it.readBytes())
            }
        val firstController = RiveFileController()
        assertNull(firstController.activeArtboard)
        assertFalse(firstController.isAdvancing)

        firstController.setRiveFile(file)
        assertNotNull(firstController.activeArtboard)
        assertTrue(firstController.isAdvancing)
        assertEquals(1, firstController.animations.size)

        val secondController = RiveFileController()
        assertNull(secondController.activeArtboard)
        assertFalse(secondController.isAdvancing)
        // Setting the *same* file.
        secondController.setRiveFile(file)
        assertNotNull(secondController.activeArtboard)
        assertTrue(secondController.isAdvancing)
        assertEquals(1, secondController.animations.size)

        // Different Artboard instances.
        assertNotEquals(firstController.activeArtboard, secondController.activeArtboard)
        // Different Animation instances.
        assertNotEquals(
            firstController.animations.first(),
            secondController.animations.first(),
        )
    }

    // This tests a bug where between checking if the queue is empty and polling an item from the queue,
    // another thread could empty the queue, causing a NoSuchElementException.
    // This was resolved by using poll() instead of remove().
    // This test should simply run without crashing.
    @Test
    fun testProcessAllInputsQueueEmptiedByAnotherThread() {
        // To simulate the threading conditions, we need a mock queue that allows interrupting at the precise moment when the bug occurs.
        class MockInputQueue(
            private val latch: CountDownLatch,
            items: Collection<ChangedInput> = emptyList()
        ) : ConcurrentLinkedQueue<ChangedInput>(items) {
            override fun isEmpty(): Boolean {
                val wasEmpty = super.isEmpty() // Cache the result before it's cleared
                latch.countDown() // Signal the clearing thread to proceed
                Thread.sleep(10) // Give that thread a chance to clear the queue
                return wasEmpty
            }
        }

        // We need a file and artboard so that the controller doesn't skip advancing
        val file = appContext.resources
            .openRawResource(R.raw.off_road_car_blog)
            .use {
                File(it.readBytes())
            }

        val latch = CountDownLatch(1) // Threading primitive to synchronize the two threads
        val inputQueue = MockInputQueue(latch, listOf(ChangedInput("stateMachine", "input")))
        val controller = RiveFileController(changedInputs = inputQueue)
        controller.setRiveFile(file)

        // Start a thread to empty the queue
        thread {
            latch.await() // Wait until the queue is checked
            inputQueue.clear()
        }

        // Invoke processAllInputs by way of advance
        controller.advance(0.0f)

        // No assertions. The intent is to run without throwing a NoSuchElementException.
    }
}

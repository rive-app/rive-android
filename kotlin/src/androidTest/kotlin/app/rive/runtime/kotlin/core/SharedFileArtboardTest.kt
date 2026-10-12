package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import app.rive.runtime.kotlin.controllers.ControllerStateManagement
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Artboards a controller instances from a [File] it does not own must not stay alive in that File
 * after the controller lets go of the File. Apps commonly keep one File and hand it to many views.
 */
@RunWith(AndroidJUnit4::class)
class SharedFileArtboardTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context

    private fun loadFile(rendererType: RendererType = RendererType.Rive): File =
        appContext.resources.openRawResource(R.raw.off_road_car_blog).use {
            File(it.readBytes(), rendererType)
        }

    @Test
    fun viewsOnASharedFileReleaseArtboardsWhenDestroyed() {
        UiThreadStatement.runOnUiThread {
            val file = loadFile()
            repeat(20) {
                val view = TestUtils.MockRiveAnimationView(appContext)
                view.setRiveFile(file)
                assertEquals(1, file.dependencies.size)
                view.mockDetach()
                assertEquals(0, file.dependencies.size)
            }
            file.release()
        }
    }

    @Test
    fun replacedArtboardsStayAliveUntilTheViewLetsGoOfTheFile() {
        UiThreadStatement.runOnUiThread {
            val file = loadFile()
            val view = TestUtils.MockRiveAnimationView(appContext)
            view.setRiveFile(file)
            val first = view.controller.activeArtboard!!

            repeat(10) { view.setRiveFile(file) }

            // Replacing an artboard keeps its natives, as reset() promises.
            assertTrue(first.hasCppObject)
            view.mockDetach()
            assertFalse(first.hasCppObject)
            assertEquals(0, file.dependencies.size)
            file.release()
        }
    }

    @Test
    fun artboardSuppliedByTheCallerIsNotDisposedWhenReplaced() {
        val file = loadFile()
        val callerArtboard = file.firstArtboard
        val controller = RiveFileController()
        controller.file = file
        controller.activeArtboard = callerArtboard

        controller.selectArtboard()

        assertTrue(callerArtboard.hasCppObject)
        assertTrue(file.dependencies.contains(callerArtboard))
        controller.release()
        assertTrue(callerArtboard.hasCppObject)
        file.release()
        assertFalse(callerArtboard.hasCppObject)
    }

    @OptIn(ControllerStateManagement::class)
    @Test
    fun restoredArtboardStaysAliveUntilTheRestoringControllerLetsGoOfTheFile() {
        val file = loadFile()
        val first = RiveFileController()
        first.setRiveFile(file)
        val artboard = first.activeArtboard!!
        val state = first.saveControllerState()!!
        first.release()

        val second = RiveFileController()
        second.restoreControllerState(state)
        second.reset()

        // reset() keeps the old artboard's natives, also for a restored artboard.
        assertTrue(artboard.hasCppObject)
        second.release()
        assertFalse(artboard.hasCppObject)
        assertEquals(0, file.dependencies.size)
        file.release()
    }

    @OptIn(ControllerStateManagement::class)
    @Test
    fun restoredArtboardSurvivesTheSavingControllerLettingGoOfTheFile() {
        val file = loadFile()
        val otherFile = loadFile()
        val first = RiveFileController()
        first.setRiveFile(file)
        val artboard = first.activeArtboard!!
        val state = first.saveControllerState()!!
        val second = RiveFileController()
        second.restoreControllerState(state)

        // The saving controller moves on while the restoring one still shows the artboard.
        first.setRiveFile(otherFile)
        second.reset()

        assertTrue(artboard.hasCppObject)
        second.release()
        assertFalse(artboard.hasCppObject)
        first.release()
        otherFile.release()
        file.release()
    }

    @OptIn(ControllerStateManagement::class)
    @Test
    fun restoredArtboardSuppliedByTheCallerStaysInTheFile() {
        val file = loadFile()
        val callerArtboard = file.firstArtboard
        val first = RiveFileController()
        first.file = file
        first.activeArtboard = callerArtboard
        val state = first.saveControllerState()!!
        val second = RiveFileController()
        second.restoreControllerState(state)

        first.release()
        second.release()

        assertTrue(callerArtboard.hasCppObject)
        assertTrue(file.dependencies.contains(callerArtboard))
        file.release()
        assertFalse(callerArtboard.hasCppObject)
    }

    @OptIn(ControllerStateManagement::class)
    @Test
    fun restoringIntoTheSavingControllerDoesNotStackReferences() {
        val file = loadFile()
        val controller = RiveFileController()
        controller.setRiveFile(file)
        val artboard = controller.activeArtboard!!

        repeat(5) { controller.restoreControllerState(controller.saveControllerState()!!) }

        // The File's reference and the controller's active artboard reference.
        assertEquals(2, artboard.refCount)
        controller.release()
        assertFalse(artboard.hasCppObject)
        file.release()
    }

    /**
     * On the Canvas renderer every paint and path holds a JNI global reference, so artboards left
     * behind in a shared File abort the process at Android's 51,200-entry limit ("global reference
     * table overflow"). Before the fix this test aborted within a few hundred iterations.
     */
    @Test
    fun canvasControllersOnASharedFileStayBelowTheJniGlobalReferenceLimit() {
        val file = loadFile(RendererType.Canvas)
        repeat(2_000) {
            val controller = RiveFileController()
            controller.setRiveFile(file)
            controller.release()
        }
        assertEquals(0, file.dependencies.size)
        file.release()
    }
}

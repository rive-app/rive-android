package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import app.rive.runtime.kotlin.RiveArtboardRenderer
import app.rive.runtime.kotlin.core.errors.RiveException
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RiveMemoryTests {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context
    private lateinit var mockRenderer: RiveArtboardRenderer


    @Before
    fun init() {
        mockRenderer = TestUtils.MockArtboardRenderer()
    }

    @Test
    fun filesAccess() {
        val file = File(
            appContext.resources.openRawResource(R.raw.state_machine_configurations).readBytes()
        )
        // cannot access file properties after disposing file.
        assertEquals(file.artboardNames, listOf("New Artboard"))
        file.dispose()
        assertThrows(RiveException::class.java) {
            file.artboardNames
        }
    }

    @Test
    fun artboardAccess() {
        val file = File(
            appContext.resources.openRawResource(R.raw.state_machine_configurations).readBytes()
        )
        val artboard = file.firstArtboard
        assertEquals(artboard.name, "New Artboard")
        file.dispose()
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
        file.dispose()
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
        file.dispose()
        assertThrows(RiveException::class.java) {
            animation.name
        }
    }

    @Test
    fun layerStateAccess() {
        var mockView = TestUtils.MockNoopRiveAnimationView(appContext)
        lateinit var layerState: LayerState
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(
                R.raw.layerstatechange,
                stateMachineName = "State Machine 1",
                autoplay = true
            )
            mockView.renderer.advance(1500f)
            layerState = mockView.renderer.stateMachines.first().statesChanged.first()
            assertTrue(layerState.isAnimationState)
            // lets assume our view got gc'd
            mockView.renderer.dispose()
        }
        assertThrows(RiveException::class.java) {
            layerState.isAnimationState
        }
    }

    @Test
    fun resetDoesNotInvalidatesObjects() {
        var mockView = TestUtils.MockNoopRiveAnimationView(appContext)

        UiThreadStatement.runOnUiThread {
            val observer = TestUtils.Observer()
            mockView.registerListener(observer)
            mockView.setRiveResource(
                R.raw.layerstatechange,
                stateMachineName = "State Machine 1",
                autoplay = true
            )
            mockView.renderer.advance(0f)
            val artboard = mockView.renderer.activeArtboard
            val stateMachine = artboard?.stateMachine(0)
            mockView.reset()

            assertEquals(artboard?.name, "New Artboard")
            assertEquals(stateMachine?.name, "State Machine 1")

//            but disposing the renderer will still remove artboards after reset
            mockView.renderer.dispose()
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
        var mockView = TestUtils.MockNoopRiveAnimationView(appContext)
        lateinit var artboard: Artboard
        UiThreadStatement.runOnUiThread {
            mockView.setRiveResource(
                R.raw.layerstatechange,
                stateMachineName = "State Machine 1",
                autoplay = true
            )
            mockView.renderer.advance(0f)
            artboard = mockView.file!!.firstArtboard
            assertEquals(artboard.name, "New Artboard")
            mockView.reset()
            // reset will not have cleared this artboard, it was never attacked to the view.
            assertEquals(artboard.name, "New Artboard")
            // lets assume our view got gc'd
            mockView.renderer.dispose()
        }
        assertThrows(RiveException::class.java) {
            artboard.name
        }
    }

}

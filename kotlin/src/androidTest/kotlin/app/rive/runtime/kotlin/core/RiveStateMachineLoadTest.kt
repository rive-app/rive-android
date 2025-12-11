package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.core.errors.RiveException
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RiveStateMachineLoadTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context

    @Test
    fun loadStateMachineFirstStateMachine() {
        val file = File(appContext.resources.openRawResource(R.raw.multipleartboards).readBytes())
        val artboard = file.artboard("artboard1")

        val stateMachineAlt = artboard.stateMachine(0)
        val stateMachine = artboard.stateMachine("artboard1stateMachine1")
        assertEquals(stateMachineAlt.name, stateMachine.name)
        assertNotEquals(stateMachineAlt.cppPointer, stateMachine.cppPointer)
        assertEquals(listOf("artboard1stateMachine1"), artboard.stateMachineNames)
    }

    @Test
    fun loadStateMachineSecondStateMachine() {
        val file = File(appContext.resources.openRawResource(R.raw.multipleartboards).readBytes())
        val artboard = file.artboard("artboard2")
        val artboard2stateMachine1 = artboard.stateMachine(0)
        val artboard2stateMachine1Alt = artboard.stateMachine("artboard2stateMachine1")
        assertNotEquals(artboard2stateMachine1Alt.cppPointer, artboard2stateMachine1.cppPointer)
        assertEquals(artboard2stateMachine1Alt.name, artboard2stateMachine1.name)

        val artboard2stateMachine2 = artboard.stateMachine(1)
        val artboard2stateMachine2Alt = artboard.stateMachine("artboard2stateMachine2")
        assertNotEquals(artboard2stateMachine2Alt.cppPointer, artboard2stateMachine2.cppPointer)
        assertEquals(artboard2stateMachine2Alt.name, artboard2stateMachine2.name)
        assertEquals(
            listOf("artboard2stateMachine1", "artboard2stateMachine2"),
            artboard.stateMachineNames
        )
    }

    @Test
    fun artboardHasNoStateMachines() {
        val bytes = appContext.resources.openRawResource(R.raw.noanimation).readBytes()
        val file = File(bytes)
        val artboard = file.firstArtboard

        // If a Rive file has no state machines, it will create one auto-generated state machine
        assertEquals(1, artboard.stateMachineCount)
        assertEquals(listOf("Auto Generated State Machine"), artboard.stateMachineNames)
    }

    @Test
    fun loadFirstStateMachineDoesNotExist() {
        val file = File(appContext.resources.openRawResource(R.raw.noanimation).readBytes())
        val artboard = file.firstArtboard

        // Does not throw, this will find the auto-generated state machine
        artboard.firstStateMachine
    }

    @Test
    fun loadStateMachineByIndexDoesNotExist() {
        val file = File(appContext.resources.openRawResource(R.raw.noanimation).readBytes())
        val artboard = file.firstArtboard

        // Does not throw, this will find the auto-generated state machine
        artboard.stateMachine(0)

        assertThrows(RiveException::class.java) {
            artboard.stateMachine(1)
        }
    }

    @Test(expected = RiveException::class)
    fun loadStateMachineByNameDoesNotExist() {
        val file = File(appContext.resources.openRawResource(R.raw.noanimation).readBytes())
        val artboard = file.firstArtboard
        artboard.stateMachine("foo")
    }
}

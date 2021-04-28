package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RiveStateMachineLoadTest {

    @Test
    fun loadStateMachineFirstStateMachine() {
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.multipleartboards).readBytes())
        var artboard = file.artboard("artboard1")

        var stateMachineAlt = artboard.stateMachine(0)
        var stateMachine = artboard.stateMachine("artboard1stateMachine1")
        assertEquals(stateMachineAlt.cppPointer, stateMachine.cppPointer)
        assertEquals(listOf("artboard1stateMachine1"), artboard.stateMachineNames)
    }

    @Test
    fun loadStateMachineSecondStateMachine() {
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.multipleartboards).readBytes())
        var artboard = file.artboard("artboard2")
        var artboard2stateMachine1 = artboard.stateMachine(0)
        var artboard2stateMachine1Alt = artboard.stateMachine("artboard2stateMachine1")
        assertEquals(artboard2stateMachine1Alt.cppPointer, artboard2stateMachine1.cppPointer)

        var artboard2stateMachine2 = artboard.stateMachine(1)
        var artboard2stateMachine2Alt = artboard.stateMachine("artboard2stateMachine2")
        assertEquals(artboard2stateMachine2Alt.cppPointer, artboard2stateMachine2.cppPointer)
        assertEquals(listOf("artboard2stateMachine1", "artboard2stateMachine2"), artboard.stateMachineNames)
    }

    @Test
    fun artboardHasNoStateMachines() {
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.noanimation).readBytes())
        var artboard = file.firstArtboard
        assertEquals(0, artboard.stateMachineCount)
        assertEquals(listOf<String>(), artboard.stateMachineNames)
    }

    @Test(expected = RiveException::class)
    fun loadFirstStateMachineNoExists() {
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.noanimation).readBytes())
        var artboard = file.firstArtboard
        artboard.firstStateMachine
    }

    @Test(expected = RiveException::class)
    fun loadStateMachineByIndexDoesntExist() {
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.noanimation).readBytes())
        var artboard = file.firstArtboard
        artboard.stateMachine(1)
    }

    @Test(expected = RiveException::class)
    fun loadStateMachineByNameDoesntExist() {
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.noanimation).readBytes())
        var artboard = file.firstArtboard
        artboard.stateMachine("foo")
    }
}
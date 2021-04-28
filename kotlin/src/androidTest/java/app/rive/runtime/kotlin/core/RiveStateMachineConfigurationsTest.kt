package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RiveStateMachineConfigurationsTest {

    @Test
    fun nothing() {
        val appContext = initTests()
        var file =
            File(appContext.resources.openRawResource(R.raw.state_machine_configurations).readBytes())
        var state_machine = file.firstArtboard.stateMachine("nothing")
        assertEquals(0, state_machine.inputCount)
        assertEquals(0, state_machine.layerCount)
    }

    @Test
    fun one_layer() {
        val appContext = initTests()
        var file =
            File(appContext.resources.openRawResource(R.raw.state_machine_configurations).readBytes())
        var state_machine = file.firstArtboard.stateMachine("one_layer")
        assertEquals(0, state_machine.inputCount)
        assertEquals(1, state_machine.layerCount)
    }


    @Test
    fun two_layers() {
        val appContext = initTests()
        var file =
            File(appContext.resources.openRawResource(R.raw.state_machine_configurations).readBytes())
        var state_machine = file.firstArtboard.stateMachine("two_layers")
        assertEquals(0, state_machine.inputCount)
        assertEquals(2, state_machine.layerCount)
    }

    @Test
    fun number_input() {
        val appContext = initTests()
        var file =
            File(appContext.resources.openRawResource(R.raw.state_machine_configurations).readBytes())
        var state_machine = file.firstArtboard.stateMachine("number_input")
        assertEquals(1, state_machine.inputCount)
        assertEquals(1, state_machine.layerCount)
    }

    @Test
    fun boolean_input() {
        val appContext = initTests()
        var file =
            File(appContext.resources.openRawResource(R.raw.state_machine_configurations).readBytes())
        var state_machine = file.firstArtboard.stateMachine("boolean_input")
        assertEquals(1, state_machine.inputCount)
        assertEquals(1, state_machine.layerCount)
    }

    @Test
    fun trigger_input() {
        val appContext = initTests()
        var file =
            File(appContext.resources.openRawResource(R.raw.state_machine_configurations).readBytes())
        var state_machine = file.firstArtboard.stateMachine("trigger_input")
        assertEquals(1, state_machine.inputCount)
        assertEquals(1, state_machine.layerCount)
    }

    @Test
    fun mixed() {
        val appContext = initTests()
        var file =
            File(appContext.resources.openRawResource(R.raw.state_machine_configurations).readBytes())
        var state_machine = file.firstArtboard.stateMachine("mixed")
        assertEquals(6, state_machine.inputCount)
        assertEquals(4, state_machine.layerCount)
    }

}
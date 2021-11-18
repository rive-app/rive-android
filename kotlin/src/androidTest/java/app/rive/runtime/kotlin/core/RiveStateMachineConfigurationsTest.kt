package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RiveStateMachineConfigurationsTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context

    @Test
    fun nothing() {
        var file =
            File(
                appContext.resources.openRawResource(R.raw.state_machine_configurations).readBytes()
            )
        var state_machine = file.firstArtboard.stateMachine("nothing")
        assertEquals(0, state_machine.inputCount)
        assertEquals(0, state_machine.layerCount)
    }

    @Test
    fun one_layer() {
        var file =
            File(
                appContext.resources.openRawResource(R.raw.state_machine_configurations).readBytes()
            )
        var state_machine = file.firstArtboard.stateMachine("one_layer")
        assertEquals(0, state_machine.inputCount)
        assertEquals(1, state_machine.layerCount)
    }


    @Test
    fun two_layers() {
        var file =
            File(
                appContext.resources.openRawResource(R.raw.state_machine_configurations).readBytes()
            )
        var state_machine = file.firstArtboard.stateMachine("two_layers")
        assertEquals(0, state_machine.inputCount)
        assertEquals(2, state_machine.layerCount)
    }

    @Test
    fun number_input() {
        var file =
            File(
                appContext.resources.openRawResource(R.raw.state_machine_configurations).readBytes()
            )
        var state_machine = file.firstArtboard.stateMachine("number_input")
        assertEquals(1, state_machine.inputCount)
        assertEquals(1, state_machine.layerCount)
        var input = state_machine.input(0)
        assertEquals(false, input.isBoolean)
        assertEquals(false, input.isTrigger)
        assertEquals(true, input.isNumber)
        assertEquals("Number 1", input.name)
        assertEquals(state_machine.input("Number 1").cppPointer, input.cppPointer);
    }

    @Test
    fun boolean_input() {
        var file =
            File(
                appContext.resources.openRawResource(R.raw.state_machine_configurations).readBytes()
            )
        var state_machine = file.firstArtboard.stateMachine("boolean_input")
        assertEquals(1, state_machine.inputCount)
        assertEquals(1, state_machine.layerCount)
        var input = state_machine.input(0)
        assertEquals(true, input.isBoolean)
        assertEquals(false, input.isTrigger)
        assertEquals(false, input.isNumber)
        assertEquals("Boolean 1", input.name)
        assertEquals(state_machine.input("Boolean 1").cppPointer, input.cppPointer);
    }

    @Test
    fun trigger_input() {
        var file =
            File(
                appContext.resources.openRawResource(R.raw.state_machine_configurations).readBytes()
            )
        var state_machine = file.firstArtboard.stateMachine("trigger_input")
        assertEquals(1, state_machine.inputCount)
        assertEquals(1, state_machine.layerCount)
        var input = state_machine.input(0)
        assertEquals(false, input.isBoolean)
        assertEquals(true, input.isTrigger)
        assertEquals(false, input.isNumber)
        assertEquals("Trigger 1", input.name)
        assertEquals(state_machine.input("Trigger 1").cppPointer, input.cppPointer);
    }

    @Test
    fun mixed() {
        var file =
            File(
                appContext.resources.openRawResource(R.raw.state_machine_configurations).readBytes()
            )
        var state_machine = file.firstArtboard.stateMachine("mixed")
        assertEquals(6, state_machine.inputCount)
        assertEquals(4, state_machine.layerCount)
        assertEquals(
            listOf("zero", "off", "trigger", "two_point_two", "on", "three"),
            state_machine.inputNames
        )
        assertEquals(true, state_machine.input("zero").isNumber)
        assertEquals(true, state_machine.input("off").isBoolean)
        assertEquals(true, state_machine.input("trigger").isTrigger)
        assertEquals(true, state_machine.input("two_point_two").isNumber)
        assertEquals(true, state_machine.input("on").isBoolean)
        assertEquals(true, state_machine.input("three").isNumber)

        assertEquals(true, state_machine.input("zero") is StateMachineNumberInput)
        assertEquals(0f, (state_machine.input("zero") as StateMachineNumberInput).value)

        assertEquals(true, state_machine.input("three") is StateMachineNumberInput)
        assertEquals(3f, (state_machine.input("three") as StateMachineNumberInput).value)

        assertEquals(true, state_machine.input("two_point_two") is StateMachineNumberInput)
        assertEquals(2.2f, (state_machine.input("two_point_two") as StateMachineNumberInput).value)

        assertEquals(true, state_machine.input("off") is StateMachineBooleanInput)
        assertEquals(false, (state_machine.input("off") as StateMachineBooleanInput).value)

        assertEquals(true, state_machine.input("on") is StateMachineBooleanInput)
        assertEquals(true, (state_machine.input("on") as StateMachineBooleanInput).value)

        assertEquals(true, state_machine.input("trigger") is StateMachineTriggerInput)


    }

}
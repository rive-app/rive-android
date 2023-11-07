package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RiveStateMachineInstanceTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context
    private lateinit var file: File

    @Before
    fun init() {
        file = File(
            appContext.resources.openRawResource(R.raw.state_machine_configurations).readBytes()
        )
    }

    @Test
    fun nothing() {
        val stateMachine = file.firstArtboard.stateMachine("nothing")
        assertEquals(0, stateMachine.inputCount)
    }

    @Test
    fun numberInput() {
        val stateMachine = file.firstArtboard.stateMachine("number_input")
        val input = stateMachine.input(0)
        assertEquals(false, input.isBoolean)
        assertEquals(false, input.isTrigger)
        assertEquals(true, input.isNumber)
        assertEquals("Number 1", input.name)

        val numberInput = input as SMINumber
        numberInput.value = 15f
        assertEquals(15f, numberInput.value)
    }

    @Test
    fun booleanInput() {
        val stateMachine = file.firstArtboard.stateMachine("boolean_input")
        val input = stateMachine.input(0)
        assertEquals(1, stateMachine.inputCount)
        assertEquals(true, input.isBoolean)
        assertEquals(false, input.isTrigger)
        assertEquals(false, input.isNumber)
        assertEquals("Boolean 1", input.name)

        val booleanInput = input as SMIBoolean
        booleanInput.value = false
        assertEquals(false, booleanInput.value)
        booleanInput.value = true
        assertEquals(true, booleanInput.value)
    }

    @Test
    fun triggerInput() {
        val stateMachine = file.firstArtboard.stateMachine("trigger_input")
        val input = stateMachine.input(0)
        assertEquals(1, stateMachine.inputCount)
        assertEquals(false, input.isBoolean)
        assertEquals(true, input.isTrigger)
        assertEquals(false, input.isNumber)
        assertEquals("Trigger 1", input.name)


        val triggerInput = input as SMITrigger
        triggerInput.fire()
    }

    @Test
    fun mixed() {
        val stateMachine = file.firstArtboard.stateMachine("mixed")
        assertEquals(6, stateMachine.inputCount)

        assertEquals(
            listOf("zero", "off", "trigger", "two_point_two", "on", "three"),
            stateMachine.inputNames
        )
        assertEquals(true, stateMachine.input("zero").isNumber)
        assertEquals(true, stateMachine.input("off").isBoolean)
        assertEquals(true, stateMachine.input("trigger").isTrigger)
        assertEquals(true, stateMachine.input("two_point_two").isNumber)
        assertEquals(true, stateMachine.input("on").isBoolean)
        assertEquals(true, stateMachine.input("three").isNumber)

        assertEquals(true, stateMachine.input("zero") is SMINumber)
        assertEquals(0f, (stateMachine.input("zero") as SMINumber).value)

        assertEquals(true, stateMachine.input("three") is SMINumber)
        assertEquals(3f, (stateMachine.input("three") as SMINumber).value)

        assertEquals(true, stateMachine.input("two_point_two") is SMINumber)
        assertEquals(2.2f, (stateMachine.input("two_point_two") as SMINumber).value)

        assertEquals(true, stateMachine.input("off") is SMIBoolean)
        assertEquals(false, (stateMachine.input("off") as SMIBoolean).value)

        assertEquals(true, stateMachine.input("on") is SMIBoolean)
        assertEquals(true, (stateMachine.input("on") as SMIBoolean).value)

        assertEquals(true, stateMachine.input("trigger") is SMITrigger)
    }
}
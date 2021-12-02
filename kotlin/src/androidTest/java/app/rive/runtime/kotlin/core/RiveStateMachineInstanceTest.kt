package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.RiveArtboardRenderer
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RiveStateMachineInstanceTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context
    private lateinit var mockRenderer: RiveArtboardRenderer
    private lateinit var file: File

    @Before
    fun init() {
        mockRenderer = TestUtils.MockArtboardRenderer()
        file = File(
            appContext.resources.openRawResource(R.raw.state_machine_configurations).readBytes()
        )
    }

    @Test
    fun nothing() {
        val stateMachine = file.firstArtboard.stateMachine("nothing")
        val instance = StateMachineInstance(stateMachine)
        assertEquals(0, instance.inputCount)
    }

    @Test
    fun number_input() {
        val stateMachine = file.firstArtboard.stateMachine("number_input")
        val instance = StateMachineInstance(stateMachine)
        val input = instance.input(0)
        assertEquals(false, input.isBoolean)
        assertEquals(false, input.isTrigger)
        assertEquals(true, input.isNumber)
        assertEquals("Number 1", input.name)

        val numberInput = input as SMINumber
        numberInput.value = 15f
        assertEquals(15f, numberInput.value)
    }

    @Test
    fun boolean_input() {
        val stateMachine = file.firstArtboard.stateMachine("boolean_input")
        val instance = StateMachineInstance(stateMachine)
        val input = instance.input(0)
        assertEquals(1, instance.inputCount)
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
    fun trigger_input() {
        val stateMachine = file.firstArtboard.stateMachine("trigger_input")
        val instance = StateMachineInstance(stateMachine)
        val input = instance.input(0)
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
        val instance = StateMachineInstance(stateMachine)
        assertEquals(6, stateMachine.inputCount)

        assertEquals(
            listOf("zero", "off", "trigger", "two_point_two", "on", "three"),
            stateMachine.inputNames
        )
        assertEquals(true, instance.input("zero").isNumber)
        assertEquals(true, instance.input("off").isBoolean)
        assertEquals(true, instance.input("trigger").isTrigger)
        assertEquals(true, instance.input("two_point_two").isNumber)
        assertEquals(true, instance.input("on").isBoolean)
        assertEquals(true, instance.input("three").isNumber)

        assertEquals(true, instance.input("zero") is SMINumber)
        assertEquals(0f, (instance.input("zero") as SMINumber).value)

        assertEquals(true, instance.input("three") is SMINumber)
        assertEquals(3f, (instance.input("three") as SMINumber).value)

        assertEquals(true, instance.input("two_point_two") is SMINumber)
        assertEquals(2.2f, (instance.input("two_point_two") as SMINumber).value)

        assertEquals(true, instance.input("off") is SMIBoolean)
        assertEquals(false, (instance.input("off") as SMIBoolean).value)

        assertEquals(true, instance.input("on") is SMIBoolean)
        assertEquals(true, (instance.input("on") as SMIBoolean).value)

        assertEquals(true, instance.input("trigger") is SMITrigger)


    }

}
package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.errors.RiveException
import app.rive.runtime.kotlin.core.errors.ViewModelException
import app.rive.runtime.kotlin.test.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContains
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class RiveDataBindingTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context
    private lateinit var view: TestUtils.MockRiveAnimationView
    private lateinit var vm: ViewModel
    private lateinit var vmi: ViewModelInstance

    data class Color(val r: Int, val g: Int, val b: Int, val a: Int) {
        companion object {
            // Parses a color string in the format "r:0 g:0 b:0 a:0" to a Color object,
            // as defined by the Editor's to string converter
            fun fromString(color: String): Color {
                val (a, r, g, b) = color.split(" ").map { it.split(":")[1].toInt(10) }
                return Color(r, g, b, a)
            }
        }
    }

    private fun String.toColor(): Color = Color.fromString(this)

    @Before
    fun init() {
        view = TestUtils.MockRiveAnimationView(appContext)

        // Most common setup. For variants, the view will need `setRiveResource` called again
        view.setRiveResource(R.raw.data_bind_test_impl)
        vm = view.controller.file?.getViewModelByName("Test All")!!
        vmi = vm.createInstanceFromName("Test Default")
        view.controller.activeArtboard?.viewModelInstance = vmi
    }

    @Test
    fun get_vm_properties() {
        assertEquals("Test All", vm.name)
        assertEquals(7, vm.propertyCount)

        val properties = vm.properties
        assertContains(
            properties,
            ViewModel.Property(ViewModel.PropertyDataType.NUMBER, "Test Num")
        )
        assertContains(
            properties,
            ViewModel.Property(ViewModel.PropertyDataType.STRING, "Test String")
        )
        assertContains(
            properties,
            ViewModel.Property(ViewModel.PropertyDataType.BOOLEAN, "Test Bool")
        )
        assertContains(
            properties,
            ViewModel.Property(ViewModel.PropertyDataType.ENUM, "Test Enum")
        )
        assertContains(
            properties,
            ViewModel.Property(ViewModel.PropertyDataType.COLOR, "Test Color")
        )
        assertContains(
            properties,
            ViewModel.Property(ViewModel.PropertyDataType.TRIGGER, "Test Trigger")
        )
        assertContains(
            properties,
            ViewModel.Property(ViewModel.PropertyDataType.VIEW_MODEL, "Test Nested")
        )
    }

    @Test
    fun get_vmi_values() {
        // Needs assigning to the SM to condition state transitions from the trigger
        view.controller.stateMachines.first().viewModelInstance = vmi

        // Since triggers don't have a "to string" that we can observe for a value, instead we can
        // listen to an event that fires when the trigger is activated (arranged through the state
        // machine's transition).
        var triggered = false
        view.controller.addEventListener(object : RiveFileController.RiveEventListener {
            override fun notifyEvent(event: RiveEvent) {
                if (event.name == "Test Trigger Event") {
                    triggered = true
                }
            }
        })

        assertEquals(2, vm.instanceCount)
        assertEquals("Test Default", vmi.name)

        // These values are default in the Rive file's view model instance
        assertEquals(123f, vmi.getNumberProperty("Test Num").value)
        assertEquals("World", vmi.getStringProperty("Test String").value)
        assertTrue(vmi.getBooleanProperty("Test Bool").value)
        assertEquals("Value 1", vmi.getEnumProperty("Test Enum").value)
        assertEquals(0xFFFF0000.toInt(), vmi.getColorProperty("Test Color").value)
        assertEquals(
            100f,
            vmi.getInstanceProperty("Test Nested").getNumberProperty("Nested Number").value
        )

        vmi.getTriggerProperty("Test Trigger").trigger()

        // Bindings will not apply their values until the artboard is advanced
        assertNotEquals("123", view.getTextRunValue("Test Number Value"))
        assertNotEquals("World", view.getTextRunValue("Test String Value"))
        assertNotEquals("true", view.getTextRunValue("Test Bool Value"))
        assertNotEquals("Value 1", view.getTextRunValue("Test Enum Value"))
        assertNotEquals(
            0xFFFF0000.toInt(),
            view.getTextRunValue("Test Color Value")?.toColor()
        )
        assertFalse(triggered)
        assertNotEquals("100", view.getTextRunValue("Test Nested Number Value"))

        // Apply the values by advancing
        view.controller.advance(0f)
        // Requires a second advance for events to be processed for the trigger
        view.controller.advance(0f)

        assertEquals("123", view.getTextRunValue("Test Number Value"))
        assertEquals("World", view.getTextRunValue("Test String Value"))
        assertEquals("1", view.getTextRunValue("Test Bool Value"))
        assertEquals("Value 1", view.getTextRunValue("Test Enum Value"))
        assertTrue(triggered)
        assertEquals("100", view.getTextRunValue("Test Nested Number Value"))

        val vmiAlt = vm.createInstanceFromName("Test Alternate")
        view.controller.activeArtboard?.viewModelInstance = vmiAlt

        assertEquals("Test Alternate", vmiAlt.name)

        assertEquals(456f, vmiAlt.getNumberProperty("Test Num").value)
        assertEquals("Moon", vmiAlt.getStringProperty("Test String").value)
        assertFalse(vmiAlt.getBooleanProperty("Test Bool").value)
        assertEquals("Value 2", vmiAlt.getEnumProperty("Test Enum").value)
        assertEquals(0xFF00FF00.toInt(), vmiAlt.getColorProperty("Test Color").value)
        assertEquals(
            200f,
            vmiAlt.getInstanceProperty("Test Nested").getNumberProperty("Nested Number").value
        )

        assertNotEquals("456", view.getTextRunValue("Test Number Value"))
        assertNotEquals("Moon", view.getTextRunValue("Test String Value"))
        assertNotEquals("0", view.getTextRunValue("Test Bool Value"))
        assertNotEquals("Value 2", view.getTextRunValue("Test Enum Value"))
        assertNotEquals(
            Color(0, 255, 0, 255),
            view.getTextRunValue("Test Color Value")?.toColor()
        )
        assertNotEquals("200", view.getTextRunValue("Test Nested Number Value"))

        view.play()

        assertEquals("456", view.getTextRunValue("Test Number Value"))
        assertEquals("Moon", view.getTextRunValue("Test String Value"))
        assertEquals("0", view.getTextRunValue("Test Bool Value"))
        assertEquals("Value 2", view.getTextRunValue("Test Enum Value"))
        assertEquals(
            Color(0, 255, 0, 255),
            view.getTextRunValue("Test Color Value")?.toColor()
        )
        assertEquals("200", view.getTextRunValue("Test Nested Number Value"))
    }

    @Test
    fun set_vmi_values() {
        // Advance to set the values from the "Test Default" instance
        view.play()

        // Set each property to a new value
        vmi.getNumberProperty("Test Num").value = 456f
        vmi.getStringProperty("Test String").value = "Moon"
        vmi.getBooleanProperty("Test Bool").value = false
        vmi.getEnumProperty("Test Enum").value = "Value 2"
        vmi.getColorProperty("Test Color").value = 0xFF00FF00.toInt()
        vmi.getNumberProperty("Test Nested/Nested Number").value = 200f

        // Bindings will not apply their values until the artboard is advanced
        assertNotEquals("456", view.getTextRunValue("Test Number Value"))
        assertNotEquals("Moon", view.getTextRunValue("Test String Value"))
        assertNotEquals("0", view.getTextRunValue("Test Bool Value"))
        assertNotEquals("Value 2", view.getTextRunValue("Test Enum Value"))
        assertNotEquals(
            Color(0, 255, 0, 255),
            view.getTextRunValue("Test Color Value")?.toColor()
        )
        assertNotEquals("200", view.getTextRunValue("Test Nested Number Value"))

        // Apply the values by advancing
        view.play()

        assertEquals("456", view.getTextRunValue("Test Number Value"))
        assertEquals("Moon", view.getTextRunValue("Test String Value"))
        assertEquals("0", view.getTextRunValue("Test Bool Value"))
        assertEquals("Value 2", view.getTextRunValue("Test Enum Value"))
        assertEquals(Color(0, 255, 0, 255), view.getTextRunValue("Test Color Value")?.toColor())
        assertEquals("200", view.getTextRunValue("Test Nested Number Value"))
    }

    @Test
    fun vm_by_index() {
        val vmCount = view.controller.file?.viewModelCount!!
        assertEquals(6, vmCount)

        // Iterate indices and verify all VM names are present
        assertEquals(
            listOf(
                "Test All",
                "Empty VM",
                "Nested VM",
                "State Transition",
                "Alternate VM",
                "Test Slash"
            ).sorted().toList(),
            (0 until vmCount).map { view.controller.file?.getViewModelByIndex(it)!!.name }
                .sorted().toList()
        )
    }

    @Test
    fun vmi_by_index() {
        assertEquals(2, vm.instanceCount)

        // Iterate indices and verify all instance names are present
        assertTrue(
            (0 until vm.instanceCount).map {
                val vmi = vm.createInstanceFromIndex(it)
                vmi.name
            }.containsAll(listOf("Test Default", "Test Alternate"))
        )
    }

    @Test
    fun non_existent_vm_throws() {
        // Missing ViewModel
        assertThrows(ViewModelException::class.java) {
            view.controller.file?.getViewModelByName("Non Existent")
        }

        // Missing ViewModelInstance
        assertThrows(ViewModelException::class.java) { vm.createInstanceFromName("Non Existent") }

        // Missing properties
        assertThrows(ViewModelException::class.java) { vmi.getNumberProperty("Non Existent") }
        assertThrows(ViewModelException::class.java) { vmi.getStringProperty("Non Existent") }
        assertThrows(ViewModelException::class.java) { vmi.getBooleanProperty("Non Existent") }
        assertThrows(ViewModelException::class.java) { vmi.getEnumProperty("Non Existent") }
        assertThrows(ViewModelException::class.java) { vmi.getColorProperty("Non Existent") }
        assertThrows(ViewModelException::class.java) { vmi.getTriggerProperty("Non Existent") }
    }

    @Test
    fun empty_vm() {
        val vm = view.controller.file?.getViewModelByName("Empty VM")!!
        assertEquals(0, vm.instanceCount)
        assertEquals(0, vm.propertyCount)
    }

    @Test
    fun mismatched_vm() {
        // This is not the VM assigned to the artboard in the editor, so no bindings are established
        val vm = view.controller.file?.getViewModelByName("Empty VM")!!
        val vmi = vm.createDefaultInstance()

        view.controller.activeArtboard?.viewModelInstance = vmi

        assertThrows(ViewModelException::class.java) {
            vmi.getNumberProperty("Test Num")
        }

        assertNotEquals("123", view.getTextRunValue("Test Number Value"))
        view.play()
        assertNotEquals("123", view.getTextRunValue("Test Number Value"))
    }

    @Test
    fun default_vm_for_artboard() {
        val vm =
            view.controller.file?.defaultViewModelForArtboard(view.controller.activeArtboard!!)!!
        assertEquals("Test All", vm.name)
    }

    @Test
    fun default_instance() {
        val vmi = vm.createDefaultInstance()
        assertEquals("Test Default", vmi.name)
    }

    @Test
    fun blank_instance() {
        val vmi = vm.createBlankInstance()
        assertEquals("", vmi.name)

        assertEquals(0f, vmi.getNumberProperty("Test Num").value)
        assertEquals("", vmi.getStringProperty("Test String").value)
        assertFalse(vmi.getBooleanProperty("Test Bool").value)
        assertEquals("Value 1", vmi.getEnumProperty("Test Enum").value)
        assertEquals(0xFF000000.toInt(), vmi.getColorProperty("Test Color").value)
        assertNotNull(vmi.getInstanceProperty("Test Nested"))
        assertEquals(0f, vmi.getNumberProperty("Test Nested/Nested Number").value)
    }

    @Test
    fun auto_bind() {
        view.setRiveResource(R.raw.data_bind_test_impl, autoBind = true)
        val vmi = view.controller.activeArtboard?.viewModelInstance!!

        assertEquals(123f, vmi.getNumberProperty("Test Num").value)
        assertEquals("World", vmi.getStringProperty("Test String").value)
        assertTrue(vmi.getBooleanProperty("Test Bool").value)
        assertEquals("Value 1", vmi.getEnumProperty("Test Enum").value)
        assertEquals(0xFFFF0000.toInt(), vmi.getColorProperty("Test Color").value)
        assertEquals(100f, vmi.getNumberProperty("Test Nested/Nested Number").value)

        val smVMI = view.controller.stateMachines.first().viewModelInstance!!
        assertEquals(vmi, smVMI)
    }

    @Test
    fun iterate_enums() {
        val enums = view.controller.file?.enums!!
        assertEquals(1, enums.size)
        assertEquals("Test Enum Values", enums[0].name)
        assertEquals(2, enums[0].values.size)
        assertEquals("Value 1", enums[0].values[0])
        assertEquals("Value 2", enums[0].values[1])
    }

    @Test
    fun nested_paths() {
        // Test by accessing each property
        assertEquals(
            100f,
            vmi.getInstanceProperty("Test Nested").getNumberProperty("Nested Number").value
        )

        // Test by path string
        assertEquals(100f, vmi.getNumberProperty("Test Nested/Nested Number").value)
    }

    @Test
    fun nested_paths_with_slash_names() {
        val vm = view.controller.file?.getViewModelByName("Test Slash")!!
        val vmi = vm.createDefaultInstance()
        view.controller.activeArtboard?.viewModelInstance = vmi

        // The property name is "/Nes/ted/", which contains the delimiter character '/'
        assertThrows(ViewModelException::class.java) {
            vmi.getNumberProperty("Test Nested/Nested Number")
        }
    }

    @Test
    fun reassign_nested_instance() {
        assertEquals(100f, vmi.getNumberProperty("Test Nested/Nested Number").value)

        val nestedVM = view.controller.file?.getViewModelByName("Nested VM")!!
        val vmiAlt = nestedVM.createInstanceFromName("Alternate Nested")
        vmi.setInstanceProperty("Test Nested", vmiAlt)

        assertEquals(200f, vmi.getNumberProperty("Test Nested/Nested Number").value)

        assertThrows(ViewModelException::class.java) {
            vmi.setInstanceProperty("Non Existent", vmiAlt)
        }
    }

    @Test
    fun reassign_nested_instance_failure() {
        // The empty VM doesn't share the same properties as the nested VM and so will throw when assigned
        val nestedEmptyVM = view.controller.file?.getViewModelByName("Empty VM")!!
        val vmiBlank = nestedEmptyVM.createBlankInstance()

        assertThrows(ViewModelException::class.java) {
            vmi.setInstanceProperty("Test Nested", vmiBlank)
        }

        // A valid instance assigned to an invalid path will also throw
        val nestedAltVM = view.controller.file?.getViewModelByName("Nested VM")!!
        val vmiAlt = nestedAltVM.createInstanceFromName("Alternate Nested")
        vmi.setInstanceProperty("Test Nested", vmiAlt)

        assertThrows(ViewModelException::class.java) {
            vmi.setInstanceProperty("Test Nested/Non Existent", vmiAlt)
        }
    }

    @Test
    fun reassign_nested_with_alternate_nested() {
        assertEquals(100f, vmi.getNumberProperty("Test Nested/Nested Number").value)

        // Note: This is a different parent instance: "Alternate VM" vs. "Test All",
        // but it has the same type of nested instance, "Nested VM"
        val alternateVM = view.controller.file?.getViewModelByName("Alternate VM")!!
        val alternateVMI = alternateVM.createDefaultInstance()
        val alternateNested = alternateVMI.getInstanceProperty("Test Nested")

        // Transplant a compatible nested instance from an unrelated parent VM
        vmi.setInstanceProperty("Test Nested", alternateNested)

        assertEquals(200f, vmi.getNumberProperty("Test Nested/Nested Number").value)

        // Test if they're sharing the same instance
        vmi.getNumberProperty("Test Nested/Nested Number").value = 300f
        assertEquals(300f, vmi.getNumberProperty("Test Nested/Nested Number").value)
        assertEquals(300f, alternateVMI.getNumberProperty("Test Nested/Nested Number").value)
        // Due to caching, these should be the same instance and so also the same property
        assertEquals(
            vmi.getInstanceProperty("Test Nested"),
            alternateVMI.getInstanceProperty("Test Nested")
        )
        assertEquals(
            vmi.getNumberProperty("Test Nested/Nested Number"),
            alternateVMI.getNumberProperty("Test Nested/Nested Number")
        )
    }

    @Test
    fun state_transition() {
        view.setRiveResource(R.raw.data_bind_test_impl, artboardName = "Test Transitions")

        val vm = view.controller.file?.getViewModelByName("State Transition")!!
        val vmi = vm.createDefaultInstance()

        assertEquals("State 1", view.getTextRunValue("Test State Text"))

        vmi.getTriggerProperty("Transition Trigger").trigger()
        view.play()

        // As the view model instance has not yet been applied to the state machine, the text is unchanged
        assertEquals("State 1", view.getTextRunValue("Test State Text"))

        view.controller.stateMachines.first().viewModelInstance = vmi

        vmi.getTriggerProperty("Transition Trigger").trigger()
        // The state machine has settled, so it needs to be played again
        view.play()

        assertEquals("State 2", view.getTextRunValue("Test State Text"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun simple_property_subscription() = runTest {
        view.controller.activeArtboard?.viewModelInstance = vmi

        val numberProperty = vmi.getNumberProperty("Test Num")

        // Check initial value
        val value = numberProperty.valueFlow.first()
        assertEquals(123f, value)

        // Check value after updating
        numberProperty.value = 456f
        val newValue = numberProperty.valueFlow.first()
        assertEquals(456f, newValue)

        // Check value by observing
        var subscribedValue = 0f
        val subscription = launch {
            numberProperty.valueFlow.collect {
                subscribedValue = it
            }
        }

        numberProperty.value = 789f
        assertEquals(789f, numberProperty.valueFlow.value)

        advanceUntilIdle()
        assertEquals(789f, subscribedValue)

        subscription.cancel()
    }

    @Test
    fun property_subscription() = runTest {
        view.setRiveResource(R.raw.data_bind_test_impl, "Test Observation", autoBind = true)
        val vmi = view.controller.stateMachines.first().viewModelInstance!!

        val numberProperty = vmi.getNumberProperty("Test Num")
        val stringProperty = vmi.getStringProperty("Test String")
        val booleanProperty = vmi.getBooleanProperty("Test Bool")
        val enumProperty = vmi.getEnumProperty("Test Enum")
        val colorProperty = vmi.getColorProperty("Test Color")
        val triggerProperty = vmi.getTriggerProperty("Test Trigger")
        val nestedNumberProperty = vmi.getNumberProperty("Test Nested/Nested Number")

        var subscribedNumber = 0f
        var subscribedString = ""
        var subscribedBoolean = false
        var subscribedEnum = ""
        var subscribedColor = 0xFF000000.toInt()
        var triggered = false
        var subscribedNestedNumber = 0f
        val subscription = launch {
            launch {
                numberProperty.valueFlow.collect {
                    subscribedNumber = it
                }
            }
            launch {
                stringProperty.valueFlow.collect {
                    subscribedString = it
                }
            }
            launch {
                booleanProperty.valueFlow.collect {
                    subscribedBoolean = it
                }
            }
            launch {
                enumProperty.valueFlow.collect {
                    subscribedEnum = it
                }
            }
            launch {
                colorProperty.valueFlow.collect {
                    subscribedColor = it
                }
            }
            launch {
                triggerProperty.valueFlow.collect {
                    triggered = true
                }
            }
            launch {
                nestedNumberProperty.valueFlow.collect {
                    subscribedNestedNumber = it
                }
            }
        }
        // Allow the subscription to start
        // Note: "advance" here refers to the coroutines test clock, not the Rive animation
        advanceUntilIdle()

        // Check initial values
        assertEquals(123f, numberProperty.value)
        assertEquals("World", stringProperty.value)
        assertTrue(booleanProperty.value)
        assertEquals("Value 1", enumProperty.value)
        assertEquals(0xFFFF0000.toInt(), colorProperty.value)
        assertEquals(100f, nestedNumberProperty.value)

        assertEquals(123f, subscribedNumber)
        assertEquals("World", subscribedString)
        assertTrue(subscribedBoolean)
        assertEquals("Value 1", subscribedEnum)
        assertEquals(0xFFFF0000.toInt(), subscribedColor)
        assertTrue(triggered).also { triggered = false }
        assertEquals(100f, subscribedNestedNumber)

        // First state from "Entry"
        view.controller.advance(0f)
        advanceUntilIdle()
        assertEquals(456f, subscribedNumber)
        assertEquals("Moon", subscribedString)
        assertFalse(subscribedBoolean)
        assertEquals("Value 2", subscribedEnum)
        assertEquals(0xFF00FF00.toInt(), subscribedColor)
        assertTrue(triggered).also { triggered = false }
        assertEquals(200f, subscribedNestedNumber)

        // Second state, triggered by a trigger input
        view.controller.fireState("Output", "Advance")

        // Double advance necessary due to the way triggers are processed
        view.controller.advance(0f)
        view.controller.advance(0f)
        advanceUntilIdle()

        assertEquals(789f, subscribedNumber)
        assertEquals("Sun", subscribedString)
        assertTrue(subscribedBoolean)
        assertEquals("Value 1", subscribedEnum)
        assertEquals(0xFF0000FF.toInt(), subscribedColor)
        assertTrue(triggered).also { triggered = false }
        assertEquals(300f, subscribedNestedNumber)

        subscription.cancel()
    }

    @Test
    fun multiple_subscribers() = runTest {
        view.setRiveResource(R.raw.data_bind_test_impl, "Test Observation", autoBind = true)
        val vmi = view.controller.stateMachines.first().viewModelInstance!!

        val numberProperty = vmi.getNumberProperty("Test Num")
        var subscribedNumber = 0f
        val subscription = launch {
            numberProperty.valueFlow.collect {
                subscribedNumber = it
            }
        }

        var secondSubscribedNumber = 0f
        val secondSubscription = launch {
            numberProperty.valueFlow.collect {
                secondSubscribedNumber = it
            }
        }

        // This time we're using the same property but with a different get call
        var thirdSubscribedNumber = 0f
        val sameNumberProperty = vmi.getNumberProperty("Test Num")
        val thirdSubscription = launch {
            sameNumberProperty.valueFlow.collect {
                thirdSubscribedNumber = it
            }
        }

        // Allow the subscription to start
        advanceUntilIdle()

        // Check initial values
        assertEquals(123f, numberProperty.value)
        assertEquals(123f, sameNumberProperty.value)

        assertEquals(123f, subscribedNumber)
        assertEquals(123f, secondSubscribedNumber)
        assertEquals(123f, thirdSubscribedNumber)

        // First state from "Entry"
        view.controller.advance(0f)
        advanceUntilIdle()
        assertEquals(456f, subscribedNumber)
        assertEquals(456f, secondSubscribedNumber)
        assertEquals(456f, thirdSubscribedNumber)

        // Second state, triggered by a trigger input
        view.controller.fireState("Output", "Advance")

        view.controller.advance(0f)
        view.controller.advance(0f)
        advanceUntilIdle()

        assertEquals(789f, subscribedNumber)
        assertEquals(789f, secondSubscribedNumber)
        assertEquals(789f, thirdSubscribedNumber)

        subscription.cancel()
        secondSubscription.cancel()
        thirdSubscription.cancel()
    }

    @Test
    fun single_subscriber_multiple_producers() = runTest {
        view.setRiveResource(R.raw.data_bind_test_impl, "Test Observation", autoBind = true)
        val vmi = view.controller.stateMachines.first().viewModelInstance!!

        val view2 = TestUtils.MockRiveAnimationView(appContext)
        view2.setRiveResource(R.raw.data_bind_test_impl, "Test Observation")
        view2.controller.stateMachines.first().viewModelInstance = vmi

        var subscribedNumber = 0f
        val subscription = launch {
            launch {
                vmi.getNumberProperty("Test Num").valueFlow.collect {
                    subscribedNumber = it
                }
            }
        }
        advanceUntilIdle()

        assertEquals(123f, subscribedNumber)

        // First run `view` through its states
        view.controller.advance(0f)
        advanceUntilIdle()

        assertEquals(456f, subscribedNumber)

        view.controller.fireState("Output", "Advance")
        view.controller.advance(0f)
        view.controller.advance(0f)
        advanceUntilIdle()

        assertEquals(789f, subscribedNumber)

        // Then run `view2` through its states
        view2.controller.advance(0f)
        advanceUntilIdle()

        assertEquals(456f, subscribedNumber)

        view2.controller.fireState("Output", "Advance")
        view2.controller.advance(0f)
        view2.controller.advance(0f)
        advanceUntilIdle()

        assertEquals(789f, subscribedNumber)

        subscription.cancel()
    }

    @Test
    fun fail_when_using_after_dispose() {
        view.mockDetach(destroy = true)

        // Both the ViewModel and ViewModelInstance have been transitively disposed
        // RiveAnimationView -> RiveFileController -> File -> ViewModel -> ViewModelInstance
        assertThrows(RiveException::class.java) { vm.name }
        assertThrows(RiveException::class.java) { vmi.name }
    }

    @Test
    fun move_instance_between_files() {
        val view2 = TestUtils.MockRiveAnimationView(appContext)
        view2.setRiveResource(R.raw.data_bind_test_impl)
        view2.controller.activeArtboard?.viewModelInstance = vmi

        // Similar tests to set_vmi_values, but after transferring
        vmi.getNumberProperty("Test Num").value = 456f

        assertNotEquals("456", view2.getTextRunValue("Test Number Value"))

        view2.play()

        assertEquals("456", view2.getTextRunValue("Test Number Value"))
    }

    @Test
    fun move_instance_between_files_after_deletion_to_artboard() {
        val view2 = TestUtils.MockRiveAnimationView(appContext)
        view2.setRiveResource(R.raw.data_bind_test_impl)

        val transfer = vmi.transfer()
        view.mockDetach(destroy = true)

        view2.controller.activeArtboard?.receiveViewModelInstance(transfer)

        vmi.getNumberProperty("Test Num").value = 456f
        assertNotEquals("456", view2.getTextRunValue("Test Number Value"))
        view2.play()
        assertEquals("456", view2.getTextRunValue("Test Number Value"))
    }

    @Test
    @Ignore("Temporarily disabled due to crashes")
    fun move_instance_between_files_after_deletion_to_artboard_crash() {
        val transfer = vmi.transfer()
        view.mockDetach(destroy = true)

        val view2 = TestUtils.MockRiveAnimationView(appContext)
        view2.setRiveResource(R.raw.data_bind_test_impl)
        view2.controller.activeArtboard?.receiveViewModelInstance(transfer)

        vmi.getNumberProperty("Test Num").value = 456f
        assertNotEquals("456", view2.getTextRunValue("Test Number Value"))
        view2.play()
        assertEquals("456", view2.getTextRunValue("Test Number Value"))
    }

    @Test
    fun move_instance_between_files_after_deletion_to_state_machine() {
        val view2 = TestUtils.MockRiveAnimationView(appContext)
        view2.setRiveResource(R.raw.data_bind_test_impl)

        val transfer = vmi.transfer()
        view.mockDetach(destroy = true)

        view2.controller.stateMachines.first().receiveViewModelInstance(transfer)

        vmi.getNumberProperty("Test Num").value = 456f
        assertNotEquals("456", view2.getTextRunValue("Test Number Value"))
        view2.play()
        assertEquals("456", view2.getTextRunValue("Test Number Value"))
    }

    @Test
    @Ignore("Temporarily disabled due to crashes")
    fun move_instance_between_files_after_deletion_while_observing() = runTest {
        view.setRiveResource(R.raw.data_bind_test_impl, "Test Observation", autoBind = true)
        val vmi = view.controller.stateMachines.first().viewModelInstance!!

        var subscribedNumber = 0f
        val subscription = launch {
            vmi.getNumberProperty("Test Num").valueFlow.collect {
                subscribedNumber = it
            }
        }
        advanceUntilIdle()

        assertEquals(123f, subscribedNumber)

        // Run view through its states
        view.controller.advance(0f)
        advanceUntilIdle()
        assertEquals(456f, subscribedNumber)
        view.controller.fireState("Output", "Advance")
        view.controller.advance(0f)
        view.controller.advance(0f)
        advanceUntilIdle()
        assertEquals(789f, subscribedNumber)

        val transfer = vmi.transfer()
        view.mockDetach(destroy = true) // Crashes with this!

        val view2 = TestUtils.MockRiveAnimationView(appContext)
        view2.setRiveResource(R.raw.data_bind_test_impl, "Test Observation")
        view2.controller.stateMachines.first().receiveViewModelInstance(transfer)

        // Run view2 through its states
        view2.controller.advance(0f)
        advanceUntilIdle()
        assertEquals(456f, subscribedNumber)
        view2.controller.fireState("Output", "Advance")
        view2.controller.advance(0f)
        view2.controller.advance(0f)
        advanceUntilIdle()
        assertEquals(789f, subscribedNumber)

        subscription.cancel()
    }

    @Test
    fun move_instance_between_multiple_files_after_deletion() {
        val view2 = TestUtils.MockRiveAnimationView(appContext)
        val view3 = TestUtils.MockRiveAnimationView(appContext)
        view2.setRiveResource(R.raw.data_bind_test_impl)
        view3.setRiveResource(R.raw.data_bind_test_impl)

        val transfer1 = vmi.transfer()
        val transfer2 = vmi.transfer()

        view.mockDetach(destroy = true)

        view2.controller.activeArtboard?.receiveViewModelInstance(transfer1)
        view3.controller.activeArtboard?.receiveViewModelInstance(transfer2)

        vmi.getNumberProperty("Test Num").value = 456f

        assertNotEquals("456", view2.getTextRunValue("Test Number Value"))
        view2.play()
        assertEquals("456", view2.getTextRunValue("Test Number Value"))

        assertNotEquals("456", view3.getTextRunValue("Test Number Value"))
        view3.play()
        assertEquals("456", view3.getTextRunValue("Test Number Value"))
    }

    @Test
    fun move_instance_between_files_after_deletion_without_transfer() {
        val view2 = TestUtils.MockRiveAnimationView(appContext)
        view2.setRiveResource(R.raw.data_bind_test_impl)

        view.mockDetach(destroy = true)

        // Accessing `cppPointer` after final reference was released
        assertThrows(RiveException::class.java) {
            view2.controller.activeArtboard?.viewModelInstance = vmi
        }
        assertThrows(RiveException::class.java) {
            view2.controller.stateMachines.first().viewModelInstance = vmi
        }
    }

    @Test
    fun dispose() {
        val view2 = TestUtils.MockRiveAnimationView(appContext)
        view2.setRiveResource(R.raw.data_bind_test_impl)

        val transfer = vmi.transfer()

        view.mockDetach(destroy = true)

        transfer.dispose()

        assertEquals(0, vmi.refCount)
        assertThrows(RiveException::class.java) {
            vmi.name
        }

        // Cannot dispose twice
        assertThrows(ViewModelException::class.java) {
            transfer.dispose()
        }

        // Cannot transfer after dispose
        assertThrows(ViewModelException::class.java) {
            vmi.transfer()
        }

        // Cannot receive after dispose
        assertThrows(ViewModelException::class.java) {
            view2.controller.activeArtboard?.receiveViewModelInstance(transfer)
        }
    }

    @Test
    fun transfer_after_delete() {
        view.mockDetach(destroy = true)

        assertThrows(ViewModelException::class.java) {
            vmi.transfer()
        }
    }
}

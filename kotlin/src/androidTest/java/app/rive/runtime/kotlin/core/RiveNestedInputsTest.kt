package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import app.rive.runtime.kotlin.core.errors.StateMachineInputException
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RiveNestedInputsTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context
    private lateinit var view: TestUtils.MockRiveAnimationView


    @Before
    fun init() {
        view = TestUtils.MockRiveAnimationView(appContext)
    }

    @Test
    fun can_set_inputs_at_path_no_errors() {
        UiThreadStatement.runOnUiThread {
            view.setRiveResource(R.raw.nested_inputs_test, artboardName = "Artboard", stateMachineName = "State Machine 1")
            view.play()
            view.setBooleanStateAtPath("bool", true, "nested")
            view.setNumberStateAtPath("number", 1f, "nested")
            view.fireStateAtPath("trigger", "nested")
        }
    }

    @Test
    fun set_incorrect_name_inputs_at_path_throws() {
        UiThreadStatement.runOnUiThread {
            view.setRiveResource(R.raw.nested_inputs_test, artboardName = "Artboard", stateMachineName = "State Machine 1")
            view.play()
            assertThrows(StateMachineInputException::class.java) {
                view.setBooleanStateAtPath("wrongname", true, "nested")
                view.artboardRenderer?.advance(0.16f)
            }
            assertThrows(StateMachineInputException::class.java) {
                view.setNumberStateAtPath("wrongname", 1f, "nested")
                view.artboardRenderer?.advance(0.16f)
            }
            assertThrows(StateMachineInputException::class.java) {
                view.fireStateAtPath("wrongname", "nested")
                view.artboardRenderer?.advance(0.16f)
            }
        }
    }

    @Test
    fun set_incorrect_path_inputs_at_path_throws() {
        UiThreadStatement.runOnUiThread {
            view.setRiveResource(R.raw.nested_inputs_test, artboardName = "Artboard", stateMachineName = "State Machine 1")
            view.play()
            assertThrows(StateMachineInputException::class.java) {
                view.setBooleanStateAtPath("bool", true, "wrongPath")
                view.artboardRenderer?.advance(0.16f)
            }
            assertThrows(StateMachineInputException::class.java) {
                view.setNumberStateAtPath("number", 1f, "wrongPath")
                view.artboardRenderer?.advance(0.16f)
            }
            assertThrows(StateMachineInputException::class.java) {
                view.fireStateAtPath("trigger", "wrongPath")
                view.artboardRenderer?.advance(0.16f)
            }
        }
    }

}
package app.rive

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.runtime.kotlin.test.R
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises [RiveCanvasFocusController] on the real main looper. */
@RunWith(AndroidJUnit4::class)
class RiveCanvasFocusControllerTest : RiveAndroidTest() {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun focusabilityIsResolvedAsSoonAsTheControllerIsCreated() {
        val stateMachine = runBlocking { loadRiveResources(R.raw.focus_nodes_list_order) }
            .stateMachine
        instrumentation.runOnMainSync {
            val controller = RiveCanvasFocusController(
                RiveFocusMode.On,
                stateMachine,
                MutableStateFlow(true),
            )
            try {
                // A host applies focusability right after creating its session.
                assertEquals(RiveFocusability.KeyboardAndTouch, controller.focusability.value)
            } finally {
                controller.close()
            }
        }
    }

    @Test
    fun aFocusNodeReportIsAppliedAfterThePublisherReturns() {
        val stateMachine = runBlocking { loadRiveResources(R.raw.focus_nodes_list_order) }
            .stateMachine
        val hasFocusNodes = MutableStateFlow<Boolean?>(true)
        lateinit var controller: RiveCanvasFocusController
        instrumentation.runOnMainSync {
            controller = RiveCanvasFocusController(RiveFocusMode.On, stateMachine, hasFocusNodes)
        }
        instrumentation.waitForIdleSync()
        try {
            // The worker publishes focus node reports while holding a lock. Applying one inline
            // would dispose the session and run host code inside that lock.
            var focusabilityWhilePublishing: RiveFocusability? = null
            instrumentation.runOnMainSync {
                hasFocusNodes.value = false
                focusabilityWhilePublishing = controller.focusability.value
            }
            instrumentation.waitForIdleSync()

            assertEquals(RiveFocusability.KeyboardAndTouch, focusabilityWhilePublishing)
            assertEquals(RiveFocusability.None, controller.focusability.value)
        } finally {
            instrumentation.runOnMainSync { controller.close() }
        }
    }
}

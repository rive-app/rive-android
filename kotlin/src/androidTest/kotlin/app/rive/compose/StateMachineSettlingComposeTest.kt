package app.rive.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.Fit
import app.rive.Result
import app.rive.Rive
import app.rive.RiveAndroidTest
import app.rive.RiveFileSource
import app.rive.StateMachine
import app.rive.ViewModelInstance
import app.rive.ViewModelSource
import app.rive.rememberViewModelInstanceResult
import app.rive.runtime.kotlin.test.R
import org.junit.Rule
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertFalse
import kotlin.test.Test

@RunWith(AndroidJUnit4::class)
class StateMachineSettlingComposeTest : RiveAndroidTest() {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Verifies that resizing a settled layout-fit artboard ignores a delayed callback from the
     * preceding generation and applies the responsive-layout transition.
     */
    @Test
    fun resize_ignoresSettledCallbackFromEarlierGeneration() {
        // Capture the state and resources created inside composition for test-thread coordination.
        lateinit var portrait: MutableState<Boolean>
        val stateMachine = AtomicReference<StateMachine?>(null)
        val viewModelInstance = AtomicReference<ViewModelInstance?>(null)
        val observedSize = AtomicReference(IntSize.Zero)

        // Render the responsive-layout fixture at its initial square size.
        composeRule.setContent {
            portrait = remember { mutableStateOf(false) }

            // Create the Rive resources once the fixture has loaded.
            val resourcesResult = rememberTestRiveResources(
                source = RiveFileSource.RawRes.from(R.raw.resize_test),
                riveWorker = riveWorker
            )
            val contentResult = resourcesResult.andThen { resources ->
                rememberViewModelInstanceResult(
                    resources.file,
                    ViewModelSource.DefaultForArtboard(resources.artboard).defaultInstance()
                ).map { rememberedViewModelInstance ->
                    SettlingTestContent(resources, rememberedViewModelInstance)
                }
            }
            if (contentResult !is Result.Success) {
                return@setContent
            }

            val (resources, rememberedViewModelInstance) = contentResult.value
            val (file, artboard, rememberedStateMachine) = resources

            // Publish the remembered resources for assertions outside composition.
            stateMachine.set(rememberedStateMachine)
            viewModelInstance.set(rememberedViewModelInstance)

            // Observe the host layout while Rive applies layout-fit artboard dimensions.
            val layoutScaleFactor = LocalDensity.current.density / ARTBOARD_TO_VIEW_RATIO
            Rive(
                file = file,
                modifier = Modifier
                    .width(VIEW_WIDTH_DP.dp)
                    .height(
                        if (portrait.value) {
                            PORTRAIT_HEIGHT_DP.dp
                        } else {
                            SQUARE_HEIGHT_DP.dp
                        }
                    )
                    .onSizeChanged(observedSize::set),
                artboard = artboard,
                stateMachine = rememberedStateMachine,
                viewModelInstance = rememberedViewModelInstance,
                fit = Fit.Layout(layoutScaleFactor)
            )
        }

        try {
            // Wait until composition has created and published the data-binding instance.
            composeRule.waitForIdle()
            composeRule.awaitWithWallClock(
                timeoutMessage = { "View model instance was not created" },
            ) {
                viewModelInstance.get() != null
            }
            val activeViewModelInstance = checkNotNull(viewModelInstance.get())

            // Establish a collapsed, square, and settled baseline before resizing.
            composeRule.awaitProperty(
                propertyPath = "expanded",
                getFlow = activeViewModelInstance::getBooleanFlow,
                expected = false,
            )
            composeRule.awaitWithWallClock(
                timeoutMessage = {
                    "Initial state did not finish: size=${observedSize.get()}, " +
                        "settled=${stateMachine.get()?.settled?.value}"
                },
            ) {
                val size = observedSize.get()
                size.width > 0 &&
                    size.width == size.height &&
                    stateMachine.get()?.settled?.value == true
            }

            // Request the portrait layout while retaining explicit control of Compose frames.
            composeRule.mainClock.autoAdvance = false
            composeRule.runOnUiThread {
                portrait.value = true
            }

            // Advance until resizing has unsettled the state machine and established its new
            // request-ID boundary.
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            // The explicit frame and waitForIdle above perform the resize. Advance only
            // Compose time here so the test stops as soon as the resize effect publishes its
            // unsettled boundary, before yielding long enough for a current settled callback
            // to overtake this deliberately narrow test window.
            composeRule.awaitWithComposeClock(
                timeoutMessage = {
                    "Resize did not finish: size=${observedSize.get()}, " +
                        "settled=${stateMachine.get()?.settled?.value}"
                },
            ) {
                val size = observedSize.get()
                // Density conversion can differ by a pixel after rounding.
                size.height > size.width &&
                    stateMachine.get()?.settled?.value == false
            }
            composeRule.waitForIdle()

            // Verify that a callback from before the resize boundary cannot settle it again.
            composeRule.runOnUiThread {
                val activeStateMachine = checkNotNull(stateMachine.get())
                riveWorker.onStateMachineSettled(
                    Long.MIN_VALUE,
                    activeStateMachine.stateMachineHandle
                )
                assertFalse(activeStateMachine.settled.value)
            }
            composeRule.waitForIdle()

            // Resume automatic frames and verify responsive layout reaches its expanded state.
            composeRule.mainClock.autoAdvance = true

            composeRule.awaitProperty(
                propertyPath = "expanded",
                getFlow = activeViewModelInstance::getBooleanFlow,
                expected = true,
            )

            // Confirm that a current callback is accepted after the expanded state stabilizes.
            composeRule.awaitWithWallClock(
                timeoutMessage = { "State machine did not settle after expanding" },
            ) {
                stateMachine.get()?.settled?.value == true
            }
        } finally {
            // The outer Rive rule keeps polling until Compose disposes its remembered resources.
            composeRule.mainClock.autoAdvance = true
        }
    }

    private companion object {
        private const val VIEW_WIDTH_DP = 250
        private const val SQUARE_HEIGHT_DP = 250
        private const val PORTRAIT_HEIGHT_DP = 500
        private const val ARTBOARD_TO_VIEW_RATIO = 2f
    }
}

private data class SettlingTestContent(
    val resources: TestRiveResources,
    val viewModelInstance: ViewModelInstance,
)

package app.rive.compose

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.Artboard
import app.rive.RiveAndroidTest
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.RiveResourceClosedException
import app.rive.core.loadDefaultRiveResources
import app.rive.rememberStateMachineResult
import app.rive.runtime.kotlin.test.R
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Compose tests for remembered state machines. */
@RunWith(AndroidJUnit4::class)
class StateMachineComposeTest : RiveAndroidTest() {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Verifies an artboard change synchronously resets a remembered state-machine result. */
    @Test
    fun artboardChange_reportsLoadingSynchronously() {
        val owners = runBlocking {
            List(2) { riveWorker.loadDefaultRiveResources(R.raw.empty) }
        }

        try {
            composeRule.assertResultResetsToLoading(
                resourceName = "StateMachine",
                withNativePollingPaused = ::withRiveWorkerPollingPaused,
                result = { useReplacement ->
                    rememberStateMachineResult(owners[useReplacement.toIndex()].artboard)
                },
                assertClosed = { stateMachine ->
                    assertFailsWith<RiveResourceClosedException> { stateMachine.checkOpen() }
                },
            )
        } finally {
            owners.asReversed().forEach { it.close() }
        }
    }

    /** Verifies a name change synchronously resets a remembered state-machine result. */
    @Test
    fun nameChange_reportsLoadingSynchronously() {
        val (file, artboard) = runBlocking {
            val loadedFile = RiveFile.load(
                RiveFileSource.RawRes(R.raw.multiple_state_machines, context.resources),
                riveWorker,
            )
            try {
                loadedFile to Artboard.create(loadedFile)
            } catch (failure: Throwable) {
                loadedFile.close()
                throw failure
            }
        }
        val names = listOf("one", "four")

        try {
            composeRule.assertResultResetsToLoading(
                resourceName = "StateMachine",
                withNativePollingPaused = ::withRiveWorkerPollingPaused,
                result = { useReplacement ->
                    rememberStateMachineResult(artboard, names[useReplacement.toIndex()])
                },
                assertClosed = { stateMachine ->
                    assertFailsWith<RiveResourceClosedException> { stateMachine.checkOpen() }
                },
            )
        } finally {
            try {
                artboard.close()
            } finally {
                file.close()
            }
        }
    }
}

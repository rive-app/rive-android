package app.rive.compose

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.RiveAndroidTest
import app.rive.RiveFileSource
import app.rive.RiveResourceClosedException
import app.rive.rememberArtboardResult
import app.rive.rememberRiveFile
import app.rive.rememberStateMachineResult
import app.rive.runtime.kotlin.test.R
import org.junit.Rule
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Compose tests for dependent resource chains. */
@RunWith(AndroidJUnit4::class)
class ResourceChainingComposeTest : RiveAndroidTest() {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Verifies a file-source change resets a file-artboard-state-machine chain. */
    @Test
    fun fileSourceChange_reportsLoadingSynchronously() {
        val bytes = context.resources.openRawResource(R.raw.empty).use { it.readBytes() }
        val sources = listOf(
            RiveFileSource.Bytes(bytes),
            RiveFileSource.Bytes(bytes.copyOf()),
        )

        composeRule.assertResultResetsToLoading(
            resourceName = "resource chain",
            withNativePollingPaused = ::withRiveWorkerPollingPaused,
            result = { useReplacement ->
                rememberRiveFile(sources[useReplacement.toIndex()], riveWorker).andThen { file ->
                    rememberArtboardResult(file).andThen { artboard ->
                        rememberStateMachineResult(artboard).map { stateMachine ->
                            TestRiveResources(file, artboard, stateMachine)
                        }
                    }
                }
            },
            assertClosed = { resources ->
                assertFailsWith<RiveResourceClosedException> { resources.stateMachine.checkOpen() }
                assertFailsWith<RiveResourceClosedException> { resources.artboard.checkOpen() }
                assertFailsWith<RiveResourceClosedException> { resources.file.checkOpen() }
            },
        )
    }
}

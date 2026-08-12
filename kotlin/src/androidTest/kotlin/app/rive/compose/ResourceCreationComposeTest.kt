package app.rive.compose

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.Result
import app.rive.RiveArtboardException
import app.rive.RiveAudioException
import app.rive.RiveAndroidTest
import app.rive.RiveFileException
import app.rive.RiveFileSource
import app.rive.RiveFontException
import app.rive.RiveImageException
import app.rive.RiveResourceClosedException
import app.rive.ViewModelInstance
import app.rive.ViewModelSource
import app.rive.rememberAudio
import app.rive.rememberFont
import app.rive.rememberImage
import app.rive.rememberViewModelInstanceResult
import app.rive.runtime.kotlin.test.R
import org.junit.Rule
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** Exercises resource creation through the real Compose and JNI path. */
@RunWith(AndroidJUnit4::class)
class ResourceCreationComposeTest : RiveAndroidTest() {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Verifies chained resources succeed and are closed when their composition is removed. */
    @Test
    fun chainedResources_closeWhenRemovedFromComposition() {
        lateinit var showContent: MutableState<Boolean>
        val observedResult = AtomicReference<Result<ComposeResources>>(Result.Loading)

        composeRule.setContent {
            showContent = remember { mutableStateOf(true) }
            if (showContent.value) {
                val resourcesResult = rememberTestRiveResources(
                    RiveFileSource.RawRes.from(R.raw.empty),
                    riveWorker
                )
                observedResult.set(
                    resourcesResult.andThen { resources ->
                        rememberViewModelInstanceResult(
                            resources.file,
                            ViewModelSource.DefaultForArtboard(resources.artboard).defaultInstance()
                        ).map { vmi ->
                            ComposeResources(resources, vmi)
                        }
                    }
                )
            }
        }

        composeRule.awaitWithWallClock(
            timeoutMessage = { "Compose resources were not created" }
        ) {
            observedResult.get() is Result.Success
        }
        val resources = assertIs<Result.Success<ComposeResources>>(observedResult.get())
            .value

        composeRule.runOnUiThread { showContent.value = false }
        composeRule.waitForIdle()

        assertFailsWith<RiveResourceClosedException> { resources.resources.file.checkOpen() }
        assertFailsWith<RiveResourceClosedException> { resources.resources.artboard.checkOpen() }
        assertFailsWith<RiveResourceClosedException> {
            resources.resources.stateMachine.checkOpen()
        }
        assertFailsWith<RiveResourceClosedException> { resources.vmi.checkOpen() }
    }

    /** Verifies an invalid artboard name becomes a Compose error result through JNI. */
    @Test
    fun missingArtboard_reportsError() {
        val error = assertReportsError("Missing artboard did not report an error") {
            rememberTestRiveResources(
                RiveFileSource.RawRes.from(R.raw.empty),
                riveWorker,
                artboardName = "Missing Artboard",
            )
        }

        assertIs<RiveFileException>(error)
    }

    /** Verifies an invalid state machine name becomes a Compose error result through JNI. */
    @Test
    fun missingStateMachine_reportsError() {
        val error = assertReportsError("Missing state machine did not report an error") {
            rememberTestRiveResources(
                RiveFileSource.RawRes.from(R.raw.empty),
                riveWorker,
                stateMachineName = "Missing State Machine",
            )
        }

        assertIs<RiveArtboardException>(error)
    }

    /** Verifies an invalid VMI source becomes a Compose error result through JNI. */
    @Test
    fun missingViewModelInstanceSource_reportsError() {
        val error = assertReportsError("Missing VMI source did not report an error") {
            rememberTestRiveResources(
                RiveFileSource.RawRes.from(R.raw.empty),
                riveWorker,
            ).andThen { resources ->
                rememberViewModelInstanceResult(
                    resources.file,
                    ViewModelSource.Named("Missing View Model").blankInstance(),
                )
            }
        }

        assertIs<RiveFileException>(error)
    }

    /** Verifies invalid image bytes become a Compose error result through JNI. */
    @Test
    fun invalidImage_reportsError() {
        val error = assertReportsError("Invalid image did not report an error") {
            rememberImage(riveWorker, remember { byteArrayOf() })
        }

        assertIs<RiveImageException>(error)
    }

    /** Verifies invalid audio bytes become a Compose error result through JNI. */
    @Test
    fun invalidAudio_reportsError() {
        val error = assertReportsError("Invalid audio did not report an error") {
            rememberAudio(riveWorker, remember { byteArrayOf() })
        }

        assertIs<RiveAudioException>(error)
    }

    /** Verifies invalid font bytes become a Compose error result through JNI. */
    @Test
    fun invalidFont_reportsError() {
        val error = assertReportsError("Invalid font did not report an error") {
            rememberFont(riveWorker, remember { byteArrayOf() })
        }

        assertIs<RiveFontException>(error)
    }

    /**
     * Composes [result] and waits for it to report an error.
     *
     * @param timeoutMessage The assertion message used if no error arrives.
     * @param result The resource result produced by composition.
     * @return The error reported by [result].
     */
    private fun <T> assertReportsError(
        timeoutMessage: String,
        result: @Composable () -> Result<T>,
    ): Throwable {
        val observedResult = AtomicReference<Result<T>>(Result.Loading)

        composeRule.setContent {
            observedResult.set(result())
        }
        composeRule.awaitWithWallClock(
            timeoutMessage = { timeoutMessage }
        ) {
            observedResult.get() is Result.Error
        }

        return assertIs<Result.Error>(observedResult.get()).throwable
    }
}

private data class ComposeResources(
    val resources: TestRiveResources,
    val vmi: ViewModelInstance,
)

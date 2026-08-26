package app.rive.compose

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.RiveAndroidTest
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.RiveResourceClosedException
import app.rive.rememberArtboardResult
import app.rive.runtime.kotlin.test.R
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Compose tests for remembered artboards. */
@RunWith(AndroidJUnit4::class)
class ArtboardComposeTest : RiveAndroidTest() {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Verifies a file change synchronously resets a remembered artboard result. */
    @Test
    fun fileChange_reportsLoadingSynchronously() {
        val files = runBlocking {
            List(2) {
                RiveFile.load(
                    RiveFileSource.RawRes(R.raw.empty, context.resources),
                    riveWorker,
                )
            }
        }

        try {
            composeRule.assertResultResetsToLoading(
                resourceName = "Artboard",
                withNativePollingPaused = ::withRiveWorkerPollingPaused,
                result = { useReplacement ->
                    rememberArtboardResult(files[useReplacement.toIndex()])
                },
                assertClosed = { artboard ->
                    assertFailsWith<RiveResourceClosedException> { artboard.checkOpen() }
                },
            )
        } finally {
            files.asReversed().forEach(RiveFile::close)
        }
    }

    /** Verifies a name change synchronously resets a remembered artboard result. */
    @Test
    fun nameChange_reportsLoadingSynchronously() {
        val file = runBlocking {
            RiveFile.load(
                RiveFileSource.RawRes(R.raw.multipleartboards, context.resources),
                riveWorker,
            )
        }
        val names = listOf("artboard1", "artboard2")

        try {
            composeRule.assertResultResetsToLoading(
                resourceName = "Artboard",
                withNativePollingPaused = ::withRiveWorkerPollingPaused,
                result = { useReplacement ->
                    rememberArtboardResult(file, names[useReplacement.toIndex()])
                },
                assertClosed = { artboard ->
                    assertFailsWith<RiveResourceClosedException> { artboard.checkOpen() }
                },
            )
        } finally {
            file.close()
        }
    }
}

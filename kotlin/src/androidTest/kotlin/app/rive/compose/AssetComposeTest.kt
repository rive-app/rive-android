package app.rive.compose

import androidx.activity.ComponentActivity
import androidx.annotation.RawRes
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.RiveAndroidTest
import app.rive.RiveResourceClosedException
import app.rive.core.CommandQueue
import app.rive.core.CommandQueuePoller
import app.rive.core.assertDisposed
import app.rive.rememberAudio
import app.rive.rememberFont
import app.rive.rememberImage
import app.rive.rememberRegisteredAudio
import app.rive.rememberRegisteredFont
import app.rive.rememberRegisteredImage
import app.rive.runtime.kotlin.test.R
import org.junit.Rule
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Compose tests for remembered assets. */
@RunWith(AndroidJUnit4::class)
class AssetComposeTest : RiveAndroidTest() {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Verifies changing image bytes synchronously resets a remembered image result. */
    @Test
    fun imageBytesChange_reportsLoadingSynchronously() {
        val bytes = replacementBytes(R.raw.eve)

        composeRule.assertResultResetsToLoading(
            resourceName = "ImageAsset",
            withNativePollingPaused = ::withRiveWorkerPollingPaused,
            result = { useReplacement ->
                rememberImage(riveWorker, bytes[useReplacement.toIndex()])
            },
            assertClosed = { image ->
                assertFailsWith<RiveResourceClosedException> { image.checkOpen() }
            },
        )
    }

    /** Verifies changing workers synchronously resets a remembered asset result. */
    @Test
    fun imageWorkerChange_reportsLoadingSynchronously() {
        val workers = listOf(CommandQueue(), CommandQueue())
        val poller = CommandQueuePoller(workers)
        val bytes = readRawBytes(R.raw.eve)

        try {
            composeRule.assertResultResetsToLoading(
                resourceName = "ImageAsset",
                withNativePollingPaused = poller::withPollingPaused,
                result = { useReplacement ->
                    rememberImage(workers[useReplacement.toIndex()], bytes)
                },
                assertClosed = { image ->
                    assertFailsWith<RiveResourceClosedException> { image.checkOpen() }
                },
            )
        } finally {
            poller.close()
            workers.forEach { worker ->
                if (!worker.isDisposed) {
                    worker.release(javaClass.simpleName, "Test cleanup")
                }
                assertDisposed(worker)
            }
        }
    }

    /** Verifies changing audio bytes synchronously resets a remembered audio result. */
    @Test
    fun audioBytesChange_reportsLoadingSynchronously() {
        val bytes = replacementBytes(R.raw.table)

        composeRule.assertResultResetsToLoading(
            resourceName = "AudioAsset",
            withNativePollingPaused = ::withRiveWorkerPollingPaused,
            result = { useReplacement ->
                rememberAudio(riveWorker, bytes[useReplacement.toIndex()])
            },
            assertClosed = { audio ->
                assertFailsWith<RiveResourceClosedException> { audio.checkOpen() }
            },
        )
    }

    /** Verifies changing font bytes synchronously resets a remembered font result. */
    @Test
    fun fontBytesChange_reportsLoadingSynchronously() {
        val bytes = replacementBytes(R.raw.font)

        composeRule.assertResultResetsToLoading(
            resourceName = "FontAsset",
            withNativePollingPaused = ::withRiveWorkerPollingPaused,
            result = { useReplacement ->
                rememberFont(riveWorker, bytes[useReplacement.toIndex()])
            },
            assertClosed = { font ->
                assertFailsWith<RiveResourceClosedException> { font.checkOpen() }
            },
        )
    }

    /** Verifies changing a registration key synchronously resets a registered asset result. */
    @Test
    fun registeredImageKeyChange_reportsLoadingSynchronously() {
        val bytes = readRawBytes(R.raw.eve)
        val keys = listOf("initial-image", "replacement-image")

        composeRule.assertResultResetsToLoading(
            resourceName = "registered ImageAsset",
            withNativePollingPaused = ::withRiveWorkerPollingPaused,
            result = { useReplacement ->
                rememberRegisteredImage(
                    riveWorker,
                    keys[useReplacement.toIndex()],
                    bytes,
                )
            },
            assertClosed = { image ->
                assertFailsWith<RiveResourceClosedException> { image.checkOpen() }
            },
        )
    }

    /** Verifies changing a registration key resets a registered audio result. */
    @Test
    fun registeredAudioKeyChange_reportsLoadingSynchronously() {
        val bytes = readRawBytes(R.raw.table)
        val keys = listOf("initial-audio", "replacement-audio")

        composeRule.assertResultResetsToLoading(
            resourceName = "registered AudioAsset",
            withNativePollingPaused = ::withRiveWorkerPollingPaused,
            result = { useReplacement ->
                rememberRegisteredAudio(
                    riveWorker,
                    keys[useReplacement.toIndex()],
                    bytes,
                )
            },
            assertClosed = { audio ->
                assertFailsWith<RiveResourceClosedException> { audio.checkOpen() }
            },
        )
    }

    /** Verifies changing a registration key resets a registered font result. */
    @Test
    fun registeredFontKeyChange_reportsLoadingSynchronously() {
        val bytes = readRawBytes(R.raw.font)
        val keys = listOf("initial-font", "replacement-font")

        composeRule.assertResultResetsToLoading(
            resourceName = "registered FontAsset",
            withNativePollingPaused = ::withRiveWorkerPollingPaused,
            result = { useReplacement ->
                rememberRegisteredFont(
                    riveWorker,
                    keys[useReplacement.toIndex()],
                    bytes,
                )
            },
            assertClosed = { font ->
                assertFailsWith<RiveResourceClosedException> { font.checkOpen() }
            },
        )
    }

    /**
     * Returns distinct byte-array identities containing the same valid encoded resource.
     *
     * @param rawResourceId The encoded image, audio, or font resource to read.
     * @return Initial and replacement byte arrays for a Compose-key transition.
     */
    private fun replacementBytes(@RawRes rawResourceId: Int): List<ByteArray> {
        val bytes = readRawBytes(rawResourceId)
        return listOf(bytes, bytes.copyOf())
    }

    /**
     * Reads an encoded Android raw resource.
     *
     * @param rawResourceId The resource to read.
     * @return The complete resource contents.
     */
    private fun readRawBytes(@RawRes rawResourceId: Int): ByteArray =
        context.resources.openRawResource(rawResourceId).use { it.readBytes() }
}

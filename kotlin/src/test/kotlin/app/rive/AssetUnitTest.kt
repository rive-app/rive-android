@file:Suppress("DEPRECATION")

package app.rive

import app.rive.core.AudioHandle
import app.rive.core.CommandQueue
import app.rive.core.FontHandle
import app.rive.core.ImageHandle
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.clearMocks
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import kotlin.coroutines.cancellation.CancellationException

private const val TEST_IMAGE_HANDLE = 101L
private const val TEST_AUDIO_HANDLE = 102L
private const val TEST_FONT_HANDLE = 103L

class AssetUnitTest : FunSpec({
    test("Image decode preserves its typed exception in the result") {
        val worker = mockk<CommandQueue>(relaxed = true)
        coEvery { worker.decodeImage(any()) } throws RiveImageException("Invalid image")

        val result = ImageAsset.fromBytes(worker, byteArrayOf())

        result.shouldBeInstanceOf<Result.Error>()
            .throwable.shouldBeInstanceOf<RiveImageException>()
        verify(exactly = 1) { worker.acquire(any()) }
        verify(exactly = 1) { worker.release(any(), "Decode error") }
    }

    test("Audio decode preserves its typed exception in the result") {
        val worker = mockk<CommandQueue>(relaxed = true)
        coEvery { worker.decodeAudio(any()) } throws RiveAudioException("Invalid audio")

        val result = AudioAsset.fromBytes(worker, byteArrayOf())

        result.shouldBeInstanceOf<Result.Error>()
            .throwable.shouldBeInstanceOf<RiveAudioException>()
        verify(exactly = 1) { worker.acquire(any()) }
        verify(exactly = 1) { worker.release(any(), "Decode error") }
    }

    test("Font decode preserves its typed exception in the result") {
        val worker = mockk<CommandQueue>(relaxed = true)
        coEvery { worker.decodeFont(any()) } throws RiveFontException("Invalid font")

        val result = FontAsset.fromBytes(worker, byteArrayOf())

        result.shouldBeInstanceOf<Result.Error>()
            .throwable.shouldBeInstanceOf<RiveFontException>()
        verify(exactly = 1) { worker.acquire(any()) }
        verify(exactly = 1) { worker.release(any(), "Decode error") }
    }

    // Image decoding represents cancellation cleanup in the shared Asset.fromBytes implementation.
    test("Asset decode cancellation releases its worker reference and propagates") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val cancellation = CancellationException("Cancelled asset decode")
        coEvery { worker.decodeImage(any()) } throws cancellation

        shouldThrow<CancellationException> {
            ImageAsset.fromBytes(worker, byteArrayOf())
        } shouldBe cancellation

        verify(exactly = 1) { worker.acquire(any()) }
        verify(exactly = 1) { worker.release(any(), "Cancellation") }
    }

    assetReferenceCases.forEach { case ->
        test("${case.name} direct construction owns one worker reference") {
            val worker = mockk<CommandQueue>(relaxed = true)
            val asset = case.construct(worker)

            verify(exactly = 1) { worker.acquire(any()) }
            verify(exactly = 0) { worker.release(any(), any()) }

            asset.close()

            verify(exactly = 1) { worker.release(any(), "Asset closed") }
        }

        test("${case.name} factory transfers its worker reference to the asset") {
            val worker = mockk<CommandQueue>(relaxed = true)
            val asset = case.fromBytes(worker)

            verify(exactly = 1) { worker.acquire(any()) }
            verify(exactly = 0) { worker.release(any(), any()) }

            asset.close()

            verify(exactly = 1) { worker.release(any(), "Asset closed") }
        }
    }

    closedAssetCases.forEach { case ->
        test("${case.name} registration throws after close without using the worker") {
            val worker = mockk<CommandQueue>(relaxed = true)
            val asset = case.create(worker).also { it.close() }
            clearMocks(worker, answers = false, recordedCalls = true)

            shouldThrow<RiveResourceClosedException> {
                asset.register("Test Key")
            }.message shouldContain case.handle.toString()

            confirmVerified(worker)
        }

        test("${case.name} unregister remains available after close") {
            val worker = mockk<CommandQueue>(relaxed = true)
            val asset = case.create(worker).also { it.close() }
            clearMocks(worker, answers = false, recordedCalls = true)

            asset.unregister("Test Key")

            case.verifyUnregistered(worker, "Test Key")
            confirmVerified(worker)
        }

        test("${case.name} rejects a different owning worker") {
            val worker = mockk<CommandQueue>(relaxed = true)
            val foreignWorker = mockk<CommandQueue>(relaxed = true)
            val asset = case.create(worker)

            asset.requireOwnedBy(worker)
            shouldThrow<RiveIncompatibleResourceException> {
                asset.requireOwnedBy(foreignWorker)
            }.message shouldContain case.handle.toString()
        }
    }
})

private data class ClosedAssetCase(
    val name: String,
    val handle: Long,
    val create: (CommandQueue) -> Asset<*>,
    val verifyUnregistered: (CommandQueue, String) -> Unit,
)

private val closedAssetCases = listOf(
    ClosedAssetCase(
        name = "Image asset",
        handle = TEST_IMAGE_HANDLE,
        create = { worker -> ImageAsset(ImageHandle(TEST_IMAGE_HANDLE), worker) },
        verifyUnregistered = { worker, key ->
            verify(exactly = 1) { worker.unregisterImage(key) }
        },
    ),
    ClosedAssetCase(
        name = "Audio asset",
        handle = TEST_AUDIO_HANDLE,
        create = { worker -> AudioAsset(AudioHandle(TEST_AUDIO_HANDLE), worker) },
        verifyUnregistered = { worker, key ->
            verify(exactly = 1) { worker.unregisterAudio(key) }
        },
    ),
    ClosedAssetCase(
        name = "Font asset",
        handle = TEST_FONT_HANDLE,
        create = { worker -> FontAsset(FontHandle(TEST_FONT_HANDLE), worker) },
        verifyUnregistered = { worker, key ->
            verify(exactly = 1) { worker.unregisterFont(key) }
        },
    ),
)

private data class AssetReferenceCase(
    val name: String,
    val construct: (CommandQueue) -> Asset<*>,
    val fromBytes: suspend (CommandQueue) -> Asset<*>,
)

private val assetReferenceCases = listOf(
    AssetReferenceCase(
        name = "Image asset",
        construct = { worker -> ImageAsset(ImageHandle(TEST_IMAGE_HANDLE), worker) },
        fromBytes = { worker ->
            coEvery { worker.decodeImage(any()) } returns ImageHandle(TEST_IMAGE_HANDLE)
            ImageAsset.fromBytes(worker, byteArrayOf())
                .shouldBeInstanceOf<Result.Success<ImageAsset>>()
                .value
        },
    ),
    AssetReferenceCase(
        name = "Audio asset",
        construct = { worker -> AudioAsset(AudioHandle(TEST_AUDIO_HANDLE), worker) },
        fromBytes = { worker ->
            coEvery { worker.decodeAudio(any()) } returns AudioHandle(TEST_AUDIO_HANDLE)
            AudioAsset.fromBytes(worker, byteArrayOf())
                .shouldBeInstanceOf<Result.Success<AudioAsset>>()
                .value
        },
    ),
    AssetReferenceCase(
        name = "Font asset",
        construct = { worker -> FontAsset(FontHandle(TEST_FONT_HANDLE), worker) },
        fromBytes = { worker ->
            coEvery { worker.decodeFont(any()) } returns FontHandle(TEST_FONT_HANDLE)
            FontAsset.fromBytes(worker, byteArrayOf())
                .shouldBeInstanceOf<Result.Success<FontAsset>>()
                .value
        },
    ),
)

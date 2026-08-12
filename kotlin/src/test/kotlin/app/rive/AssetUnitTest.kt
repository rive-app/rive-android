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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.clearMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import kotlin.coroutines.cancellation.CancellationException

private const val TEST_IMAGE_HANDLE = 101L
private const val TEST_AUDIO_HANDLE = 102L
private const val TEST_FONT_HANDLE = 103L

class AssetUnitTest : FunSpec({
    assetFactoryCases.forEach { case ->
        test("${case.name} factory returns a decoded asset") {
            val worker = mockk<CommandQueue>(relaxed = true)
            case.stubSuccess(worker)

            val asset = case.create(worker)

            asset.handle shouldBe case.handle
            verify(exactly = 1) { worker.acquire(any()) }
            verify(exactly = 0) { worker.release(any(), any()) }
            asset.close()
        }

        test("${case.name} factory propagates decoding failure") {
            val worker = mockk<CommandQueue>(relaxed = true)
            val error = case.createFailure()
            case.stubFailure(worker, error)

            shouldThrow<Exception> {
                case.create(worker)
            } shouldBe error

            verify(exactly = 1) { worker.acquire(any()) }
            verify(exactly = 1) { worker.release(any(), "Decode error") }
        }

        test("${case.name} factory propagates cancellation") {
            val worker = mockk<CommandQueue>(relaxed = true)
            val cancellation = CancellationException("Cancelled ${case.name.lowercase()} decode")
            case.stubFailure(worker, cancellation)

            shouldThrow<CancellationException> {
                case.create(worker)
            } shouldBe cancellation

            verify(exactly = 1) { worker.acquire(any()) }
            verify(exactly = 1) { worker.release(any(), "Cancellation") }
        }
    }

    test("Asset factory propagates worker acquisition failure without releasing") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val expected = RiveResourceClosedException("RiveWorker is disposed")
        every { worker.acquire(any()) } throws expected

        shouldThrow<RiveResourceClosedException> {
            ImageAsset.create(worker, byteArrayOf())
        } shouldBe expected
        verify(exactly = 1) { worker.acquire(any()) }
        verify(exactly = 0) { worker.release(any(), any()) }
        coVerify(exactly = 0) { worker.decodeImage(any()) }
    }

    test("Asset factory deletes a decoded handle when construction fails") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val ops = mockk<AssetOps<ImageHandle, ImageAsset>>()
        val handle = ImageHandle(TEST_IMAGE_HANDLE)
        val expected = IllegalStateException("Construction failed")
        every { ops.tag } returns "Test/Asset"
        every { ops.label } returns "test asset"
        coEvery { ops.decode(worker, any()) } returns handle
        every { ops.construct(handle, worker) } throws expected
        every { ops.delete(worker, handle) } just runs

        shouldThrow<IllegalStateException> {
            Asset.createAsset(ops, worker, byteArrayOf())
        } shouldBe expected

        verifyOrder {
            ops.delete(worker, handle)
            worker.release("Test/Asset", "Decode error")
        }
    }

    test("Deprecated asset factory maps decode failure to an error result") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val expected = RiveImageException("Invalid image")
        coEvery { worker.decodeImage(any()) } throws expected

        val result = ImageAsset.fromBytes(worker, byteArrayOf())

        result shouldBe Result.Error(expected)
        verify(exactly = 1) { worker.acquire(any()) }
        verify(exactly = 1) { worker.release(any(), "Decode error") }
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
            val asset = case.create(worker)

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

private data class AssetFactoryCase(
    val name: String,
    val handle: Any,
    val create: suspend (CommandQueue) -> Asset<*>,
    val createFailure: () -> Exception,
    val stubSuccess: (CommandQueue) -> Unit,
    val stubFailure: (CommandQueue, Throwable) -> Unit,
)

private val assetFactoryCases = listOf(
    AssetFactoryCase(
        name = "Image asset",
        handle = ImageHandle(TEST_IMAGE_HANDLE),
        create = { worker -> ImageAsset.create(worker, byteArrayOf()) },
        createFailure = { RiveImageException("Invalid image") },
        stubSuccess = { worker ->
            coEvery { worker.decodeImage(any()) } returns ImageHandle(TEST_IMAGE_HANDLE)
        },
        stubFailure = { worker, error ->
            coEvery { worker.decodeImage(any()) } throws error
        },
    ),
    AssetFactoryCase(
        name = "Audio asset",
        handle = AudioHandle(TEST_AUDIO_HANDLE),
        create = { worker -> AudioAsset.create(worker, byteArrayOf()) },
        createFailure = { RiveAudioException("Invalid audio") },
        stubSuccess = { worker ->
            coEvery { worker.decodeAudio(any()) } returns AudioHandle(TEST_AUDIO_HANDLE)
        },
        stubFailure = { worker, error ->
            coEvery { worker.decodeAudio(any()) } throws error
        },
    ),
    AssetFactoryCase(
        name = "Font asset",
        handle = FontHandle(TEST_FONT_HANDLE),
        create = { worker -> FontAsset.create(worker, byteArrayOf()) },
        createFailure = { RiveFontException("Invalid font") },
        stubSuccess = { worker ->
            coEvery { worker.decodeFont(any()) } returns FontHandle(TEST_FONT_HANDLE)
        },
        stubFailure = { worker, error ->
            coEvery { worker.decodeFont(any()) } throws error
        },
    ),
)

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
    val create: suspend (CommandQueue) -> Asset<*>,
)

private val assetReferenceCases = listOf(
    AssetReferenceCase(
        name = "Image asset",
        construct = { worker -> ImageAsset(ImageHandle(TEST_IMAGE_HANDLE), worker) },
        create = { worker ->
            coEvery { worker.decodeImage(any()) } returns ImageHandle(TEST_IMAGE_HANDLE)
            ImageAsset.create(worker, byteArrayOf())
        },
    ),
    AssetReferenceCase(
        name = "Audio asset",
        construct = { worker -> AudioAsset(AudioHandle(TEST_AUDIO_HANDLE), worker) },
        create = { worker ->
            coEvery { worker.decodeAudio(any()) } returns AudioHandle(TEST_AUDIO_HANDLE)
            AudioAsset.create(worker, byteArrayOf())
        },
    ),
    AssetReferenceCase(
        name = "Font asset",
        construct = { worker -> FontAsset(FontHandle(TEST_FONT_HANDLE), worker) },
        create = { worker ->
            coEvery { worker.decodeFont(any()) } returns FontHandle(TEST_FONT_HANDLE)
            FontAsset.create(worker, byteArrayOf())
        },
    ),
)

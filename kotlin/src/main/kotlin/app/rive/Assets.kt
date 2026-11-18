package app.rive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key as compositionKey
import androidx.compose.runtime.produceState
import app.rive.core.AudioHandle
import app.rive.core.BlobHandle
import app.rive.core.CheckableAutoCloseable
import app.rive.core.CloseOnce
import app.rive.core.FontHandle
import app.rive.core.ImageHandle
import app.rive.core.RiveWorker
import kotlin.coroutines.cancellation.CancellationException

private const val IMAGE_TAG = "Rive/Image"
private const val AUDIO_TAG = "Rive/Audio"
private const val FONT_TAG = "Rive/Font"
private const val BLOB_TAG = "Rive/Blob"

/**
 * A worker reference already acquired for an asset to own.
 *
 * This distinguishes factory construction, which transfers the reference acquired before
 * decoding, from deprecated raw-handle construction, which must acquire its own reference.
 * Remove this wrapper with the raw-handle constructors in 12.0.
 *
 * @param worker The acquired worker reference.
 */
private class AcquiredWorkerReference(val worker: RiveWorker)

/**
 * Acquires the worker reference owned by a directly constructed asset.
 *
 * This compatibility helper exists only for the deprecated raw-handle constructors. Remove it
 * with those constructors in 12.0.
 *
 * @param tag The asset-specific source used for reference-count logging.
 * @return The acquired worker reference for the asset to own.
 * @throws RiveResourceClosedException If this worker has been disposed.
 */
@Throws(RiveResourceClosedException::class)
private fun RiveWorker.acquireForAsset(tag: String): AcquiredWorkerReference {
    acquire(tag)
    return AcquiredWorkerReference(this)
}

/** Operations for managing assets of type [A] with handle type [H]. */
internal interface AssetOps<H, A : Asset<H>> {
    /** The tag used for logging purposes. */
    val tag: String

    /** A label for the asset type, used in logging. */
    val label: String

    /** Decode the asset from the given byte array on the provided Rive worker. */
    suspend fun decode(worker: RiveWorker, bytes: ByteArray): H

    /** Delete the asset from the provided Rive worker. */
    fun delete(worker: RiveWorker, handle: H)

    /** Construct the asset by taking ownership of the worker reference acquired by the factory. */
    fun construct(handle: H, worker: RiveWorker): A
}

/** Native operations for assets that can fulfill referenced assets in a Rive file. */
internal interface RegisterableAssetOps<H, A : Asset<H>> : AssetOps<H, A> {
    /** Registers the asset under the given key on its worker. */
    fun register(worker: RiveWorker, key: String, handle: H)

    /** Removes the key from the worker's referenced asset registry. */
    fun unregister(worker: RiveWorker, key: String)
}

/**
 * Base class for assets managed by a [RiveWorker].
 *
 * Uses [ops] to perform operations specific to the asset type.
 *
 * Each asset holds a reference to its [RiveWorker]. When creating assets manually, call [close]
 * when you no longer need the asset; releasing the worker alone is not enough to purge asset or
 * worker memory while the asset remains open.
 *
 * @param handle The handle to the asset on the command server.
 * @param riveWorker The Rive worker that owns the asset.
 * @param ops The operations for managing the asset type.
 */
sealed class Asset<H>(
    val handle: H,
    protected val riveWorker: RiveWorker,
    private val ops: AssetOps<H, out Asset<H>>,
) : CheckableAutoCloseable {
    private val closer = CloseOnce("$handle") {
        RiveLog.d(ops.tag) { "Deleting ${ops.label} with handle: $handle" }
        ops.delete(riveWorker, handle)

        riveWorker.release(ops.tag, "Asset closed")
    }

    /**
     * Closes this asset and schedules deletion on its Rive worker.
     *
     * Repeated calls are safe and have no effect after the first call.
     *
     * @throws RiveResourceClosedException If the owning Rive worker has been disposed.
     */
    @Throws(RiveResourceClosedException::class)
    override fun close() = closer.close()

    /** Whether this asset has been closed. */
    override val closed: Boolean
        get() = closer.closed

    /**
     * Ensures this asset has not been closed.
     *
     * @throws RiveResourceClosedException If this asset has already been closed.
     */
    @Throws(RiveResourceClosedException::class)
    internal fun checkOpen() = closer.checkOpen()

    /**
     * Requires this asset to be owned by [worker].
     *
     * This compatibility check assumes callers have already verified that the asset is open.
     *
     * @param worker The worker required to own this asset.
     * @throws RiveIncompatibleResourceException If this asset is owned by another worker.
     */
    @Throws(RiveIncompatibleResourceException::class)
    internal fun requireOwnedBy(worker: RiveWorker) {
        if (riveWorker !== worker) {
            throw RiveIncompatibleResourceException(
                "${ops.label} asset $handle is not owned by the required RiveWorker"
            )
        }
    }

    companion object {
        /**
         * Create and decode an asset of type [A] with handle type [H] from the given byte array.
         *
         * @param ops The operations for managing the asset type.
         * @param riveWorker The Rive worker that owns the asset.
         * @param bytes The byte array containing the asset data to decode.
         * @return The decoded asset.
         * @throws RiveResourceClosedException If [riveWorker] has been disposed.
         * @throws CancellationException If the coroutine is cancelled before decoding completes.
         */
        internal suspend fun <H, A : Asset<H>> createAsset(
            ops: AssetOps<H, A>,
            riveWorker: RiveWorker,
            bytes: ByteArray,
        ): A {
            RiveLog.d(ops.tag) { "Decoding ${ops.label}" }
            var workerReferenceAcquired = false // A failed acquire must not be balanced by release.
            // The factory owns a decoded handle until construction transfers it to the asset.
            var decodedHandle: H? = null
            return try {
                riveWorker.acquire(ops.tag)
                workerReferenceAcquired = true
                val handle = ops.decode(riveWorker, bytes)
                decodedHandle = handle
                ops.construct(handle, riveWorker)
            } catch (ce: CancellationException) {
                RiveLog.d(ops.tag) { "Decoding ${ops.label} was cancelled." }
                if (workerReferenceAcquired) {
                    decodedHandle?.let { ops.delete(riveWorker, it) }
                    riveWorker.release(ops.tag, "Cancellation")
                }
                throw ce
            } catch (e: Exception) {
                RiveLog.e(ops.tag, e) { "Failed to decode ${ops.label}." }
                if (workerReferenceAcquired) {
                    decodedHandle?.let { ops.delete(riveWorker, it) }
                    riveWorker.release(ops.tag, "Decode error")
                }
                throw e
            }
        }
    }
}

/**
 * Shared registration implementation for image, audio, and font assets.
 *
 * Closing releases the asset handle but does not remove registrations. Remove them explicitly,
 * preferably before closing. [unregister] remains available afterward while the worker is open.
 *
 * @param handle The native asset handle.
 * @param riveWorker The worker that owns the asset and registry.
 * @param ops The lifecycle and registration operations for this asset type.
 */
sealed class ReferencedAsset<H>(
    handle: H,
    riveWorker: RiveWorker,
    private val ops: RegisterableAssetOps<H, out Asset<H>>,
) : Asset<H>(handle, riveWorker, ops) {
    /**
     * Register the asset with the given key. When Rive fulfills a referenced asset, it will
     * look for an asset registered under that key. The key comes from the zip file created when
     * exporting a Rive file.
     *
     * Be sure to unregister the key with [unregister] when it is no longer needed, preferably
     * before calling [close].
     *
     * @param key The key to register the asset under.
     * @throws RiveResourceClosedException If this asset has been closed or its Rive worker has been
     *    disposed.
     */
    @Throws(RiveResourceClosedException::class)
    fun register(key: String) {
        checkOpen()
        RiveLog.d(ops.tag) { "Registering ${ops.label} with key: $key and handle: $handle" }
        ops.register(riveWorker, key, handle)
    }

    /**
     * Unregister the asset with the given key.
     *
     * This worker-level cleanup does not require the asset handle and remains available after
     * [close], provided the owning worker is still open.
     *
     * @param key The key to unregister the asset from.
     * @throws RiveResourceClosedException If this asset's Rive worker has been disposed.
     * @see register
     */
    @Throws(RiveResourceClosedException::class)
    fun unregister(key: String) {
        RiveLog.d(ops.tag) { "Unregistering ${ops.label} with key: $key" }
        ops.unregister(riveWorker, key)
    }
}

/**
 * An image asset, representing a renderable bitmap, managed by a [RiveWorker].
 *
 */
class ImageAsset private constructor(
    handle: ImageHandle,
    workerReference: AcquiredWorkerReference,
) : ReferencedAsset<ImageHandle>(handle, workerReference.worker, Ops) {
    /**
     * Creates an image asset for an existing decoded handle and acquires its worker reference.
     *
     * @param handle The handle to the image on the command server.
     * @param worker The Rive worker that owns the image.
     * @throws RiveResourceClosedException If [worker] has been disposed.
     * @deprecated Use [create]. Raw-handle construction will become internal in 12.0.
     */
    @Deprecated(
        "Raw-handle image construction will become internal in 12.0. Use ImageAsset.create()."
    )
    @Throws(RiveResourceClosedException::class)
    constructor(handle: ImageHandle, worker: RiveWorker) : this(
        handle,
        worker.acquireForAsset(IMAGE_TAG),
    )

    companion object {
        /**
         * Creates and decodes an image asset from the given byte array on the provided Rive worker.
         *
         * The input is copied during creation. Mutating [bytes] concurrently can produce
         * inconsistent image data or cause decoding to fail. After this function returns,
         * changes to [bytes] do not affect the image.
         *
         * The image can only be used on the same [RiveWorker] it was created on.
         *
         * Must be registered with [ImageAsset.register] to be used for referenced images. Remove
         * every registration with [ImageAsset.unregister], preferably before closing the image.
         *
         * ⚠️ The lifetime of the returned image is managed by the caller. Make sure to call [close]
         * when you are done with it to release its resources. The returned image holds a reference
         * to [riveWorker], so closing it is required before the worker can fully release memory
         * associated with this image.
         *
         * This is the replacement for [fromBytes]. Its temporary name avoids a
         * source-incompatible overload; it will be renamed to `fromBytes` in 12.0 when the
         * result-returning API is removed.
         *
         * @param riveWorker The Rive worker that owns the image.
         * @param bytes The byte array containing the image data to decode.
         * @return The decoded image asset.
         * @throws RiveImageException If [bytes] cannot be decoded as an image.
         * @throws RiveResourceClosedException If [riveWorker] has been disposed.
         * @throws CancellationException If the coroutine is cancelled before decoding completes.
         */
        @Throws(
            RiveImageException::class,
            RiveResourceClosedException::class,
            CancellationException::class
        )
        suspend fun create(riveWorker: RiveWorker, bytes: ByteArray): ImageAsset =
            createAsset(Ops, riveWorker, bytes)

        /**
         * Decodes an image asset from [bytes].
         *
         * @param riveWorker The Rive worker that owns the image.
         * @param bytes The encoded image bytes.
         * @return A success containing the decoded image, or an error if decoding fails. The
         *    loading state is not returned by this suspending function.
         * @throws CancellationException If the coroutine is cancelled before decoding completes.
         * @deprecated Use [create]. This result-returning implementation will be removed in 12.0,
         *    when [create] will be renamed to `fromBytes` and return the image directly.
         */
        @Throws(CancellationException::class)
        @Deprecated(
            "Use create. This result-returning implementation will be removed in 12.0, when " +
                "create will be renamed to fromBytes and return the image directly."
        )
        suspend fun fromBytes(riveWorker: RiveWorker, bytes: ByteArray): Result<ImageAsset> = try {
            Result.Success(create(riveWorker, bytes))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /** Internal lifecycle operations. Construction transfers an already-acquired worker reference. */
    private object Ops : RegisterableAssetOps<ImageHandle, ImageAsset> {
        override val tag = IMAGE_TAG
        override val label = "image"

        /** Decodes bytes on the owning worker. */
        override suspend fun decode(worker: RiveWorker, bytes: ByteArray): ImageHandle =
            worker.decodeImage(bytes)

        /** Deletes the native asset handle. */
        override fun delete(worker: RiveWorker, handle: ImageHandle) = worker.deleteImage(handle)

        /** Registers the asset under the referenced asset key. */
        override fun register(worker: RiveWorker, key: String, handle: ImageHandle) =
            worker.registerImage(key, handle)

        /** Removes the referenced asset registration. */
        override fun unregister(worker: RiveWorker, key: String) = worker.unregisterImage(key)

        /** Takes ownership of the worker reference acquired by the factory. */
        override fun construct(handle: ImageHandle, worker: RiveWorker) =
            ImageAsset(handle, AcquiredWorkerReference(worker))
    }
}

/**
 * An audio asset, representing a playable sound, managed by a [RiveWorker].
 *
 */
class AudioAsset private constructor(
    handle: AudioHandle,
    workerReference: AcquiredWorkerReference,
) : ReferencedAsset<AudioHandle>(handle, workerReference.worker, Ops) {
    /**
     * Creates an audio asset for an existing decoded handle and acquires its worker reference.
     *
     * @param handle The handle to the audio on the command server.
     * @param worker The Rive worker that owns the audio.
     * @throws RiveResourceClosedException If [worker] has been disposed.
     * @deprecated Use [create]. Raw-handle construction will become internal in 12.0.
     */
    @Deprecated(
        "Raw-handle audio construction will become internal in 12.0. Use AudioAsset.create()."
    )
    @Throws(RiveResourceClosedException::class)
    constructor(handle: AudioHandle, worker: RiveWorker) : this(
        handle,
        worker.acquireForAsset(AUDIO_TAG),
    )

    companion object {
        /**
         * Creates and decodes an audio asset from the given byte array on the provided Rive worker.
         *
         * The audio can only be used on the same [RiveWorker] it was created on.
         *
         * Must be registered with [AudioAsset.register] to be used for referenced audio. Remove
         * every registration with [AudioAsset.unregister], preferably before closing the audio.
         *
         * ⚠️ The lifetime of the returned audio is managed by the caller. Make sure to call [close]
         * when you are done with it to release its resources. The returned audio holds a reference
         * to [riveWorker], so closing it is required before the worker can fully release memory
         * associated with this audio.
         *
         * This is the replacement for [fromBytes]. Its temporary name avoids a
         * source-incompatible overload; it will be renamed to `fromBytes` in 12.0 when the
         * result-returning API is removed.
         *
         * @param riveWorker The Rive worker that owns the audio.
         * @param bytes The byte array containing the audio data to decode.
         * @return The decoded audio asset.
         * @throws RiveAudioException If [bytes] cannot be decoded as audio.
         * @throws RiveResourceClosedException If [riveWorker] has been disposed.
         * @throws CancellationException If the coroutine is cancelled before decoding completes.
         */
        @Throws(
            RiveAudioException::class,
            RiveResourceClosedException::class,
            CancellationException::class
        )
        suspend fun create(riveWorker: RiveWorker, bytes: ByteArray): AudioAsset =
            createAsset(Ops, riveWorker, bytes)

        /**
         * Decodes an audio asset from [bytes].
         *
         * @param riveWorker The Rive worker that owns the audio.
         * @param bytes The encoded audio bytes.
         * @return A success containing the decoded audio, or an error if decoding fails. The
         *    loading state is not returned by this suspending function.
         * @throws CancellationException If the coroutine is cancelled before decoding completes.
         * @deprecated Use [create]. This result-returning implementation will be removed in 12.0,
         *    when [create] will be renamed to `fromBytes` and return the audio directly.
         */
        @Throws(CancellationException::class)
        @Deprecated(
            "Use create. This result-returning implementation will be removed in 12.0, when " +
                "create will be renamed to fromBytes and return the audio directly."
        )
        suspend fun fromBytes(riveWorker: RiveWorker, bytes: ByteArray): Result<AudioAsset> = try {
            Result.Success(create(riveWorker, bytes))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /** Internal lifecycle operations. Construction transfers an already-acquired worker reference. */
    private object Ops : RegisterableAssetOps<AudioHandle, AudioAsset> {
        override val tag = AUDIO_TAG
        override val label = "audio"

        /** Decodes bytes on the owning worker. */
        override suspend fun decode(worker: RiveWorker, bytes: ByteArray): AudioHandle =
            worker.decodeAudio(bytes)

        /** Deletes the native asset handle. */
        override fun delete(worker: RiveWorker, handle: AudioHandle) = worker.deleteAudio(handle)

        /** Registers the asset under the referenced asset key. */
        override fun register(worker: RiveWorker, key: String, handle: AudioHandle) =
            worker.registerAudio(key, handle)

        /** Removes the referenced asset registration. */
        override fun unregister(worker: RiveWorker, key: String) = worker.unregisterAudio(key)

        /** Takes ownership of the worker reference acquired by the factory. */
        override fun construct(handle: AudioHandle, worker: RiveWorker) =
            AudioAsset(handle, AcquiredWorkerReference(worker))
    }
}

/**
 * A font asset, representing a renderable typeface, managed by a [RiveWorker].
 *
 */
class FontAsset private constructor(handle: FontHandle, workerReference: AcquiredWorkerReference) :
    ReferencedAsset<FontHandle>(handle, workerReference.worker, Ops) {
    /**
     * Creates a font asset for an existing decoded handle and acquires its worker reference.
     *
     * @param handle The handle to the font on the command server.
     * @param worker The Rive worker that owns the font.
     * @throws RiveResourceClosedException If [worker] has been disposed.
     * @deprecated Use [create]. Raw-handle construction will become internal in 12.0.
     */
    @Deprecated(
        "Raw-handle font construction will become internal in 12.0. Use FontAsset.create()."
    )
    @Throws(RiveResourceClosedException::class)
    constructor(handle: FontHandle, worker: RiveWorker) : this(
        handle,
        worker.acquireForAsset(FONT_TAG),
    )

    companion object {
        /**
         * Creates and decodes a font asset from the given byte array on the provided Rive worker.
         *
         * The input is copied during creation. Mutating [bytes] concurrently can produce
         * inconsistent font data or cause decoding to fail. After this function returns,
         * changes to [bytes] do not affect the font.
         *
         * The font can only be used on the same [RiveWorker] it was created on.
         *
         * Must be registered with [FontAsset.register] to be used for referenced fonts. Remove
         * every registration with [FontAsset.unregister], preferably before closing the font.
         *
         * ⚠️ The lifetime of the returned font is managed by the caller. Make sure to call [close]
         * when you are done with it to release its resources. The returned font holds a reference
         * to [riveWorker], so closing it is required before the worker can fully release memory
         * associated with this font.
         *
         * This is the replacement for [fromBytes]. Its temporary name avoids a
         * source-incompatible overload; it will be renamed to `fromBytes` in 12.0 when the
         * result-returning API is removed.
         *
         * @param riveWorker The Rive worker that owns the font.
         * @param bytes The byte array containing the font data to decode.
         * @return The decoded font asset.
         * @throws RiveFontException If [bytes] cannot be decoded as a font.
         * @throws RiveResourceClosedException If [riveWorker] has been disposed.
         * @throws CancellationException If the coroutine is cancelled before decoding completes.
         */
        @Throws(
            RiveFontException::class,
            RiveResourceClosedException::class,
            CancellationException::class
        )
        suspend fun create(riveWorker: RiveWorker, bytes: ByteArray): FontAsset =
            createAsset(Ops, riveWorker, bytes)

        /**
         * Decodes a font asset from [bytes].
         *
         * @param riveWorker The Rive worker that owns the font.
         * @param bytes The encoded font bytes.
         * @return A success containing the decoded font, or an error if decoding fails. The loading
         *    state is not returned by this suspending function.
         * @throws CancellationException If the coroutine is cancelled before decoding completes.
         * @deprecated Use [create]. This result-returning implementation will be removed in 12.0,
         *    when [create] will be renamed to `fromBytes` and return the font directly.
         */
        @Throws(CancellationException::class)
        @Deprecated(
            "Use create. This result-returning implementation will be removed in 12.0, when " +
                "create will be renamed to fromBytes and return the font directly."
        )
        suspend fun fromBytes(riveWorker: RiveWorker, bytes: ByteArray): Result<FontAsset> = try {
            Result.Success(create(riveWorker, bytes))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /** Internal lifecycle operations. Construction transfers an already-acquired worker reference. */
    private object Ops : RegisterableAssetOps<FontHandle, FontAsset> {
        override val tag = FONT_TAG
        override val label = "font"

        /** Decodes bytes on the owning worker. */
        override suspend fun decode(worker: RiveWorker, bytes: ByteArray): FontHandle =
            worker.decodeFont(bytes)

        /** Deletes the native asset handle. */
        override fun delete(worker: RiveWorker, handle: FontHandle) = worker.deleteFont(handle)

        /** Registers the asset under the referenced asset key. */
        override fun register(worker: RiveWorker, key: String, handle: FontHandle) =
            worker.registerFont(key, handle)

        /** Removes the referenced asset registration. */
        override fun unregister(worker: RiveWorker, key: String) = worker.unregisterFont(key)

        /** Takes ownership of the worker reference acquired by the factory. */
        override fun construct(handle: FontHandle, worker: RiveWorker) =
            FontAsset(handle, AcquiredWorkerReference(worker))
    }
}

/**
 * Arbitrary binary data managed by a [RiveWorker], for assignment with [ViewModelInstance.setBlob].
 *
 * The blob owns a copy of the input bytes. Close it when no longer needed. Assigned properties
 * retain their own native reference, so the blob may be closed after assignment.
 */
class BlobAsset private constructor(handle: BlobHandle, workerReference: AcquiredWorkerReference) :
    Asset<BlobHandle>(handle, workerReference.worker, Ops) {
    companion object {
        /**
         * Creates a blob by copying arbitrary binary data, including an empty array.
         *
         * The input is copied during creation. Mutating [bytes] concurrently can produce
         * inconsistent blob contents. After this function returns, changes to [bytes] do not
         * affect the blob.
         *
         * The returned blob can only be used with its owning worker and must be closed to release
         * its handle and worker reference.
         *
         * @param riveWorker The worker that owns the blob.
         * @param bytes The binary data to copy.
         * @return The created blob asset.
         * @throws RiveBlobException If native blob creation fails.
         * @throws RiveResourceClosedException If [riveWorker] has been disposed.
         * @throws CancellationException If creation is cancelled.
         */
        @Throws(
            RiveBlobException::class,
            RiveResourceClosedException::class,
            CancellationException::class
        )
        suspend fun fromBytes(riveWorker: RiveWorker, bytes: ByteArray): BlobAsset =
            createAsset(Ops, riveWorker, bytes)
    }

    /** Internal lifecycle operations. Construction transfers an already-acquired worker reference. */
    private object Ops : AssetOps<BlobHandle, BlobAsset> {
        override val tag = BLOB_TAG
        override val label = "blob"

        /** Copies the bytes into a blob on the worker. */
        override suspend fun decode(worker: RiveWorker, bytes: ByteArray): BlobHandle =
            worker.decodeBlob(bytes)

        /** Deletes the worker's blob handle. */
        override fun delete(worker: RiveWorker, handle: BlobHandle) = worker.deleteBlob(handle)

        /** Takes ownership of the handle and the worker reference acquired by the factory. */
        override fun construct(handle: BlobHandle, worker: RiveWorker) =
            BlobAsset(handle, AcquiredWorkerReference(worker))
    }
}

/**
 * Creates a blob from [bytes], closing it when the inputs change or it leaves composition.
 *
 * The blob can only be assigned to view models on the same worker. Treat [bytes] as immutable
 * while remembered, supplying a new array to replace the blob.
 *
 * @param riveWorker The worker that owns the blob.
 * @param bytes The binary data to copy, including an empty array.
 * @return Loading, an error, or the created [BlobAsset].
 */
@Composable
fun rememberBlob(riveWorker: RiveWorker, bytes: ByteArray): Result<BlobAsset> =
    rememberAsset(riveWorker, bytes, BlobAsset::fromBytes)

/**
 * Decode an image from the given [bytes] on the provided [RiveWorker]. The decoded image can only
 * be used on the same [RiveWorker] it was created on.
 *
 * This function is intended for use with images that may want to be registered multiple
 * times with [ImageAsset.register]. If you want to decode and register in one step, use
 * [rememberRegisteredImage] instead.
 *
 * The image will be deleted and its [RiveWorker] reference released when the composable leaves the
 * composition.
 *
 * @param riveWorker The Rive worker that owns and performs operations on this image.
 * @param bytes The byte array containing the image data to decode.
 * @return The [Result] of the image decoding, which can be either loading, error, or success with
 *    the [ImageAsset].
 */
@Composable
fun rememberImage(riveWorker: RiveWorker, bytes: ByteArray): Result<ImageAsset> =
    rememberAsset(riveWorker, bytes, ImageAsset::create)

/**
 * Decode and register an image from the given [bytes] on the provided [RiveWorker]. The decoded
 * image can only be used on the same [RiveWorker] it was created on.
 *
 * This function is intended for use with images that are registered once, as a convenience. If you
 * want to register multiple times, use [rememberImage] and [ImageAsset.register] instead.
 *
 * The image will be unregistered, deleted, and its [RiveWorker] reference released when the
 * composable leaves the composition.
 *
 * @param riveWorker The Rive worker that owns and performs operations on this image.
 * @param key The key of the referenced image. This comes from the zip file created when exporting a
 *    Rive file.
 * @param bytes The byte array containing the image data to decode.
 * @return The [Result] of the image decoding and registration, which can be either loading, error,
 *    or success with the [ImageAsset].
 */
@Composable
fun rememberRegisteredImage(
    riveWorker: RiveWorker,
    key: String,
    bytes: ByteArray,
): Result<ImageAsset> = rememberRegisteredAsset(riveWorker, bytes, ImageAsset::create, key)

/**
 * Decode audio from the given [bytes] on the provided [RiveWorker]. The decoded audio can only be
 * used on the same [RiveWorker] it was created on.
 *
 * This function is intended for use with audio that may want to be registered multiple
 * times with [AudioAsset.register]. If you want to decode and register in one step, use
 * [rememberRegisteredAudio] instead.
 *
 * The audio will be deleted and its [RiveWorker] reference released when the composable leaves the
 * composition.
 *
 * @param riveWorker The Rive worker that owns and performs operations on this audio.
 * @param bytes The byte array containing the audio data to decode.
 * @return The [Result] of the audio decoding, which can be either loading, error, or success with
 *    the [AudioAsset].
 */
@Composable
fun rememberAudio(riveWorker: RiveWorker, bytes: ByteArray): Result<AudioAsset> =
    rememberAsset(riveWorker, bytes, constructFn = AudioAsset::create)

/**
 * Decode and register audio from the given [bytes] on the provided [riveWorker]. The decoded audio
 * can only be used on the same [RiveWorker] it was created on.
 *
 * This function is intended for use with audio that is registered once, as a convenience. If you
 * want to register multiple times, use [rememberAudio] and [AudioAsset.register] instead.
 *
 * The audio will be unregistered, deleted, and its [RiveWorker] reference released when the
 * composable leaves the composition.
 *
 * @param riveWorker The Rive worker that owns and performs operations on this audio.
 * @param key The key of the referenced audio. This comes from the zip file created when exporting a
 *    Rive file.
 * @param bytes The byte array containing the audio data to decode.
 * @return The [Result] of the audio decoding and registration, which can be either loading, error,
 *    or success with the [AudioAsset].
 */
@Composable
fun rememberRegisteredAudio(
    riveWorker: RiveWorker,
    key: String,
    bytes: ByteArray,
): Result<AudioAsset> = rememberRegisteredAsset(riveWorker, bytes, AudioAsset::create, key)

/**
 * Decode a font from the given [bytes] on the provided [RiveWorker]. The decoded font can only be
 * used on the same [RiveWorker] it was created on.
 *
 * This function is intended for use with fonts that may want to be registered multiple
 * times with [FontAsset.register]. If you want to decode and register in one step, use
 * [rememberRegisteredFont] instead.
 *
 * The font will be deleted and its [RiveWorker] reference released when the composable leaves the
 * composition.
 *
 * @param riveWorker The Rive worker that owns and performs operations on this font.
 * @param bytes The byte array containing the font data to decode.
 * @return The [Result] of the font decoding, which can be either loading, error, or success with
 *    the [FontAsset].
 */
@Composable
fun rememberFont(riveWorker: RiveWorker, bytes: ByteArray): Result<FontAsset> =
    rememberAsset(riveWorker, bytes, FontAsset::create)

/**
 * Decode and register a font from the given [bytes] on the provided [RiveWorker]. The decoded font
 * can only be used on the same [RiveWorker] it was created on.
 *
 * This function is intended for use with fonts that are registered once, as a convenience. If you
 * want to register multiple times, use [rememberFont] and [FontAsset.register] instead.
 *
 * The font will be unregistered, deleted, and its [RiveWorker] reference released when the
 * composable leaves the composition.
 *
 * @param riveWorker The Rive worker that owns and performs operations on this font.
 * @param name The name of the referenced font. This comes from the zip file created when exporting
 *    a Rive file.
 * @param bytes The byte array containing the font data to decode.
 * @return The [Result] of the font decoding and registration, which can be either loading, error,
 *    or success with the [FontAsset].
 */
@Composable
fun rememberRegisteredFont(
    riveWorker: RiveWorker,
    name: String,
    bytes: ByteArray,
): Result<FontAsset> = rememberRegisteredAsset(riveWorker, bytes, FontAsset::create, name)

/**
 * Remembers a registered asset, requiring registration support at compile time.
 *
 * @param riveWorker The owning worker.
 * @param bytes The asset bytes.
 * @param constructFn The asset factory.
 * @param key The referenced asset registration key.
 * @return The loading, error, or registered asset result.
 */
@Composable
private fun <T : ReferencedAsset<H>, H> rememberRegisteredAsset(
    riveWorker: RiveWorker,
    bytes: ByteArray,
    constructFn: suspend (RiveWorker, ByteArray) -> T,
    key: String,
): Result<T> = rememberAsset(
    riveWorker,
    bytes,
    constructFn,
    key,
    onCreated = { it.register(key) },
    onDispose = { it.unregister(key) },
)

/**
 * Remembers an asset and closes it on replacement or disposal, including setup failures.
 *
 * @param riveWorker The owning worker.
 * @param bytes The asset bytes.
 * @param constructFn The asset factory.
 * @param key An additional key that requires recreation when changed.
 * @param onCreated Optional setup after creation, before publishing success.
 * @param onDispose Optional cleanup before closing a successfully created asset.
 * @return Loading, error, or success. Input changes synchronously reset the result to loading.
 */
@Composable
private fun <T : Asset<H>, H> rememberAsset(
    riveWorker: RiveWorker,
    bytes: ByteArray,
    constructFn: suspend (RiveWorker, ByteArray) -> T,
    key: String? = null,
    onCreated: ((T) -> Unit)? = null,
    onDispose: ((T) -> Unit)? = null,
): Result<T> = compositionKey(riveWorker, bytes, key) {
    produceState<Result<T>>(Result.Loading) {
        val asset = try {
            constructFn(riveWorker, bytes)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            value = Result.Error(e)
            return@produceState
        }

        if (onCreated != null) {
            try {
                onCreated(asset)
            } catch (ce: CancellationException) {
                try {
                    asset.close()
                } catch (closeFailure: Exception) {
                    ce.addSuppressed(closeFailure)
                }
                throw ce
            } catch (e: Exception) {
                try {
                    asset.close()
                } catch (closeFailure: Exception) {
                    e.addSuppressed(closeFailure)
                }
                value = Result.Error(e)
                return@produceState
            }
        }

        value = Result.Success(asset)

        awaitDispose {
            try {
                onDispose?.invoke(asset)
            } finally {
                asset.close()
            }
        }
    }.value
}

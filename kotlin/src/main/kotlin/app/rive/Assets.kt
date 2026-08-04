package app.rive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import app.rive.core.AudioHandle
import app.rive.core.CloseOnce
import app.rive.core.FontHandle
import app.rive.core.ImageHandle
import app.rive.core.RiveWorker
import kotlin.coroutines.cancellation.CancellationException

private const val IMAGE_TAG = "Rive/Image"
private const val AUDIO_TAG = "Rive/Audio"
private const val FONT_TAG = "Rive/Font"

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

    /** Register the asset with the provided Rive worker under the given key. */
    fun register(worker: RiveWorker, key: String, handle: H) {}

    /** Unregister the asset from the provided Rive worker for the given key. */
    fun unregister(worker: RiveWorker, key: String) {}

    /** Construct the asset by taking ownership of the worker reference acquired by [Asset.fromBytes]. */
    fun construct(handle: H, worker: RiveWorker): A
}

/**
 * Base class for assets managed by a [RiveWorker].
 *
 * Uses [ops] to perform operations specific to the asset type.
 *
 * Each asset holds a reference to its [RiveWorker]. When creating assets manually, call [close]
 * when you no longer need the asset; releasing the worker alone is not enough to purge asset or
 * worker memory while the asset remains open. Closing the asset does not unregister its keys;
 * unregister them explicitly, preferably before calling [close].
 *
 * @param handle The handle to the asset on the command server.
 * @param riveWorker The Rive worker that owns the asset.
 * @param ops The operations for managing the asset type.
 */
sealed class Asset<H>(
    val handle: H,
    protected val riveWorker: RiveWorker,
    private val ops: AssetOps<H, out Asset<H>>,
) : AutoCloseable {
    private val closer = CloseOnce("$handle") {
        RiveLog.d(ops.tag) { "Deleting ${ops.label} with handle: $handle" }
        ops.delete(riveWorker, handle)

        riveWorker.release(ops.tag, "Asset closed")
    }

    /**
     * Closes this asset and schedules deletion on its Rive worker.
     *
     * Closing does not unregister keys registered through this asset. Prefer to unregister them
     * before closing; [unregister] remains available afterward while the worker is open. Repeated
     * calls are safe and have no effect after the first call.
     *
     * @throws RiveResourceClosedException If the owning Rive worker has been disposed.
     */
    @Throws(RiveResourceClosedException::class)
    override fun close() = closer.close()

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
         * @return The [Result] of the asset decoding, which can be either loading, error, or
         *    success with the decoded asset.
         */
        internal suspend fun <H, A : Asset<H>> fromBytes(
            ops: AssetOps<H, A>,
            riveWorker: RiveWorker,
            bytes: ByteArray,
        ): Result<A> {
            RiveLog.d(ops.tag) { "Decoding ${ops.label}" }
            riveWorker.acquire(ops.tag)
            return try {
                val handle = ops.decode(riveWorker, bytes)
                Result.Success(ops.construct(handle, riveWorker))
            } catch (ce: CancellationException) {
                RiveLog.d(ops.tag) { "Decoding ${ops.label} was cancelled." }
                riveWorker.release(ops.tag, "Cancellation")
                throw ce
            } catch (e: Exception) {
                RiveLog.e(ops.tag, e) { "Failed to decode ${ops.label}." }
                riveWorker.release(ops.tag, "Decode error")
                Result.Error(e)
            }
        }
    }

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
        closer.checkOpen()
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
) : Asset<ImageHandle>(handle, workerReference.worker, ImageAsset) {
    /**
     * Creates an image asset for an existing decoded handle and acquires its worker reference.
     *
     * @param handle The handle to the image on the command server.
     * @param worker The Rive worker that owns the image.
     * @throws RiveResourceClosedException If [worker] has been disposed.
     * @deprecated Use [fromBytes]. Raw-handle construction will become internal in 12.0.
     */
    @Deprecated(
        "Raw-handle image construction will become internal in 12.0. Use ImageAsset.fromBytes()."
    )
    @Throws(RiveResourceClosedException::class)
    constructor(handle: ImageHandle, worker: RiveWorker) : this(
        handle,
        worker.acquireForAsset(IMAGE_TAG),
    )

    companion object : AssetOps<ImageHandle, ImageAsset> {
        /**
         * Create and decode an image asset from the given byte array on the provided Rive worker.
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
         * @param riveWorker The Rive worker that owns the image.
         * @param bytes The byte array containing the image data to decode.
         * @return The [Result] of the image decoding. Decode failures are returned as
         *    [Result.Error] containing a [RiveImageException].
         * @throws CancellationException If the coroutine is cancelled before decoding completes.
         */
        @Throws(CancellationException::class)
        suspend fun fromBytes(riveWorker: RiveWorker, bytes: ByteArray): Result<ImageAsset> =
            fromBytes(this, riveWorker, bytes)

        override val tag = IMAGE_TAG
        override val label = "image"
        override suspend fun decode(worker: RiveWorker, bytes: ByteArray): ImageHandle =
            worker.decodeImage(bytes)

        override fun delete(worker: RiveWorker, handle: ImageHandle) =
            worker.deleteImage(handle)

        override fun register(worker: RiveWorker, key: String, handle: ImageHandle) =
            worker.registerImage(key, handle)

        override fun unregister(worker: RiveWorker, key: String) =
            worker.unregisterImage(key)

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
) : Asset<AudioHandle>(handle, workerReference.worker, AudioAsset) {
    /**
     * Creates an audio asset for an existing decoded handle and acquires its worker reference.
     *
     * @param handle The handle to the audio on the command server.
     * @param worker The Rive worker that owns the audio.
     * @throws RiveResourceClosedException If [worker] has been disposed.
     * @deprecated Use [fromBytes]. Raw-handle construction will become internal in 12.0.
     */
    @Deprecated(
        "Raw-handle audio construction will become internal in 12.0. Use AudioAsset.fromBytes()."
    )
    @Throws(RiveResourceClosedException::class)
    constructor(handle: AudioHandle, worker: RiveWorker) : this(
        handle,
        worker.acquireForAsset(AUDIO_TAG),
    )

    companion object : AssetOps<AudioHandle, AudioAsset> {
        /**
         * Create and decode an audio asset from the given byte array on the provided Rive worker.
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
         * @param riveWorker The Rive worker that owns the audio.
         * @param bytes The byte array containing the audio data to decode.
         * @return The [Result] of the audio decoding. Decode failures are returned as
         *    [Result.Error] containing a [RiveAudioException].
         * @throws CancellationException If the coroutine is cancelled before decoding completes.
         */
        @Throws(CancellationException::class)
        suspend fun fromBytes(riveWorker: RiveWorker, bytes: ByteArray): Result<AudioAsset> =
            fromBytes(this, riveWorker, bytes)

        override val tag = AUDIO_TAG
        override val label = "audio"
        override suspend fun decode(worker: RiveWorker, bytes: ByteArray): AudioHandle =
            worker.decodeAudio(bytes)

        override fun delete(worker: RiveWorker, handle: AudioHandle) =
            worker.deleteAudio(handle)

        override fun register(worker: RiveWorker, key: String, handle: AudioHandle) =
            worker.registerAudio(key, handle)

        override fun unregister(worker: RiveWorker, key: String) =
            worker.unregisterAudio(key)

        override fun construct(handle: AudioHandle, worker: RiveWorker) =
            AudioAsset(handle, AcquiredWorkerReference(worker))
    }
}

/**
 * A font asset, representing a renderable typeface, managed by a [RiveWorker].
 *
 */
class FontAsset private constructor(
    handle: FontHandle,
    workerReference: AcquiredWorkerReference,
) : Asset<FontHandle>(handle, workerReference.worker, FontAsset) {
    /**
     * Creates a font asset for an existing decoded handle and acquires its worker reference.
     *
     * @param handle The handle to the font on the command server.
     * @param worker The Rive worker that owns the font.
     * @throws RiveResourceClosedException If [worker] has been disposed.
     * @deprecated Use [fromBytes]. Raw-handle construction will become internal in 12.0.
     */
    @Deprecated(
        "Raw-handle font construction will become internal in 12.0. Use FontAsset.fromBytes()."
    )
    @Throws(RiveResourceClosedException::class)
    constructor(handle: FontHandle, worker: RiveWorker) : this(
        handle,
        worker.acquireForAsset(FONT_TAG),
    )

    companion object : AssetOps<FontHandle, FontAsset> {
        /**
         * Create and decode a font asset from the given byte array on the provided Rive worker.
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
         * @param riveWorker The Rive worker that owns the font.
         * @param bytes The byte array containing the font data to decode.
         * @return The [Result] of the font decoding. Decode failures are returned as
         *    [Result.Error] containing a [RiveFontException].
         * @throws CancellationException If the coroutine is cancelled before decoding completes.
         */
        @Throws(CancellationException::class)
        suspend fun fromBytes(riveWorker: RiveWorker, bytes: ByteArray): Result<FontAsset> =
            fromBytes(this, riveWorker, bytes)

        override val tag = FONT_TAG
        override val label = "font"
        override suspend fun decode(worker: RiveWorker, bytes: ByteArray): FontHandle =
            worker.decodeFont(bytes)

        override fun delete(worker: RiveWorker, handle: FontHandle) =
            worker.deleteFont(handle)

        override fun register(worker: RiveWorker, key: String, handle: FontHandle) =
            worker.registerFont(key, handle)

        override fun unregister(worker: RiveWorker, key: String) =
            worker.unregisterFont(key)

        override fun construct(handle: FontHandle, worker: RiveWorker) =
            FontAsset(handle, AcquiredWorkerReference(worker))
    }
}

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
 *    the [ImageHandle].
 */
@Composable
fun rememberImage(
    riveWorker: RiveWorker,
    bytes: ByteArray,
): Result<ImageAsset> = rememberAsset(riveWorker, bytes, ImageAsset::fromBytes)

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
 *    or success with the [ImageHandle].
 */
@Composable
fun rememberRegisteredImage(
    riveWorker: RiveWorker,
    key: String,
    bytes: ByteArray,
): Result<ImageAsset> = rememberAsset(riveWorker, bytes, ImageAsset::fromBytes, key)

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
 *    the [AudioHandle].
 */
@Composable
fun rememberAudio(
    riveWorker: RiveWorker,
    bytes: ByteArray,
): Result<AudioAsset> = rememberAsset(riveWorker, bytes, constructFn = AudioAsset::fromBytes)

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
 *    or success with the [AudioHandle].
 */
@Composable
fun rememberRegisteredAudio(
    riveWorker: RiveWorker,
    key: String,
    bytes: ByteArray,
): Result<AudioAsset> = rememberAsset(riveWorker, bytes, AudioAsset::fromBytes, key)

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
 *    the [FontHandle].
 */
@Composable
fun rememberFont(
    riveWorker: RiveWorker,
    bytes: ByteArray,
): Result<FontAsset> = rememberAsset(riveWorker, bytes, FontAsset::fromBytes)

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
 *    or success with the [FontHandle].
 */
@Composable
fun rememberRegisteredFont(
    riveWorker: RiveWorker,
    name: String,
    bytes: ByteArray,
): Result<FontAsset> = rememberAsset(riveWorker, bytes, FontAsset::fromBytes, name)

/**
 * Internal helper to unify the implementation of asset loading and registering.
 *
 * It handles the registration and un-registration of the asset if a [key] is provided, and it
 * deletes the asset when the composable leaves the composition.
 *
 * @param riveWorker The Rive worker that owns and performs operations on this asset.
 * @param bytes The byte array containing the asset data to decode.
 * @param constructFn The function to use to decode and construct the asset from the given
 *    [riveWorker], [bytes], and optional [key].
 * @param key The key of the asset, used for registration. If null, the asset will not be
 *    registered.
 * @return A [Result] containing the result of the asset decoding (and possible registration), which
 *    can be either loading, error, or success with the decoded asset.
 */
@Composable
private fun <T : Asset<H>, H> rememberAsset(
    riveWorker: RiveWorker,
    bytes: ByteArray,
    constructFn: (suspend (RiveWorker, ByteArray) -> Result<T>),
    key: String? = null
): Result<T> = produceState<Result<T>>(Result.Loading, riveWorker, bytes, key) {
    val asset = constructFn(riveWorker, bytes)

    if (key != null && asset is Result.Success) {
        asset.value.register(key)
    }

    value = asset

    awaitDispose {
        if (asset !is Result.Success) return@awaitDispose

        if (key != null) {
            asset.value.unregister(key)
        }
        asset.value.close()
    }
}.value

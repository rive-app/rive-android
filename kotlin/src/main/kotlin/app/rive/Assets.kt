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

    /** Construct the asset instance from the given handle, key, and Rive worker. */
    fun construct(handle: H, worker: RiveWorker): A
}

/**
 * Base class for assets managed by a [RiveWorker].
 *
 * Uses [ops] to perform operations specific to the asset type.
 *
 * @param handle The handle to the asset on the command server.
 * @param riveWorker The Rive worker that owns the asset.
 * @param ops The operations for managing the asset type.
 */
sealed class Asset<H>(
    val handle: H,
    protected val riveWorker: RiveWorker,
    private val ops: AssetOps<H, out Asset<H>>,
) : AutoCloseable by CloseOnce("$handle", {
    RiveLog.d(ops.tag) { "Deleting ${ops.label} with handle: $handle" }
    ops.delete(riveWorker, handle)

    riveWorker.release(ops.tag, "Asset closed")
}) {
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
     * Be sure to unregister it with [unregister] when done.
     *
     * @param key The key to register the asset under.
     */
    fun register(key: String) {
        RiveLog.d(ops.tag) { "Registering ${ops.label} with key: $key and handle: $handle" }
        ops.register(riveWorker, key, handle)
    }

    /**
     * Unregister the asset with the given key.
     *
     * @param key The key to unregister the asset from.
     * @see register
     */
    fun unregister(key: String) {
        RiveLog.d(ops.tag) { "Unregistering ${ops.label} with key: $key" }
        ops.unregister(riveWorker, key)
    }
}

/**
 * An image asset, representing a renderable bitmap, managed by a [RiveWorker].
 *
 * @param handle The handle to the image on the command server.
 * @param worker The Rive worker that owns the image.
 */
class ImageAsset(
    handle: ImageHandle,
    worker: RiveWorker,
) : Asset<ImageHandle>(handle, worker, ImageAsset) {
    companion object : AssetOps<ImageHandle, ImageAsset> {
        /**
         * Create and decode an image asset from the given byte array on the provided Rive worker.
         *
         * The image can only be used on the same [RiveWorker] it was created on.
         *
         * Must be registered with [ImageAsset.register] to be used for referenced images.
         *
         * ⚠️ The lifetime of the returned image is managed by the caller. Make sure to call [close]
         * when you are done with it to release its resources.
         *
         * @param riveWorker The Rive worker that owns the image.
         * @param bytes The byte array containing the image data to decode.
         * @return The [Result] of the image decoding, which can be either loading, error, or
         *    success with the decoded image asset.
         */
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
            ImageAsset(handle, worker)
    }
}

/**
 * An audio asset, representing a playable sound, managed by a [RiveWorker].
 *
 * @param handle The handle to the audio on the command server.
 * @param worker The Rive worker that owns the audio.
 */
class AudioAsset(
    handle: AudioHandle,
    worker: RiveWorker,
) : Asset<AudioHandle>(handle, worker, AudioAsset) {
    companion object : AssetOps<AudioHandle, AudioAsset> {
        /**
         * Create and decode an audio asset from the given byte array on the provided Rive worker.
         *
         * The audio can only be used on the same [RiveWorker] it was created on.
         *
         * Must be registered with [AudioAsset.register] to be used for referenced audio.
         *
         * ⚠️ The lifetime of the returned audio is managed by the caller. Make sure to call [close]
         * when you are done with it to release its resources.
         *
         * @param riveWorker The Rive worker that owns the audio.
         * @param bytes The byte array containing the audio data to decode.
         * @return The [Result] of the audio decoding, which can be either loading, error, or
         *    success with the decoded audio asset.
         */
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
            AudioAsset(handle, worker)
    }
}

/**
 * A font asset, representing a renderable typeface, managed by a [RiveWorker].
 *
 * @param handle The handle to the font on the command server.
 * @param worker The Rive worker that owns the font.
 */
class FontAsset(
    handle: FontHandle,
    worker: RiveWorker,
) : Asset<FontHandle>(handle, worker, FontAsset) {
    companion object : AssetOps<FontHandle, FontAsset> {
        /**
         * Create and decode a font asset from the given byte array on the provided Rive worker.
         *
         * The font can only be used on the same [RiveWorker] it was created on.
         *
         * Must be registered with [FontAsset.register] to be used for referenced fonts.
         *
         * ⚠️ The lifetime of the returned font is managed by the caller. Make sure to call [close]
         * when you are done with it to release its resources.
         *
         * @param riveWorker The Rive worker that owns the font.
         * @param bytes The byte array containing the font data to decode.
         * @return The [Result] of the font decoding, which can be either loading, error, or success
         *    with the decoded font asset.
         */
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
            FontAsset(handle, worker)
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
 * The image will be deleted when the composable leaves the composition.
 *
 * @param riveWorker The Rive worker that owns and performs operations on this image.
 * @param bytes The byte array containing the image data to decode.
 * @return The [Result] of the image decoding, which can be either loading, error, or success with
 *    the [ImageHandle].
 */
@ExperimentalRiveComposeAPI
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
 * The image will be deleted and unregistered when the composable leaves the composition.
 *
 * @param riveWorker The Rive worker that owns and performs operations on this image.
 * @param key The key of the referenced image. This comes from the zip file created when exporting a
 *    Rive file.
 * @param bytes The byte array containing the image data to decode.
 * @return The [Result] of the image decoding and registration, which can be either loading, error,
 *    or success with the [ImageHandle].
 */
@ExperimentalRiveComposeAPI
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
 * The audio will be deleted when the composable leaves the composition.
 *
 * @param riveWorker The Rive worker that owns and performs operations on this audio.
 * @param bytes The byte array containing the audio data to decode.
 * @return The [Result] of the audio decoding, which can be either loading, error, or success with
 *    the [AudioHandle].
 */
@ExperimentalRiveComposeAPI
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
 * The audio will be deleted and unregistered when the composable leaves the composition.
 *
 * @param riveWorker The Rive worker that owns and performs operations on this audio.
 * @param key The key of the referenced audio. This comes from the zip file created when exporting a
 *    Rive file.
 * @param bytes The byte array containing the audio data to decode.
 * @return The [Result] of the audio decoding and registration, which can be either loading, error,
 *    or success with the [AudioHandle].
 */
@ExperimentalRiveComposeAPI
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
 * The font will be deleted when the composable leaves the composition.
 *
 * @param riveWorker The Rive worker that owns and performs operations on this font.
 * @param bytes The byte array containing the font data to decode.
 * @return The [Result] of the font decoding, which can be either loading, error, or success with
 *    the [FontHandle].
 */
@ExperimentalRiveComposeAPI
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
 * The font will be deleted and unregistered when the composable leaves the composition.
 *
 * @param riveWorker The Rive worker that owns and performs operations on this font.
 * @param name The name of the referenced font. This comes from the zip file created when exporting
 *    a Rive file.
 * @param bytes The byte array containing the font data to decode.
 * @return The [Result] of the font decoding and registration, which can be either loading, error,
 *    or success with the [FontHandle].
 */
@ExperimentalRiveComposeAPI
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

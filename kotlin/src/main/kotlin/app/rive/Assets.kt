package app.rive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import app.rive.core.AudioHandle
import app.rive.core.CloseOnce
import app.rive.core.CommandQueue
import app.rive.core.FontHandle
import app.rive.core.ImageHandle
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

    /** Decode the asset from the given byte array on the provided command queue. */
    suspend fun decode(queue: CommandQueue, bytes: ByteArray): H

    /** Delete the asset from the provided command queue. */
    fun delete(queue: CommandQueue, handle: H)

    /** Register the asset with the provided command queue under the given key. */
    fun register(queue: CommandQueue, key: String, handle: H) {}

    /** Unregister the asset from the provided command queue for the given key. */
    fun unregister(queue: CommandQueue, key: String) {}

    /** Construct the asset instance from the given handle, key, and command queue. */
    fun construct(handle: H, queue: CommandQueue): A
}

/**
 * Base class for assets managed by a [CommandQueue].
 *
 * Uses [ops] to perform operations specific to the asset type.
 *
 * @param handle The handle to the asset on the command server.
 * @param commandQueue The command queue that owns the asset.
 * @param ops The operations for managing the asset type.
 */
sealed class Asset<H>(
    protected val handle: H,
    protected val commandQueue: CommandQueue,
    private val ops: AssetOps<H, out Asset<H>>,
) : AutoCloseable by CloseOnce("$handle", {
    RiveLog.d(ops.tag) { "Deleting ${ops.label} with handle: $handle" }
    ops.delete(commandQueue, handle)

    commandQueue.release(ops.tag, "Asset closed")
}) {
    companion object {
        /**
         * Create and decode an asset of type [A] with handle type [H] from the given byte array.
         *
         * @param ops The operations for managing the asset type.
         * @param commandQueue The command queue that owns the asset.
         * @param bytes The byte array containing the asset data to decode.
         * @return The [Result] of the asset decoding, which can be either loading, error, or
         *    success with the decoded asset.
         */
        internal suspend fun <H, A : Asset<H>> fromBytes(
            ops: AssetOps<H, A>,
            commandQueue: CommandQueue,
            bytes: ByteArray,
        ): Result<A> {
            RiveLog.d(ops.tag) { "Decoding ${ops.label}" }
            commandQueue.acquire(ops.tag)
            return try {
                val handle = ops.decode(commandQueue, bytes)
                Result.Success(ops.construct(handle, commandQueue))
            } catch (ce: CancellationException) {
                RiveLog.d(ops.tag) { "Decoding ${ops.label} was cancelled." }
                commandQueue.release(ops.tag, "Cancellation")
                throw ce
            } catch (e: Exception) {
                RiveLog.e(ops.tag, e) { "Failed to decode ${ops.label}." }
                commandQueue.release(ops.tag, "Decode error")
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
        ops.register(commandQueue, key, handle)
    }

    /**
     * Unregister the asset with the given key.
     *
     * @param key The key to unregister the asset from.
     * @see register
     */
    fun unregister(key: String) {
        RiveLog.d(ops.tag) { "Unregistering ${ops.label} with key: $key" }
        ops.unregister(commandQueue, key)
    }
}

/**
 * An image asset, representing a renderable bitmap, managed by a [CommandQueue].
 *
 * @param handle The handle to the image on the command server.
 * @param queue The command queue that owns the image.
 */
class ImageAsset(
    handle: ImageHandle,
    queue: CommandQueue,
) : Asset<ImageHandle>(handle, queue, ImageAsset) {
    companion object : AssetOps<ImageHandle, ImageAsset> {
        /**
         * Create and decode an image asset from the given byte array on the provided command queue.
         *
         * The image can only be used on the same [CommandQueue] it was created on.
         *
         * Must be registered with [ImageAsset.register] to be used for referenced images.
         *
         * @param commandQueue The command queue that owns the image.
         * @param bytes The byte array containing the image data to decode.
         * @return The [Result] of the image decoding, which can be either loading, error, or
         *    success with the decoded image asset.
         */
        suspend fun fromBytes(commandQueue: CommandQueue, bytes: ByteArray): Result<ImageAsset> =
            fromBytes(this, commandQueue, bytes)

        override val tag = IMAGE_TAG
        override val label = "image"
        override suspend fun decode(queue: CommandQueue, bytes: ByteArray): ImageHandle =
            queue.decodeImage(bytes)

        override fun delete(queue: CommandQueue, handle: ImageHandle) =
            queue.deleteImage(handle)

        override fun register(queue: CommandQueue, key: String, handle: ImageHandle) =
            queue.registerImage(key, handle)

        override fun unregister(queue: CommandQueue, key: String) =
            queue.unregisterImage(key)

        override fun construct(handle: ImageHandle, queue: CommandQueue) =
            ImageAsset(handle, queue)
    }
}

/**
 * An audio asset, representing a playable sound, managed by a [CommandQueue].
 *
 * @param handle The handle to the audio on the command server.
 * @param queue The command queue that owns the audio.
 */
class AudioAsset(
    handle: AudioHandle,
    queue: CommandQueue,
) : Asset<AudioHandle>(handle, queue, AudioAsset) {
    companion object : AssetOps<AudioHandle, AudioAsset> {
        /**
         * Create and decode an audio asset from the given byte array on the provided command queue.
         *
         * The audio can only be used on the same [CommandQueue] it was created on.
         *
         * Must be registered with [AudioAsset.register] to be used for referenced audio.
         *
         * @param commandQueue The command queue that owns the audio.
         * @param bytes The byte array containing the audio data to decode.
         * @return The [Result] of the audio decoding, which can be either loading, error, or
         *    success with the decoded audio asset.
         */
        suspend fun fromBytes(commandQueue: CommandQueue, bytes: ByteArray): Result<AudioAsset> =
            fromBytes(this, commandQueue, bytes)

        override val tag = AUDIO_TAG
        override val label = "audio"
        override suspend fun decode(queue: CommandQueue, bytes: ByteArray): AudioHandle =
            queue.decodeAudio(bytes)

        override fun delete(queue: CommandQueue, handle: AudioHandle) =
            queue.deleteAudio(handle)

        override fun register(queue: CommandQueue, key: String, handle: AudioHandle) =
            queue.registerAudio(key, handle)

        override fun unregister(queue: CommandQueue, key: String) =
            queue.unregisterAudio(key)

        override fun construct(handle: AudioHandle, queue: CommandQueue) =
            AudioAsset(handle, queue)
    }
}

/**
 * A font asset, representing a renderable typeface, managed by a [CommandQueue].
 *
 * @param handle The handle to the font on the command server.
 * @param queue The command queue that owns the font.
 */
class FontAsset(
    handle: FontHandle,
    queue: CommandQueue,
) : Asset<FontHandle>(handle, queue, FontAsset) {
    companion object : AssetOps<FontHandle, FontAsset> {
        /**
         * Create and decode a font asset from the given byte array on the provided command queue.
         *
         * The font can only be used on the same [CommandQueue] it was created on.
         *
         * Must be registered with [FontAsset.register] to be used for referenced fonts.
         *
         * @param commandQueue The command queue that owns the font.
         * @param bytes The byte array containing the font data to decode.
         * @return The [Result] of the font decoding, which can be either loading, error, or success
         *    with the decoded font asset.
         */
        suspend fun fromBytes(commandQueue: CommandQueue, bytes: ByteArray): Result<FontAsset> =
            fromBytes(this, commandQueue, bytes)

        override val tag = FONT_TAG
        override val label = "font"
        override suspend fun decode(queue: CommandQueue, bytes: ByteArray): FontHandle =
            queue.decodeFont(bytes)

        override fun delete(queue: CommandQueue, handle: FontHandle) =
            queue.deleteFont(handle)

        override fun register(queue: CommandQueue, key: String, handle: FontHandle) =
            queue.registerFont(key, handle)

        override fun unregister(queue: CommandQueue, key: String) =
            queue.unregisterFont(key)

        override fun construct(handle: FontHandle, queue: CommandQueue) =
            FontAsset(handle, queue)
    }
}

/**
 * WARNING: This function is not yet functional. It will be implemented in a future release.
 *
 * Decode an image from the given [bytes] on the provided [commandQueue]. The decoded image can only
 * be used on the same [CommandQueue] it was created on.
 *
 * This function is intended for use with images that may want to be registered multiple
 * times with [ImageAsset.register]. If you want to decode and register in one step, use
 * [rememberRegisteredImage] instead.
 *
 * The image will be deleted when the composable leaves the composition.
 *
 * @param commandQueue The command queue that owns and performs operations on this image.
 * @param bytes The byte array containing the image data to decode.
 * @return The [Result] of the image decoding, which can be either loading, error, or success with
 *    the [ImageHandle].
 */
@ExperimentalRiveComposeAPI
@Composable
@Throws(NotImplementedError::class)
@Suppress("UNUSED_PARAMETER", "UNREACHABLE_CODE")
fun rememberImage(
    commandQueue: CommandQueue,
    bytes: ByteArray,
): Result<ImageAsset> {
    TODO("Image decoding is not yet functional in the Rive Compose library. It will be implemented in a future release.")
    return rememberAsset(commandQueue, bytes, ImageAsset::fromBytes)
}

/**
 * WARNING: This function is not yet functional. It will be implemented in a future release.
 *
 * Decode and register an image from the given [bytes] on the provided [commandQueue]. The decoded
 * image can only be used on the same [CommandQueue] it was created on.
 *
 * This function is intended for use with images that are registered once, as a convenience. If you
 * want to register multiple times, use [rememberImage] and [ImageAsset.register] instead.
 *
 * The image will be deleted and unregistered when the composable leaves the composition.
 *
 * @param commandQueue The command queue that owns and performs operations on this image.
 * @param key The key of the referenced image. This comes from the zip file created when exporting a
 *    Rive file.
 * @param bytes The byte array containing the image data to decode.
 * @return The [Result] of the image decoding and registration, which can be either loading, error,
 *    or success with the [ImageHandle].
 */
@ExperimentalRiveComposeAPI
@Composable
@Throws(NotImplementedError::class)
@Suppress("UNUSED_PARAMETER", "UNREACHABLE_CODE")
fun rememberRegisteredImage(
    commandQueue: CommandQueue,
    key: String,
    bytes: ByteArray,
): Result<ImageAsset> {
    TODO("Image decoding is not yet functional in the Rive Compose library. It will be implemented in a future release.")
    return rememberAsset(commandQueue, bytes, ImageAsset::fromBytes, key)
}

/**
 * Decode audio from the given [bytes] on the provided [commandQueue]. The decoded audio can only be
 * used on the same [CommandQueue] it was created on.
 *
 * This function is intended for use with audio that may want to be registered multiple
 * times with [AudioAsset.register]. If you want to decode and register in one step, use
 * [rememberRegisteredAudio] instead.
 *
 * The audio will be deleted when the composable leaves the composition.
 *
 * @param commandQueue The command queue that owns and performs operations on this audio.
 * @param bytes The byte array containing the audio data to decode.
 * @return The [Result] of the audio decoding, which can be either loading, error, or success with
 *    the [AudioHandle].
 */
@ExperimentalRiveComposeAPI
@Composable
fun rememberAudio(
    commandQueue: CommandQueue,
    bytes: ByteArray,
): Result<AudioAsset> = rememberAsset(commandQueue, bytes, constructFn = AudioAsset::fromBytes)

/**
 * Decode and register audio from the given [bytes] on the provided [commandQueue]. The decoded
 * audio can only be used on the same [CommandQueue] it was created on.
 *
 * This function is intended for use with audio that is registered once, as a convenience. If you
 * want to register multiple times, use [rememberAudio] and [AudioAsset.register] instead.
 *
 * The audio will be deleted and unregistered when the composable leaves the composition.
 *
 * @param commandQueue The command queue that owns and performs operations on this audio.
 * @param key The key of the referenced audio. This comes from the zip file created when exporting a
 *    Rive file.
 * @param bytes The byte array containing the audio data to decode.
 * @return The [Result] of the audio decoding and registration, which can be either loading, error,
 *    or success with the [AudioHandle].
 */
@ExperimentalRiveComposeAPI
@Composable
fun rememberRegisteredAudio(
    commandQueue: CommandQueue,
    key: String,
    bytes: ByteArray,
): Result<AudioAsset> = rememberAsset(commandQueue, bytes, AudioAsset::fromBytes, key)

/**
 * Decode a font from the given [bytes] on the provided [commandQueue]. The decoded font can only be
 * used on the same [CommandQueue] it was created on.
 *
 * This function is intended for use with fonts that may want to be registered multiple
 * times with [FontAsset.register]. If you want to decode and register in one step, use
 * [rememberRegisteredFont] instead.
 *
 * The font will be deleted when the composable leaves the composition.
 *
 * @param commandQueue The command queue that owns and performs operations on this font.
 * @param bytes The byte array containing the font data to decode.
 * @return The [Result] of the font decoding, which can be either loading, error, or success with
 *    the [FontHandle].
 */
@ExperimentalRiveComposeAPI
@Composable
fun rememberFont(
    commandQueue: CommandQueue,
    bytes: ByteArray,
): Result<FontAsset> = rememberAsset(commandQueue, bytes, FontAsset::fromBytes)

/**
 * Decode and register a font from the given [bytes] on the provided [commandQueue]. The decoded
 * font can only be used on the same [CommandQueue] it was created on.
 *
 * This function is intended for use with fonts that are registered once, as a convenience. If you
 * want to register multiple times, use [rememberFont] and [FontAsset.register] instead.
 *
 * The font will be deleted and unregistered when the composable leaves the composition.
 *
 * @param commandQueue The command queue that owns and performs operations on this font.
 * @param name The name of the referenced font. This comes from the zip file created when exporting
 *    a Rive file.
 * @param bytes The byte array containing the font data to decode.
 * @return The [Result] of the font decoding and registration, which can be either loading, error,
 *    or success with the [FontHandle].
 */
@ExperimentalRiveComposeAPI
@Composable
fun rememberRegisteredFont(
    commandQueue: CommandQueue,
    name: String,
    bytes: ByteArray,
): Result<FontAsset> = rememberAsset(commandQueue, bytes, FontAsset::fromBytes, name)

/**
 * Internal helper to unify the implementation of asset loading and registering.
 *
 * It handles the registration and un-registration of the asset if a [key] is provided, and it
 * deletes the asset when the composable leaves the composition.
 *
 * @param commandQueue The command queue that owns and performs operations on this asset.
 * @param bytes The byte array containing the asset data to decode.
 * @param constructFn The function to use to decode and construct the asset from the given
 *    [commandQueue], [bytes], and optional [key].
 * @param key The key of the asset, used for registration. If null, the asset will not be
 *    registered.
 * @return A [Result] containing the result of the asset decoding (and possible registration), which
 *    can be either loading, error, or success with the decoded asset.
 */
@Composable
private fun <T : Asset<H>, H> rememberAsset(
    commandQueue: CommandQueue,
    bytes: ByteArray,
    constructFn: (suspend (CommandQueue, ByteArray) -> Result<T>),
    key: String? = null
): Result<T> = produceState<Result<T>>(Result.Loading, commandQueue, bytes, key) {
    val asset = constructFn(commandQueue, bytes)

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

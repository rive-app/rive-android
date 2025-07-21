package app.rive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import app.rive.core.AudioHandle
import app.rive.core.CommandQueue
import app.rive.core.FontHandle
import app.rive.core.ImageHandle
import kotlin.coroutines.cancellation.CancellationException

const val IMAGE_TAG = "Rive/Image"
const val AUDIO_TAG = "Rive/Audio"
const val FONT_TAG = "Rive/Font"

/**
 * WARNING: This function is not yet functional. It will be implemented in a future release.
 *
 * Decode an image from the given [bytes] on the provided [commandQueue]. The decoded image can only
 * be used on the same [CommandQueue] it was created on.
 *
 * This function is intended for use with images that may want to be registered multiple times
 * with [CommandQueue.registerImage]. If you want to decode and register in one step, use
 * [rememberRegisteredImage] instead.
 *
 * The image will be deleted when the composable leaves the composition.
 *
 * @param commandQueue The command queue that owns and performs operations on this image.
 * @param bytes The byte array containing the image data to decode.
 * @return A [State] containing the result of the image decoding, which can be either loading,
 *    error, or success with the [ImageHandle].
 */
@ExperimentalRiveComposeAPI
@Composable
@Throws(NotImplementedError::class)
fun rememberImage(
    commandQueue: CommandQueue,
    bytes: ByteArray,
): State<Result<ImageHandle>> {
    TODO("Image decoding is not yet functional in the Rive Compose library. It will be implemented in a future release.")
    return rememberAsset(
        commandQueue,
        bytes,
        decodeFn = { commandQueue.decodeImage(it) },
        deleteFn = { commandQueue.deleteImage(it) },
        tag = IMAGE_TAG,
        assetLabel = "image",
    )
}

/**
 * WARNING: This function is not yet functional. It will be implemented in a future release.
 *
 * Decode and register an image from the given [bytes] on the provided [commandQueue]. The decoded
 * image can only be used on the same [CommandQueue] it was created on.
 *
 * This function is intended for use with images that are registered once, as a convenience. If you
 * want to register multiple times, use [rememberImage] and [CommandQueue.registerImage] instead.
 *
 * The image will be deleted and unregistered when the composable leaves the composition.
 *
 * @param commandQueue The command queue that owns and performs operations on this image.
 * @param name The name of the referenced image. This comes from the zip file created when exporting
 *    a Rive file.
 * @param bytes The byte array containing the image data to decode.
 * @return A [State] containing the result of the image decoding and registration, which can be
 *    either loading, error, or success with the [ImageHandle].
 */
@ExperimentalRiveComposeAPI
@Composable
@Throws(NotImplementedError::class)
fun rememberRegisteredImage(
    commandQueue: CommandQueue,
    name: String,
    bytes: ByteArray,
): State<Result<ImageHandle>> {
    TODO("Image decoding is not yet functional in the Rive Compose library. It will be implemented in a future release.")
    return rememberAsset(
        commandQueue,
        name = name,
        bytes = bytes,
        decodeFn = { commandQueue.decodeImage(it) },
        deleteFn = { commandQueue.deleteImage(it) },
        registerFn = { key, handle -> commandQueue.registerImage(key, handle) },
        unregisterFn = { key -> commandQueue.unregisterImage(key) },
        tag = IMAGE_TAG,
        assetLabel = "image",
    )
}

/**
 * Decode audio from the given [bytes] on the provided [commandQueue]. The decoded audio can only be
 * used on the same [CommandQueue] it was created on.
 *
 * This function is intended for use with audio that may want to be registered multiple times
 * with [CommandQueue.registerAudio]. If you want to decode and register in one step, use
 * [rememberRegisteredAudio] instead.
 *
 * The audio will be deleted when the composable leaves the composition.
 *
 * @param commandQueue The command queue that owns and performs operations on this audio.
 * @param bytes The byte array containing the audio data to decode.
 * @return A [State] containing the result of the audio decoding, which can be either loading,
 *    error, or success with the [AudioHandle].
 */
@ExperimentalRiveComposeAPI
@Composable
fun rememberAudio(
    commandQueue: CommandQueue,
    bytes: ByteArray,
): State<Result<AudioHandle>> =
    rememberAsset(
        commandQueue,
        bytes,
        decodeFn = { commandQueue.decodeAudio(it) },
        deleteFn = { commandQueue.deleteAudio(it) },
        tag = AUDIO_TAG,
        assetLabel = "audio",
    )

/**
 * Decode and register audio from the given [bytes] on the provided [commandQueue]. The decoded
 * audio can only be used on the same [CommandQueue] it was created on.
 *
 * This function is intended for use with audio that is registered once, as a convenience. If you
 * want to register multiple times, use [rememberAudio] and [CommandQueue.registerAudio] instead.
 *
 * The audio will be deleted and unregistered when the composable leaves the composition.
 *
 * @param commandQueue The command queue that owns and performs operations on this audio.
 * @param name The name of the referenced audio. This comes from the zip file created when exporting
 *    a Rive file.
 * @param bytes The byte array containing the audio data to decode.
 * @return A [State] containing the result of the audio decoding and registration, which can be
 *    either loading, error, or success with the [AudioHandle].
 */
@ExperimentalRiveComposeAPI
@Composable
fun rememberRegisteredAudio(
    commandQueue: CommandQueue,
    name: String,
    bytes: ByteArray,
): State<Result<AudioHandle>> =
    rememberAsset(
        commandQueue,
        name = name,
        bytes = bytes,
        decodeFn = { commandQueue.decodeAudio(it) },
        deleteFn = { commandQueue.deleteAudio(it) },
        registerFn = { key, handle -> commandQueue.registerAudio(key, handle) },
        unregisterFn = { key -> commandQueue.unregisterAudio(key) },
        tag = AUDIO_TAG,
        assetLabel = "audio",
    )

/**
 * Decode a font from the given [bytes] on the provided [commandQueue]. The decoded font can only be
 * used on the same [CommandQueue] it was created on.
 *
 * This function is intended for use with fonts that may want to be registered multiple times
 * with [CommandQueue.registerFont]. If you want to decode and register in one step, use
 * [rememberRegisteredFont] instead.
 *
 * The font will be deleted when the composable leaves the composition.
 *
 * @param commandQueue The command queue that owns and performs operations on this font.
 * @param bytes The byte array containing the font data to decode.
 * @return A [State] containing the result of the font decoding, which can be either loading, error,
 *    or success with the [FontHandle].
 */
@ExperimentalRiveComposeAPI
@Composable
fun rememberFont(
    commandQueue: CommandQueue,
    bytes: ByteArray,
): State<Result<FontHandle>> =
    rememberAsset(
        commandQueue,
        bytes,
        decodeFn = { commandQueue.decodeFont(it) },
        deleteFn = { commandQueue.deleteFont(it) },
        tag = FONT_TAG,
        assetLabel = "font",
    )

/**
 * Decode and register a font from the given [bytes] on the provided [commandQueue]. The decoded
 * font can only be used on the same [CommandQueue] it was created on.
 *
 * This function is intended for use with fonts that are registered once, as a convenience. If you
 * want to register multiple times, use [rememberFont] and [CommandQueue.registerFont] instead.
 *
 * The font will be deleted and unregistered when the composable leaves the composition.
 *
 * @param commandQueue The command queue that owns and performs operations on this font.
 * @param name The name of the referenced font. This comes from the zip file created when exporting
 *    a Rive file.
 * @param bytes The byte array containing the font data to decode.
 * @return A [State] containing the result of the font decoding and registration, which can be
 *    either loading, error, or success with the [FontHandle].
 */
@ExperimentalRiveComposeAPI
@Composable
fun rememberRegisteredFont(
    commandQueue: CommandQueue,
    name: String,
    bytes: ByteArray,
): State<Result<FontHandle>> =
    rememberAsset(
        commandQueue,
        name = name,
        bytes = bytes,
        decodeFn = { commandQueue.decodeFont(it) },
        deleteFn = { commandQueue.deleteFont(it) },
        registerFn = { key, handle -> commandQueue.registerFont(key, handle) },
        unregisterFn = { key -> commandQueue.unregisterFont(key) },
        tag = FONT_TAG,
        assetLabel = "font",
    )

/**
 * Internal helper to unify the implementation of the various asset loading and registering
 * functions.
 *
 * This function decodes an asset from the given [bytes] on the provided [commandQueue]. The decoded
 * asset can only be used on the same [CommandQueue] it was created on.
 *
 * It handles the registration and un-registration of the asset if a [name] is provided, and it
 * deletes the asset when the composable leaves the composition.
 *
 * @param commandQueue The command queue that owns and performs operations on this asset.
 * @param bytes The byte array containing the asset data to decode.
 * @param decodeFn The function to decode the asset from the byte array.
 * @param deleteFn The function to delete the asset.
 * @param tag The tag used for logging.
 * @param assetLabel A label for the asset type, used in logging.
 * @param name The name of the asset, used for registration. If null, the asset will not be
 *    registered.
 * @param registerFn The function to register the asset with the command queue. If null, the asset
 *    will not be registered.
 * @param unregisterFn The function to unregister the asset from the command queue. If null, the
 *    asset will not be unregistered.
 * @return A [State] containing the result of the asset decoding (and possible registration), which
 *    can be either loading, error, or success with the decoded asset.
 */
@Composable
private fun <T> rememberAsset(
    commandQueue: CommandQueue,
    bytes: ByteArray,
    decodeFn: suspend (ByteArray) -> T,
    deleteFn: (T) -> Unit,
    tag: String,
    assetLabel: String,
    name: String? = null,
    registerFn: ((String, T) -> Unit)? = null,
    unregisterFn: ((String) -> Unit)? = null,
): State<Result<T>> = produceState<Result<T>>(Result.Loading, commandQueue, bytes, name) {
    require((name == null) == (registerFn == null) && (name == null) == (unregisterFn == null)) {
        "Either provide name/registerFn/unregisterFn all together, or none."
    }

    RiveLog.v(COMMAND_QUEUE_TAG) {
        "Acquiring command queue from $assetLabel " +
                "(ref count before acquire: ${commandQueue.refCount})"
    }
    commandQueue.acquire()

    fun release(label: String) {
        RiveLog.v(COMMAND_QUEUE_TAG) {
            "Releasing command queue from $assetLabel ($label) " +
                    "(remaining ref count before release: ${commandQueue.refCount})"
        }
        commandQueue.release()
    }

    val handle = try {
        RiveLog.d(tag) { "Decoding $assetLabel" }
        decodeFn(bytes).also { handle -> RiveLog.d(tag) { "Created $handle" } }
    } catch (ce: CancellationException) {
        RiveLog.d(tag) { "Decoding $assetLabel was cancelled." }
        release("cancellation")
        throw ce // Propagate cancellation exceptions
    } catch (e: Exception) {
        RiveLog.e(tag, e) { "Failed to decode $assetLabel." }
        value = Result.Error(e)
        release("error")
        return@produceState
    }

    if (registerFn != null && name != null) {
        RiveLog.d(tag) { "Registering $assetLabel with key: $name and handle: $handle" }
        registerFn(name, handle)
    }

    value = Result.Success(handle)

    awaitDispose {
        if (unregisterFn != null && name != null) {
            RiveLog.d(tag) { "Unregistering $assetLabel with key: $name" }
            unregisterFn(name)
        }

        RiveLog.d(tag) { "Deleting $handle" }
        deleteFn(handle)

        release("dispose")
    }
}

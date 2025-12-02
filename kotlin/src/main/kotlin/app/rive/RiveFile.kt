package app.rive

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.produceState
import app.rive.core.CloseOnce
import app.rive.core.CommandQueue
import app.rive.core.FileHandle
import app.rive.core.SuspendLazy
import app.rive.runtime.kotlin.core.File.Enum
import app.rive.runtime.kotlin.core.ViewModel.Property
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

private const val FILE_TAG = "Rive/File"

/**
 * A Rive file which contains one or more artboards, state machines, and view model instances.
 *
 * A Rive file is created from the Rive editor and is exported as a `.riv` file.
 *
 * Create an instance of this class using [rememberRiveFile] or [RiveFile.fromSource]. When using
 * the latter, make sure to call [close] when you are done with the file to release its resources.
 *
 * The this object can be used to query the file for its contents, such as artboards names. It can
 * then be passed to [rememberArtboard] to create an [Artboard], and then to [RiveUI] for rendering.
 *
 * Queries are cached for performance.
 *
 * @param fileHandle The handle to the file on the command server.
 * @param commandQueue The command queue that owns and performs operations on this file.
 */
@Stable
class RiveFile internal constructor(
    internal val fileHandle: FileHandle,
    internal val commandQueue: CommandQueue
) : AutoCloseable by CloseOnce({
    RiveLog.d(FILE_TAG) { "Deleting $fileHandle" }
    commandQueue.deleteFile(fileHandle)

    commandQueue.release(FILE_TAG, "RiveFile closed")
}) {
    companion object {
        /**
         * Loads a [RiveFile] from the given [source].
         *
         * The lifetime of the [RiveFile] is managed by the caller. Make sure to call [close] when
         * you are done with the file to release its resources.
         *
         * @param source The source of the Rive file.
         * @param commandQueue The command queue that owns the file.
         * @return The loaded Rive file, or an error if loading failed.
         */
        suspend fun fromSource(
            source: RiveFileSource,
            commandQueue: CommandQueue
        ): Result<RiveFile> {
            RiveLog.d(FILE_TAG) { "Loading Rive file from source: $source" }
            return try {
                commandQueue.acquire(FILE_TAG)

                val fileBytes = when (source) {
                    is RiveFileSource.Bytes -> source.data
                    is RiveFileSource.RawRes -> {
                        // Use an I/O worker to load the raw resource bytes
                        withContext(Dispatchers.IO) {
                            source.resources.openRawResource(source.resId)
                                .use { it.readBytes() }
                        }
                    }
                }
                RiveLog.v(FILE_TAG) { "Loaded Rive file bytes from source: $source; sending to command queue" }
                val fileHandle = commandQueue.loadFile(fileBytes)

                RiveLog.d(FILE_TAG) { "Loaded Rive file from source: $source; $fileHandle" }
                Result.Success(RiveFile(fileHandle, commandQueue))
            } catch (ce: CancellationException) {
                // Thrown by withContext if the coroutine is cancelled
                RiveLog.d(FILE_TAG) { "Rive file loading was cancelled: $source" }
                commandQueue.release(FILE_TAG, "Cancellation")
                // Propagate the cancellation exception, needed by callers to handle cancellation correctly
                throw ce
            } catch (e: Exception) {
                RiveLog.e(FILE_TAG, e) { "Error loading Rive file with source: $source" }
                commandQueue.release(FILE_TAG, "Load error")
                Result.Error(e)
            }
        }
    }

    /** @return A list of all exported artboard names available on this file. */
    suspend fun getArtboardNames(): List<String> = artboardNamesCache.await()
    private val artboardNamesCache = SuspendLazy {
        commandQueue.getArtboardNames(fileHandle)
    }

    /** @return A list of all view model names available on this file. */
    suspend fun getViewModelNames(): List<String> = viewModelNamesCache.await()
    private val viewModelNamesCache = SuspendLazy {
        commandQueue.getViewModelNames(fileHandle)
    }

    /** @return A list of all instance names available on the given [viewModel]. */
    suspend fun getViewModelInstanceNames(viewModel: String): List<String> =
        synchronized(instanceNamesCache) {
            instanceNamesCache.getOrPut(viewModel) {
                SuspendLazy {
                    commandQueue.getViewModelInstanceNames(fileHandle, viewModel)
                }
            }
        }.await()

    private val instanceNamesCache = mutableMapOf<String, SuspendLazy<List<String>>>()

    /**
     * @return A list of all properties available on the given [viewModel].
     * @see [Property]
     */
    suspend fun getViewModelProperties(viewModel: String): List<Property> =
        synchronized(propertiesCache) {
            propertiesCache.getOrPut(viewModel) {
                SuspendLazy {
                    commandQueue.getViewModelProperties(fileHandle, viewModel)
                }
            }
        }.await()

    private val propertiesCache = mutableMapOf<String, SuspendLazy<List<Property>>>()

    /**
     * @return A list of all enums available on this file.
     * @see [Enum]
     */
    suspend fun getEnums(): List<Enum> = enumsCache.await()
    private val enumsCache = SuspendLazy {
        commandQueue.getEnums(fileHandle)
    }
}

/**
 * The source for a [RiveFile].
 * - If you have loaded the Rive file yourself, use [RiveFileSource.Bytes].
 * - If the file is included as a raw resource in your Android project, use [RiveFileSource.RawRes].
 */
sealed interface RiveFileSource {
    @JvmInline
    value class Bytes(val data: ByteArray) : RiveFileSource

    data class RawRes(
        @androidx.annotation.RawRes val resId: Int,
        val resources: Resources
    ) : RiveFileSource
}

/**
 * Loads a [RiveFile] from the given [source].
 *
 * The lifetime of the [RiveFile] is managed by this composable. It will release the resources
 * allocated to the file when it falls out of scope.
 *
 * @param source The source of the Rive file, which can be a byte array or a raw resource ID.
 * @param commandQueue The command queue that owns the file. If not provided, a new command queue
 *    will be created and remembered.
 * @return The [Result] of loading the Rive file, which can be either loading, error, or success
 *    with the [RiveFile].
 */
@ExperimentalRiveComposeAPI
@Composable
fun rememberRiveFile(
    source: RiveFileSource,
    commandQueue: CommandQueue = rememberCommandQueue(),
): Result<RiveFile> = produceState<Result<RiveFile>>(Result.Loading, source) {
    val result = RiveFile.fromSource(source, commandQueue)
    value = result

    when (result) {
        is Result.Success -> {
            awaitDispose {
                result.value.close()
            }
        }

        else -> {}
    }
}.value

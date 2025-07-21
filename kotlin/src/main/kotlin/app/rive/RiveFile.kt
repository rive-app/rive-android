package app.rive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import app.rive.core.CommandQueue
import app.rive.core.FileHandle
import app.rive.runtime.kotlin.core.File.Enum
import app.rive.runtime.kotlin.core.ViewModel.Property
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * A Rive file, which contains one or more artboards, state machines, and view model instances.
 *
 * A Rive file is created from the Rive editor, which is exported as a `.riv` file.
 *
 * The this object can be used to query the file for its contents, such as artboards names. It can
 * then be passed to [rememberArtboard] to create an [Artboard], and then to [RiveUI] for rendering.
 *
 * Queries are cached for performance.
 *
 * @param fileHandle The handle to the file on the command server.
 * @param commandQueue The command queue that owns and performs operations on this file.
 * @param parentScope The coroutine scope to use for launching coroutines.
 */
@Stable
class RiveFile(
    internal val fileHandle: FileHandle,
    internal val commandQueue: CommandQueue,
    private val parentScope: CoroutineScope,
) {
    /** @return A list of all exported artboard names available on this file. */
    suspend fun getArtboardNames(): List<String> = artboardNamesCache.await()
    private val artboardNamesCache by lazyDeferred<List<String>>(parentScope) {
        commandQueue.getArtboardNames(fileHandle)
    }

    /** @return A list of all view model names available on this file. */
    suspend fun getViewModelNames(): List<String> = viewModelNamesCache.await()
    private val viewModelNamesCache by lazyDeferred<List<String>>(parentScope) {
        commandQueue.getViewModelNames(fileHandle)
    }

    /** @return A list of all instance names available on the given [viewModel]. */
    suspend fun getViewModelInstanceNames(viewModel: String): List<String> =
        synchronized(instanceNamesCache) {
            instanceNamesCache.getOrPut(viewModel) {
                lazyDeferred(parentScope) {
                    commandQueue.getViewModelInstanceNames(fileHandle, viewModel)
                }
            }
        }.value.await()

    private val instanceNamesCache = mutableMapOf<String, Lazy<Deferred<List<String>>>>()

    /**
     * @return A list of all properties available on the given [viewModel].
     * @see [Property]
     */
    suspend fun getViewModelProperties(viewModel: String): List<Property> =
        synchronized(propertiesCache) {
            propertiesCache.getOrPut(viewModel) {
                lazyDeferred(parentScope) {
                    commandQueue.getViewModelProperties(fileHandle, viewModel)
                }
            }
        }.value.await()

    private val propertiesCache = mutableMapOf<String, Lazy<Deferred<List<Property>>>>()

    /**
     * @return A list of all enums available on this file.
     * @see [Enum]
     */
    suspend fun getEnums(): List<Enum> = enumsCache.await()
    private val enumsCache by lazyDeferred<List<Enum>>(parentScope) {
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

    @JvmInline
    value class RawRes(@androidx.annotation.RawRes val resId: Int) : RiveFileSource
}

private const val FILE_TAG = "Rive/File"

/**
 * Loads a [RiveFile] from the given [source].
 *
 * The lifetime of the [RiveFile] is managed by this composable. It will release the resources
 * allocated to the file when it falls out of scope.
 *
 * @param source The source of the Rive file, which can be a byte array or a raw resource ID.
 * @param commandQueue The command queue that owns the file. If not provided, a new command queue
 *    will be created and remembered.
 */
@ExperimentalRiveComposeAPI
@Composable
fun rememberRiveFile(
    source: RiveFileSource,
    commandQueue: CommandQueue = rememberCommandQueue(),
): State<Result<RiveFile>> {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    return produceState<Result<RiveFile>>(Result.Loading, source) {
        RiveLog.v(COMMAND_QUEUE_TAG) { "Acquiring command queue from Rive File (ref count before acquire: ${commandQueue.refCount})" }
        commandQueue.acquire()
        var acquired = true

        fun release(label: String) {
            if (acquired) {
                RiveLog.v(COMMAND_QUEUE_TAG) { "Releasing command queue from Rive File ($label) (ref before: ${commandQueue.refCount})" }
                commandQueue.release()
                acquired = false
            }
        }

        RiveLog.d(FILE_TAG) { "Loading Rive file from source: $source" }
        try {
            val fileHandle = when (source) {
                is RiveFileSource.Bytes -> commandQueue.loadFile(source.data)

                is RiveFileSource.RawRes -> {
                    // Use an I/O worker to load the raw resource and file
                    withContext(Dispatchers.IO) {
                        val bytes =
                            context.resources.openRawResource(source.resId).use { it.readBytes() }
                        commandQueue.loadFile(bytes)
                    }
                }
            }

            RiveLog.d(FILE_TAG) { "Loaded Rive file from source: $source; $fileHandle" }
            value = Result.Success(RiveFile(fileHandle, commandQueue, coroutineScope))

            awaitDispose {
                RiveLog.d(FILE_TAG) { "Deleting $fileHandle" }
                commandQueue.deleteFile(fileHandle)
                release("dispose")
            }
        } catch (ce: CancellationException) {
            // Thrown by withContext if the coroutine is cancelled
            RiveLog.d(FILE_TAG) { "Rive file loading was cancelled: $source" }
            release("cancellation")
            // Propagate the cancellation exception, needed by Compose to handle cancellation correctly
            throw ce
        } catch (e: Exception) {
            RiveLog.e(FILE_TAG, e) { "Error loading Rive file with source: $source" }
            value = Result.Error(e)
            release("error")
        }
    }
}

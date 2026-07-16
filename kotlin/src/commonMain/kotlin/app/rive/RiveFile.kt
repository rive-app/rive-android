package app.rive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.produceState
import app.rive.core.ArtboardHandle
import app.rive.core.CloseOnce
import app.rive.core.DefaultViewModelInfo
import app.rive.core.FileHandle
import app.rive.core.RiveWorker
import app.rive.core.SuspendLazy
import app.rive.core.FileEnum
import app.rive.core.ViewModelProperty
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.jvm.JvmInline
import kotlin.coroutines.cancellation.CancellationException

private const val FILE_TAG = "Rive/File"

/**
 * A Rive file which contains one or more artboards, state machines, and view model instances.
 *
 * A Rive file is created from the Rive editor and is exported as a `.riv` file.
 *
 * Create an instance of this class using [rememberRiveFile] or [RiveFile.fromSource]. When using
 * the latter, make sure to call [close] when you are done with the file to release its resources.
 * A manually-created file holds a reference to its [RiveWorker], so releasing the worker alone is
 * not enough to purge the file or worker memory while this file remains open.
 *
 * This object can be used to query the file for its contents, such as artboards names. It can then
 * be passed to [rememberArtboard] to create an [Artboard], and then to [Rive] for rendering.
 *
 * Queries are cached for performance.
 *
 * @param fileHandle The handle to the file on the command server.
 * @param riveWorker The Rive worker that owns and performs operations on this file.
 */
@Stable
class RiveFile internal constructor(
    val fileHandle: FileHandle,
    val riveWorker: RiveWorker
) : AutoCloseable by CloseOnce("$fileHandle", {
    RiveLog.d(FILE_TAG) { "Deleting $fileHandle" }
    riveWorker.deleteFile(fileHandle)

    riveWorker.release(FILE_TAG, "RiveFile closed")
}) {
    companion object {
        /**
         * Loads a [RiveFile] from the given [source].
         *
         * ⚠️ The lifetime of the [RiveFile] is managed by the caller. Make sure to call [close]
         * when you are done with the file to release its resources. The returned file holds a
         * reference to [riveWorker], so closing it is required before the worker can fully release
         * memory associated with this file.
         *
         * @param source The source of the Rive file.
         * @param riveWorker The Rive worker that owns the file.
         * @return The loaded Rive file, or an error if loading failed. The Loading state is not
         *    used here since the loading is performed in a suspend function.
         */
        suspend fun fromSource(
            source: RiveFileSource,
            riveWorker: RiveWorker
        ): Result<RiveFile> {
            RiveLog.d(FILE_TAG) { "Loading Rive file from source: $source" }
            return try {
                riveWorker.acquire(FILE_TAG)

                val fileBytes = source.load()
                RiveLog.v(FILE_TAG) { "Loaded Rive file bytes from source: $source; sending to Rive worker" }
                val fileHandle = riveWorker.loadFile(fileBytes)

                RiveLog.d(FILE_TAG) { "Loaded Rive file from source: $source; $fileHandle" }
                Result.Success(RiveFile(fileHandle, riveWorker))
            } catch (ce: CancellationException) {
                // Thrown by withContext if the coroutine is cancelled
                RiveLog.d(FILE_TAG) { "Rive file loading was cancelled: $source" }
                riveWorker.release(FILE_TAG, "Cancellation")
                // Propagate the cancellation exception, needed by callers to handle cancellation correctly
                throw ce
            } catch (e: Exception) {
                RiveLog.e(FILE_TAG, e) { "Error loading Rive file with source: $source" }
                riveWorker.release(FILE_TAG, "Load error")
                Result.Error(e)
            }
        }
    }

    /** @return A list of all exported artboard names available on this file. */
    suspend fun getArtboardNames(): List<String> = artboardNamesCache.await()
    private val artboardNamesCache = SuspendLazy {
        riveWorker.getArtboardNames(fileHandle)
    }

    /** @return A list of all view model names available on this file. */
    suspend fun getViewModelNames(): List<String> = viewModelNamesCache.await()
    private val viewModelNamesCache = SuspendLazy {
        riveWorker.getViewModelNames(fileHandle)
    }

    /**
     * @param viewModel The name of the view model to get instance names for.
     * @return A list of all instance names available on the given view model.
     */
    suspend fun getViewModelInstanceNames(viewModel: String): List<String> =
        cacheMutex.withLock {
            instanceNamesCache.getOrPut(viewModel) {
                SuspendLazy {
                    riveWorker.getViewModelInstanceNames(fileHandle, viewModel)
                }
            }
        }.await()

    // Guards the query caches below, which may be populated from concurrent coroutines.
    private val cacheMutex = Mutex()

    private val instanceNamesCache = mutableMapOf<String, SuspendLazy<List<String>>>()

    /**
     * @param viewModel The name of the view model to get properties for.
     * @return A list of all properties available on the given view model.
     * @see [ViewModelProperty]
     */
    suspend fun getViewModelProperties(viewModel: String): List<ViewModelProperty> =
        cacheMutex.withLock {
            propertiesCache.getOrPut(viewModel) {
                SuspendLazy {
                    riveWorker.getViewModelProperties(fileHandle, viewModel)
                }
            }
        }.await()

    private val propertiesCache = mutableMapOf<String, SuspendLazy<List<ViewModelProperty>>>()

    /**
     * @return A list of all enums available on this file.
     * @see [FileEnum]
     */
    suspend fun getEnums(): List<FileEnum> = enumsCache.await()
    private val enumsCache = SuspendLazy {
        riveWorker.getEnums(fileHandle)
    }

    /**
     * @param artboard The artboard to query for default view model information.
     * @return A [DefaultViewModelInfo] containing the view model name and instance name.
     */
    suspend fun getDefaultViewModelInfo(artboard: Artboard): DefaultViewModelInfo =
        cacheMutex.withLock {
            defaultViewModelInfoCache.getOrPut(artboard.artboardHandle) {
                SuspendLazy {
                    riveWorker.getDefaultViewModelInfo(fileHandle, artboard.artboardHandle)
                }
            }
        }.await()

    private val defaultViewModelInfoCache =
        mutableMapOf<ArtboardHandle, SuspendLazy<DefaultViewModelInfo>>()
}

/**
 * The source for a [RiveFile].
 * - If you have loaded the Rive file yourself, use [RiveFileSource.Bytes].
 * - If the file is included as a raw resource in your Android project, use `RawRes` (Android).
 *
 * Platforms and apps can provide additional sources by implementing [load].
 */
interface RiveFileSource {
    /** Produces the bytes of the `.riv` file. May perform I/O. */
    suspend fun load(): ByteArray

    @JvmInline
    value class Bytes(val data: ByteArray) : RiveFileSource {
        override suspend fun load(): ByteArray = data
    }
}

/**
 * Loads a [RiveFile] from the given [source].
 *
 * The lifetime of the [RiveFile] is managed by this composable. It will release the resources
 * allocated to the file, including its reference to [riveWorker], when it falls out of scope.
 *
 * @param source The source of the Rive file, which can be a byte array or a raw resource ID.
 * @param riveWorker The Rive worker that owns the file.
 * @return The [Result] of loading the Rive file, which can be either loading, error, or success
 *    with the [RiveFile].
 */
@Composable
fun rememberRiveFile(
    source: RiveFileSource,
    riveWorker: RiveWorker,
): Result<RiveFile> = produceState<Result<RiveFile>>(Result.Loading, source, riveWorker) {
    val result = RiveFile.fromSource(source, riveWorker)
    value = result

    when (result) {
        is Result.Success -> awaitDispose {
            result.value.close()
        }

        else -> {}
    }
}.value

package app.rive

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import app.rive.core.CloseOnce
import app.rive.core.FileHandle
import app.rive.core.RiveWorker
import app.rive.core.SuspendLazy
import app.rive.runtime.kotlin.core.File.Enum
import app.rive.runtime.kotlin.core.ViewModel.Property
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import androidx.annotation.RawRes as RawResource

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
 * then be passed to [rememberArtboard] to create an [Artboard], and then to [Rive] for rendering.
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
         * when you are done with the file to release its resources.
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
        synchronized(instanceNamesCache) {
            instanceNamesCache.getOrPut(viewModel) {
                SuspendLazy {
                    riveWorker.getViewModelInstanceNames(fileHandle, viewModel)
                }
            }
        }.await()

    private val instanceNamesCache = mutableMapOf<String, SuspendLazy<List<String>>>()

    /**
     * @param viewModel The name of the view model to get properties for.
     * @return A list of all properties available on the given view model.
     * @see [Property]
     */
    suspend fun getViewModelProperties(viewModel: String): List<Property> =
        synchronized(propertiesCache) {
            propertiesCache.getOrPut(viewModel) {
                SuspendLazy {
                    riveWorker.getViewModelProperties(fileHandle, viewModel)
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
        riveWorker.getEnums(fileHandle)
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
        @param:RawResource val resId: Int,
        val resources: Resources
    ) : RiveFileSource {
        companion object {
            /**
             * Convenience function for Compose contexts to create a [RawRes] instance.
             *
             * Uses the current Compose [LocalContext] to obtain [Resources], avoiding the need to
             * pass it manually.
             *
             * @param resId The resource ID of the raw Rive file.
             * @return A [RawRes] instance with the given resource ID and the current [Resources].
             */
            @Composable
            fun from(@RawResource resId: Int) = RawRes(resId, LocalContext.current.resources)
        }
    }
}

/**
 * Loads a [RiveFile] from the given [source].
 *
 * The lifetime of the [RiveFile] is managed by this composable. It will release the resources
 * allocated to the file when it falls out of scope.
 *
 * @param source The source of the Rive file, which can be a byte array or a raw resource ID.
 * @param riveWorker The Rive worker that owns the file.
 * @return The [Result] of loading the Rive file, which can be either loading, error, or success
 *    with the [RiveFile].
 */
@ExperimentalRiveComposeAPI
@Composable
fun rememberRiveFile(
    source: RiveFileSource,
    riveWorker: RiveWorker,
): Result<RiveFile> = produceState<Result<RiveFile>>(Result.Loading, source) {
    val result = RiveFile.fromSource(source, riveWorker)
    value = result

    when (result) {
        is Result.Success -> awaitDispose {
            result.value.close()
        }

        else -> {}
    }
}.value

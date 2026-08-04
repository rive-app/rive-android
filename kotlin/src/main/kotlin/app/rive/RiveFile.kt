package app.rive

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import app.rive.core.ArtboardHandle
import app.rive.core.CloseOnce
import app.rive.core.DefaultViewModelInfo
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
) : AutoCloseable {
    private val closer = CloseOnce("$fileHandle") {
        RiveLog.d(FILE_TAG) { "Deleting $fileHandle" }
        riveWorker.deleteFile(fileHandle)

        riveWorker.release(FILE_TAG, "RiveFile closed")
    }

    /** Closes this file and schedules deletion on its Rive worker. */
    override fun close() = closer.close()

    /**
     * Ensures this file has not been closed.
     *
     * @throws RiveResourceClosedException If this file has already been closed.
     */
    @Throws(RiveResourceClosedException::class)
    internal fun checkOpen() = closer.checkOpen()

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
         * @throws CancellationException If the coroutine is cancelled before loading completes.
         */
        @Throws(CancellationException::class)
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

    /**
     * @return A list of all exported artboard names available on this file.
     * @throws RiveResourceClosedException If this file has been closed or its Rive worker has been
     *    disposed.
     * @throws RiveFileException If the file operation fails.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     */
    @Throws(RiveFileException::class, RiveResourceClosedException::class, CancellationException::class)
    suspend fun getArtboardNames(): List<String> {
        closer.checkOpen()
        return artboardNamesCache.await()
    }

    private val artboardNamesCache = SuspendLazy {
        riveWorker.getArtboardNames(fileHandle)
    }

    /**
     * @return A list of all view model names available on this file.
     * @throws RiveResourceClosedException If this file has been closed or its Rive worker has been
     *    disposed.
     * @throws RiveFileException If the file operation fails.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     */
    @Throws(RiveFileException::class, RiveResourceClosedException::class, CancellationException::class)
    suspend fun getViewModelNames(): List<String> {
        closer.checkOpen()
        return viewModelNamesCache.await()
    }

    private val viewModelNamesCache = SuspendLazy {
        riveWorker.getViewModelNames(fileHandle)
    }

    /**
     * @param viewModel The name of the view model to get instance names for.
     * @return A list of all instance names available on the given view model.
     * @throws RiveResourceClosedException If this file has been closed or its Rive worker has been
     *    disposed.
     * @throws RiveFileException If the file operation fails.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     */
    @Throws(RiveFileException::class, RiveResourceClosedException::class, CancellationException::class)
    suspend fun getViewModelInstanceNames(viewModel: String): List<String> {
        closer.checkOpen()
        return synchronized(instanceNamesCache) {
            instanceNamesCache.getOrPut(viewModel) {
                SuspendLazy {
                    riveWorker.getViewModelInstanceNames(fileHandle, viewModel)
                }
            }
        }.await()
    }

    private val instanceNamesCache = mutableMapOf<String, SuspendLazy<List<String>>>()

    /**
     * @param viewModel The name of the view model to get properties for.
     * @return A list of all properties available on the given view model.
     * @throws RiveResourceClosedException If this file has been closed or its Rive worker has been
     *    disposed.
     * @throws RiveFileException If the file operation fails.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     * @see [Property]
     */
    @Throws(RiveFileException::class, RiveResourceClosedException::class, CancellationException::class)
    suspend fun getViewModelProperties(viewModel: String): List<Property> {
        closer.checkOpen()
        return synchronized(propertiesCache) {
            propertiesCache.getOrPut(viewModel) {
                SuspendLazy {
                    riveWorker.getViewModelProperties(fileHandle, viewModel)
                }
            }
        }.await()
    }

    private val propertiesCache = mutableMapOf<String, SuspendLazy<List<Property>>>()

    /**
     * @return A list of all enums available on this file.
     * @throws RiveResourceClosedException If this file has been closed or its Rive worker has been
     *    disposed.
     * @throws RiveFileException If the file operation fails.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     * @see [Enum]
     */
    @Throws(RiveFileException::class, RiveResourceClosedException::class, CancellationException::class)
    suspend fun getEnums(): List<Enum> {
        closer.checkOpen()
        return enumsCache.await()
    }

    private val enumsCache = SuspendLazy {
        riveWorker.getEnums(fileHandle)
    }

    /**
     * @param artboard The artboard to query for default view model information.
     * @return A [DefaultViewModelInfo] containing the view model name and instance name.
     * @throws RiveResourceClosedException If this file or [artboard] has been closed, or if the
     *    owning Rive worker has been disposed.
     * @throws RiveIncompatibleResourceException If [artboard] was created from another file.
     * @throws RiveArtboardException If the artboard operation fails.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     */
    @Throws(
        RiveIncompatibleResourceException::class,
        RiveArtboardException::class,
        RiveResourceClosedException::class,
        CancellationException::class
    )
    suspend fun getDefaultViewModelInfo(artboard: Artboard): DefaultViewModelInfo {
        closer.checkOpen()
        artboard.checkOpen()
        artboard.requireFromFile(this)
        return synchronized(defaultViewModelInfoCache) {
            defaultViewModelInfoCache.getOrPut(artboard.artboardHandle) {
                SuspendLazy {
                    riveWorker.getDefaultViewModelInfo(fileHandle, artboard.artboardHandle)
                }
            }
        }.await()
    }

    private val defaultViewModelInfoCache =
        mutableMapOf<ArtboardHandle, SuspendLazy<DefaultViewModelInfo>>()
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

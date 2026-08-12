package app.rive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import app.rive.core.ArtboardHandle
import app.rive.core.CheckableAutoCloseable
import app.rive.core.CloseOnce
import app.rive.core.FileHandle
import app.rive.core.RiveSurface
import app.rive.core.RiveWorker
import app.rive.core.SuspendLazy
import kotlin.coroutines.cancellation.CancellationException

private const val ARTBOARD_TAG = "Rive/Artboard"

/**
 * An instantiated artboard from a [RiveFile].
 *
 * Can be queried for state machine names, and used to create a [Rive] composable.
 *
 * Create an instance of this class using [rememberArtboardResult] or [Artboard.create]. When using
 * the latter, make sure to call [close] when you are done with the artboard to release its
 * resources.
 *
 * @param artboardHandle The handle to the artboard on the command server.
 * @param riveWorker The Rive worker that owns the artboard.
 */
class Artboard internal constructor(
    val artboardHandle: ArtboardHandle,
    internal val riveWorker: RiveWorker,
    internal val fileHandle: FileHandle,
    val name: String?,
) : CheckableAutoCloseable {
    private val closer = CloseOnce("$artboardHandle") {
        val nameLog = name?.let { "with name $it" } ?: "(default)"
        RiveLog.d(ARTBOARD_TAG) { "Deleting $artboardHandle $nameLog (${fileHandle})" }
        riveWorker.deleteArtboard(artboardHandle)
    }

    /**
     * Closes this artboard and schedules deletion on its Rive worker.
     *
     * @throws RiveResourceClosedException If the owning Rive worker has been disposed.
     */
    @Throws(RiveResourceClosedException::class)
    override fun close() = closer.close()

    /** Whether this artboard has been closed. */
    override val closed: Boolean
        get() = closer.closed

    /**
     * Ensures this artboard has not been closed.
     *
     * @throws RiveResourceClosedException If this artboard has already been closed.
     */
    @Throws(RiveResourceClosedException::class)
    internal fun checkOpen() = closer.checkOpen()

    companion object {
        /**
         * Creates a new [Artboard] and suspends until its Rive worker confirms creation.
         *
         * This is the replacement for [fromFile]. Its temporary name avoids a source-incompatible
         * overload; it will be renamed to `fromFile` in 12.0 when the unconfirmed API is removed.
         *
         * ⚠️ The lifetime of a successfully created artboard is managed by the caller. Make sure
         * to call [close] when you are done with it to release its resources.
         *
         * @param file The [RiveFile] to instantiate the artboard from.
         * @param artboardName The name of the artboard to load. If null, the default artboard will
         *    be loaded.
         * @return The created artboard.
         * @throws RiveFileException If the file or requested artboard cannot be resolved.
         * @throws RiveResourceClosedException If [file] has been closed or its Rive worker has
         *    been disposed.
         * @throws CancellationException If the coroutine is cancelled before creation is
         *    confirmed.
         */
        @Throws(
            RiveFileException::class,
            RiveResourceClosedException::class,
            CancellationException::class
        )
        suspend fun create(
            file: RiveFile,
            artboardName: String? = null
        ): Artboard {
            val nameLog = artboardName?.let { "with name $it" } ?: "(default)"
            RiveLog.d(ARTBOARD_TAG) {
                "Creating artboard $nameLog (${file.fileHandle})"
            }
            return try {
                file.checkOpen()
                val handle = artboardName?.let { name ->
                    file.riveWorker.createArtboardByNameConfirmed(file.fileHandle, name)
                } ?: file.riveWorker.createDefaultArtboardConfirmed(file.fileHandle)
                RiveLog.d(ARTBOARD_TAG) {
                    "Created $handle $nameLog (${file.fileHandle})"
                }
                Artboard(handle, file.riveWorker, file.fileHandle, artboardName)
            } catch (ce: CancellationException) {
                RiveLog.d(ARTBOARD_TAG) {
                    "Artboard creation was cancelled $nameLog (${file.fileHandle})"
                }
                throw ce
            } catch (e: Exception) {
                RiveLog.e(ARTBOARD_TAG, e) {
                    "Error creating artboard $nameLog (${file.fileHandle})"
                }
                throw e
            }
        }

        /**
         * Creates a new [Artboard].
         *
         * ⚠️ The lifetime of the returned artboard is managed by the caller. Make sure to call
         * [close] when you are done with it to release its resources.
         *
         * @param file The [RiveFile] to instantiate the artboard from.
         * @param artboardName The name of the artboard to load. If null, the default artboard will
         *    be loaded.
         * @return The created artboard.
         * @throws RiveResourceClosedException If [file] has been closed or its Rive worker has been
         *    disposed.
         * @deprecated Use [create]. This unconfirmed implementation will be removed in 12.0, when
         *    [create] will be renamed to `fromFile` as a suspending API.
         */
        @Throws(RiveResourceClosedException::class)
        @Deprecated(
            "Use create. This unconfirmed implementation will be removed in 12.0, when create " +
                "will be renamed to fromFile as a suspending API."
        )
        @Suppress("DEPRECATION")
        fun fromFile(
            file: RiveFile,
            artboardName: String? = null
        ): Artboard {
            file.checkOpen()
            val handle = artboardName?.let { name ->
                file.riveWorker.createArtboardByName(file.fileHandle, name)
            } ?: file.riveWorker.createDefaultArtboard(file.fileHandle)
            val nameLog = artboardName?.let { "with name $it" } ?: "(default)"
            RiveLog.d(ARTBOARD_TAG) { "Created $handle $nameLog (${file.fileHandle})" }
            return Artboard(handle, file.riveWorker, file.fileHandle, artboardName)
        }
    }

    /**
     * Requires this artboard to be owned by [worker].
     *
     * This compatibility check assumes callers have already verified that participating resources
     * are open.
     *
     * @param worker The worker required to own this artboard.
     * @throws RiveIncompatibleResourceException If this artboard is owned by another worker.
     */
    @Throws(RiveIncompatibleResourceException::class)
    internal fun requireOwnedBy(worker: RiveWorker) {
        if (riveWorker !== worker) {
            throw RiveIncompatibleResourceException(
                "Artboard $artboardHandle is not owned by the required RiveWorker"
            )
        }
    }

    /**
     * Requires this artboard to have been created from [file].
     *
     * This compatibility check assumes callers have already verified that both resources are
     * open.
     *
     * @param file The file required to own this artboard.
     * @throws RiveIncompatibleResourceException If this artboard was created from another file.
     */
    @Throws(RiveIncompatibleResourceException::class)
    internal fun requireFromFile(file: RiveFile) {
        requireFromFile(file.riveWorker, file.fileHandle)
    }

    /**
     * Requires this artboard to have been created from [worker] and [expectedFileHandle].
     *
     * This overload supports worker entry points that identify a file by its owning worker and raw
     * handle.
     *
     * @param worker The worker required to own this artboard.
     * @param expectedFileHandle The file handle required to own this artboard.
     * @throws RiveIncompatibleResourceException If this artboard has different ownership.
     */
    @Throws(RiveIncompatibleResourceException::class)
    internal fun requireFromFile(worker: RiveWorker, expectedFileHandle: FileHandle) {
        if (riveWorker !== worker || fileHandle != expectedFileHandle) {
            throw RiveIncompatibleResourceException(
                "Artboard $artboardHandle was not created from RiveFile $expectedFileHandle"
            )
        }
    }

    /**
     * @return A list of all state machine names on this artboard.
     * @throws RiveResourceClosedException If this artboard has been closed or its Rive worker has
     *    been disposed.
     * @throws RiveArtboardException If the artboard operation fails.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     */
    @Throws(
        RiveArtboardException::class,
        RiveResourceClosedException::class,
        CancellationException::class
    )
    suspend fun getStateMachineNames(): List<String> {
        closer.checkOpen()
        return stateMachineNamesCache.await()
    }

    private val stateMachineNamesCache = SuspendLazy {
        riveWorker.getStateMachineNames(artboardHandle)
    }

    /**
     * Resizes this artboard to match the dimensions of the given surface, divided by the scale
     * factor.
     *
     * ℹ️ This is required when drawing with a fit type of [Fit.Layout], where the artboard is
     * expected to match the dimensions of the surface it is drawn to and layout its children within
     * those bounds.
     *
     * ⚠️ In order for this to take effect, the state machine associated to this artboard must be
     * advanced, even if just by 0.
     *
     * @param surface The surface whose width and height will be used to resize the artboard.
     * @param scaleFactor The scale factor to apply when resizing. The artboard will be resized to
     *    surface dimensions divided by this factor. Defaults to 1f.
     * @throws RiveResourceClosedException If this artboard or [surface] has been closed, or if the
     *    owning Rive worker has been disposed.
     * @throws RiveIncompatibleResourceException If [surface] is owned by another Rive worker.
     */
    @Throws(RiveIncompatibleResourceException::class, RiveResourceClosedException::class)
    fun resizeArtboard(
        surface: RiveSurface,
        scaleFactor: Float = 1f
    ) {
        closer.checkOpen()
        surface.checkOpen()
        surface.requireOwnedBy(riveWorker)
        riveWorker.resizeArtboard(artboardHandle, surface, scaleFactor)
    }

    /**
     * Resets this artboard to its original dimensions.
     *
     * ℹ️ This should be called if the artboard was previously resized with [resizeArtboard] and
     * you now want to draw with a fit type other than [Fit.Layout], to restore the artboard to its
     * original size.
     *
     * ⚠️ In order for this to take effect, the state machine associated to this artboard must be
     * advanced, even if just by 0.
     *
     * @throws RiveResourceClosedException If this artboard has been closed or its Rive worker has
     *    been disposed.
     */
    @Throws(RiveResourceClosedException::class)
    fun resetArtboardSize() {
        closer.checkOpen()
        riveWorker.resetArtboardSize(artboardHandle)
    }

    /**
     * Set this artboard's volume multiplier.
     *
     * This updates audio events driven by this artboard and any nested artboards that inherit its
     * volume. The operation is queued on this artboard's [RiveWorker].
     *
     * @param volume The volume multiplier to apply.
     * @throws RiveResourceClosedException If this artboard has been closed or its Rive worker has
     *    been disposed.
     */
    @Throws(RiveResourceClosedException::class)
    fun setVolume(volume: Float) {
        closer.checkOpen()
        riveWorker.setArtboardVolume(artboardHandle, volume)
    }

    /**
     * Get this artboard's current volume multiplier.
     *
     * This suspends while the value is requested from the Rive worker.
     *
     * @return The current artboard volume multiplier.
     * @throws RiveResourceClosedException If this artboard has been closed or its Rive worker has
     *    been disposed.
     * @throws RiveArtboardException If the artboard operation fails.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     */
    @Throws(
        RiveArtboardException::class,
        RiveResourceClosedException::class,
        CancellationException::class
    )
    suspend fun getVolume(): Float {
        closer.checkOpen()
        return riveWorker.getArtboardVolume(artboardHandle)
    }
}

/**
 * Creates an [Artboard] from the given [RiveFile].
 *
 * The lifetime of the artboard is managed by this composable. It will delete the artboard when it
 * falls out of scope.
 *
 * @param file The [RiveFile] to instantiate the artboard from.
 * @param artboardName The name of the artboard to load. If null, the default artboard will be
 *    loaded.
 * @return The created [Artboard].
 * @throws RiveResourceClosedException If [file] has been closed or its Rive worker has been
 *    disposed.
 * @deprecated Use [rememberArtboardResult]. This implementation will be removed in 12.0, when
 *    [rememberArtboardResult] will be renamed to `rememberArtboard` and return a [Result].
 */
@Throws(RiveResourceClosedException::class)
@Deprecated(
    "Use rememberArtboardResult. This implementation will be removed in 12.0, when " +
        "rememberArtboardResult will be renamed to rememberArtboard and return a Result."
)
@Suppress("DEPRECATION")
@Composable
fun rememberArtboard(
    file: RiveFile,
    artboardName: String? = null,
): Artboard {
    val artboard = remember(file, artboardName) {
        Artboard.fromFile(file, artboardName)
    }

    DisposableEffect(artboard) {
        onDispose { artboard.close() }
    }

    return artboard
}

/**
 * Creates an [Artboard] from [file] and exposes its creation state.
 *
 * The lifetime of a successfully created artboard is managed by this composable. It will delete
 * the artboard when it falls out of scope.
 *
 * This is the replacement for [rememberArtboard]; its temporary name will become
 * `rememberArtboard` in 12.0 when the unconfirmed API is removed.
 *
 * @param file The [RiveFile] to instantiate the artboard from.
 * @param artboardName The name of the artboard to load. If null, the default artboard will be
 *    loaded.
 * @return The current creation result: loading, error, or success with the created [Artboard].
 */
@Composable
fun rememberArtboardResult(
    file: RiveFile,
    artboardName: String? = null,
): Result<Artboard> = produceState<Result<Artboard>>(Result.Loading, file, artboardName) {
    val artboard = try {
        Artboard.create(file, artboardName)
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: Exception) {
        value = Result.Error(e)
        return@produceState
    }

    value = Result.Success(artboard)
    awaitDispose { artboard.close() }
}.value

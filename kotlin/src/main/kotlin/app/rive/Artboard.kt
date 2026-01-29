package app.rive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import app.rive.core.ArtboardHandle
import app.rive.core.CloseOnce
import app.rive.core.FileHandle
import app.rive.core.RiveSurface
import app.rive.core.RiveWorker
import app.rive.core.SuspendLazy

private const val ARTBOARD_TAG = "Rive/Artboard"

/**
 * An instantiated artboard from a [RiveFile].
 *
 * Can be queried for state machine names, and used to create a [Rive] composable.
 *
 * Create an instance of this class using [rememberArtboard] or [Artboard.fromFile]. When using the
 * latter, make sure to call [close] when you are done with the artboard to release its resources.
 *
 * @param artboardHandle The handle to the artboard on the command server.
 * @param riveWorker The Rive worker that owns the artboard.
 */
class Artboard internal constructor(
    val artboardHandle: ArtboardHandle,
    internal val riveWorker: RiveWorker,
    internal val fileHandle: FileHandle,
    val name: String?,
) : AutoCloseable by CloseOnce("$artboardHandle", {
    val nameLog = name?.let { "with name $it" } ?: "(default)"
    RiveLog.d(ARTBOARD_TAG) { "Deleting $artboardHandle $nameLog (${fileHandle})" }
    riveWorker.deleteArtboard(artboardHandle)
}) {
    companion object {
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
         */
        fun fromFile(
            file: RiveFile,
            artboardName: String? = null
        ): Artboard {
            val handle = artboardName?.let { name ->
                file.riveWorker.createArtboardByName(file.fileHandle, name)
            } ?: file.riveWorker.createDefaultArtboard(file.fileHandle)
            val nameLog = artboardName?.let { "with name $it" } ?: "(default)"
            RiveLog.d(ARTBOARD_TAG) { "Created $handle $nameLog (${file.fileHandle})" }
            return Artboard(handle, file.riveWorker, file.fileHandle, artboardName)
        }
    }

    /** @return A list of all state machine names on this artboard. */
    suspend fun getStateMachineNames(): List<String> = stateMachineNamesCache.await()
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
     * @throws IllegalStateException If the Rive worker has been released.
     */
    @Throws(IllegalStateException::class)
    fun resizeArtboard(
        surface: RiveSurface,
        scaleFactor: Float = 1f
    ) = riveWorker.resizeArtboard(artboardHandle, surface, scaleFactor)

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
     * @throws IllegalStateException If the Rive worker has been released.
     */
    @Throws(IllegalStateException::class)
    fun resetArtboardSize() = riveWorker.resetArtboardSize(artboardHandle)
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
 */
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

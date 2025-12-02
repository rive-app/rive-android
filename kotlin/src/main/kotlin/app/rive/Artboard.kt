package app.rive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import app.rive.core.ArtboardHandle
import app.rive.core.CloseOnce
import app.rive.core.CommandQueue
import app.rive.core.FileHandle
import app.rive.core.SuspendLazy

private const val ARTBOARD_TAG = "Rive/Artboard"

/**
 * An instantiated artboard from a [RiveFile].
 *
 * Can be queried for state machine names, and used to create a [RiveUI] composable.
 *
 * Create an instance of this class using [rememberArtboard] or [Artboard.fromFile]. When using the
 * latter, make sure to call [close] when you are done with the artboard to release its resources.
 *
 * @param artboardHandle The handle to the artboard on the command server.
 * @param commandQueue The command queue that owns the artboard.
 */
class Artboard internal constructor(
    internal val artboardHandle: ArtboardHandle,
    private val commandQueue: CommandQueue,
    private val fileHandle: FileHandle,
    val name: String?,
) : AutoCloseable by CloseOnce({
    RiveLog.d(ARTBOARD_TAG) { "Deleting $artboardHandle with name: $name (${fileHandle})" }
    commandQueue.deleteArtboard(artboardHandle)
}) {
    companion object {
        /**
         * Creates a new [Artboard].
         *
         * The lifetime of the artboard is managed by the caller. Make sure to call [close] when you
         * are done with it to release its resources.
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
                file.commandQueue.createArtboardByName(file.fileHandle, name)
            } ?: file.commandQueue.createDefaultArtboard(file.fileHandle)
            RiveLog.d(ARTBOARD_TAG) { "Created $handle with name: $artboardName (${file.fileHandle})" }
            return Artboard(handle, file.commandQueue, file.fileHandle, artboardName)
        }
    }

    /** @return A list of all state machine names on this artboard. */
    suspend fun getStateMachineNames(): List<String> = stateMachineNamesCache.await()
    private val stateMachineNamesCache = SuspendLazy {
        commandQueue.getStateMachineNames(artboardHandle)
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
 */
@ExperimentalRiveComposeAPI
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

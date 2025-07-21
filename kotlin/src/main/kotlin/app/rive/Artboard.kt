package app.rive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.rive.core.ArtboardHandle
import app.rive.core.CommandQueue
import kotlinx.coroutines.CoroutineScope

/**
 * An instantiated artboard from a [RiveFile].
 *
 * Can be queried for state machine names, and used to create a [RiveUI] composable.
 *
 * @param artboardHandle The handle to the artboard on the command server.
 * @param commandQueue The command queue that owns the artboard.
 * @param parentScope The coroutine scope to use for launching coroutines.
 */
class Artboard(
    internal val artboardHandle: ArtboardHandle,
    private val commandQueue: CommandQueue,
    parentScope: CoroutineScope,
) {
    /** @return A list of all state machine names on this artboard. */
    suspend fun getStateMachineNames(): List<String> = stateMachineNamesCache.await()
    private val stateMachineNamesCache by lazyDeferred<List<String>>(parentScope) {
        commandQueue.getStateMachineNames(artboardHandle)
    }
}

/**
 * Creates an [Artboard] from the given [RiveFile].
 *
 * The lifetime of the [Artboard] is managed by this composable. It will release the resources
 * allocated to the artboard when it falls out of scope.
 *
 * @param file The [RiveFile] to instantiate the artboard from.
 * @param artboardName The name of the artboard to load. If null, the default artboard will be
 *    loaded.
 */
@ExperimentalRiveComposeAPI
@Composable
fun rememberArtboard(
    file: RiveFile,
    artboardName: String? = null,
): Artboard {
    val commandQueue = file.commandQueue
    val scope = rememberCoroutineScope()

    val artboard = remember(file, artboardName) {
        val handle = if (artboardName != null) {
            commandQueue.createArtboardByName(file.fileHandle, artboardName)
        } else {
            commandQueue.createDefaultArtboard(file.fileHandle)
        }
        RiveLog.d("Rive/Artboard") { "Created $handle with name: $artboardName (${file.fileHandle})" }
        Artboard(handle, commandQueue, scope)
    }

    DisposableEffect(artboard) {
        onDispose {
            RiveLog.d("Rive/Artboard") { "Deleting $artboard with name: $artboardName (${file.fileHandle})" }
            commandQueue.deleteArtboard(artboard.artboardHandle)
        }
    }

    return artboard
}

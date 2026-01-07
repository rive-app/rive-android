package app.rive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import app.rive.core.ArtboardHandle
import app.rive.core.CloseOnce
import app.rive.core.RiveWorker
import app.rive.core.StateMachineHandle
import kotlin.time.Duration

private const val STATE_MACHINE_TAG = "Rive/StateMachine"

/**
 * An instantiated state machine from an [Artboard].
 *
 * Can be used to create a [Rive] composable and to manually advance the state machine.
 *
 * Create an instance of this class using [rememberStateMachine] or [StateMachine.fromArtboard].
 * When using the latter, make sure to call [close] when you are done with the state machine to
 * release its resources.
 *
 * @param stateMachineHandle The handle to the state machine on the command server.
 * @param riveWorker The Rive worker that owns the state machine.
 * @param artboardHandle The artboard handle that owns the state machine.
 * @param name The name of the state machine, or null if it's the default state machine.
 */
class StateMachine internal constructor(
    val stateMachineHandle: StateMachineHandle,
    private val riveWorker: RiveWorker,
    private val artboardHandle: ArtboardHandle,
    val name: String?,
) : AutoCloseable by CloseOnce("$stateMachineHandle", {
    val nameLog = name?.let { "with name $it" } ?: "(default)"
    RiveLog.d(STATE_MACHINE_TAG) { "Deleting $stateMachineHandle $nameLog ($artboardHandle)" }
    riveWorker.deleteStateMachine(stateMachineHandle)
}) {
    companion object {
        /**
         * Creates a new [StateMachine] from an [Artboard].
         *
         * ⚠️ The lifetime of the returned state machine is managed by the caller. Make sure to call
         * [close] when you are done with it to release its resources.
         *
         * @param artboard The [Artboard] to instantiate the state machine from.
         * @param stateMachineName The name of the state machine to load. If null, the default state
         *    machine will be loaded.
         * @return The created state machine.
         */
        fun fromArtboard(
            artboard: Artboard,
            stateMachineName: String? = null
        ): StateMachine {
            val handle = stateMachineName?.let { name ->
                artboard.riveWorker.createStateMachineByName(artboard.artboardHandle, name)
            } ?: artboard.riveWorker.createDefaultStateMachine(artboard.artboardHandle)
            val nameLog = stateMachineName?.let { "with name $it" } ?: "(default)"
            RiveLog.d(STATE_MACHINE_TAG) { "Created $handle $nameLog (${artboard.artboardHandle}; ${artboard.fileHandle})" }
            return StateMachine(
                handle,
                artboard.riveWorker,
                artboard.artboardHandle,
                stateMachineName
            )
        }
    }

    /**
     * Advance the state machine by the given delta time in nanoseconds.
     *
     * @param deltaTime The delta time to advance the state machine by.
     */
    fun advance(deltaTime: Duration) =
        riveWorker.advanceStateMachine(stateMachineHandle, deltaTime)
}

/**
 * Creates a [StateMachine] from the given [Artboard].
 *
 * The lifetime of the state machine is managed by this composable. It will delete the state machine
 * when it falls out of scope.
 *
 * @param artboard The [Artboard] to instantiate the state machine from.
 * @param stateMachineName The name of the state machine to load. If null, the default state machine
 *    will be loaded.
 * @return The created [StateMachine].
 */
@ExperimentalRiveComposeAPI
@Composable
fun rememberStateMachine(
    artboard: Artboard,
    stateMachineName: String? = null,
): StateMachine {
    val stateMachine = remember(artboard, stateMachineName) {
        StateMachine.fromArtboard(artboard, stateMachineName)
    }

    DisposableEffect(stateMachine) {
        onDispose { stateMachine.close() }
    }

    return stateMachine
}

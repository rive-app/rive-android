package app.rive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import app.rive.core.ArtboardHandle
import app.rive.core.CheckableAutoCloseable
import app.rive.core.CloseOnce
import app.rive.core.RiveWorker
import app.rive.core.StateMachineHandle
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

private const val STATE_MACHINE_TAG = "Rive/StateMachine"

/**
 * An instantiated state machine from an [Artboard].
 *
 * Can be used to create a [Rive] composable and to manually advance the state machine.
 *
 * Create an instance of this class using [rememberStateMachineResult] or [StateMachine.create].
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
) : CheckableAutoCloseable {
    private val closer = CloseOnce("$stateMachineHandle") {
        val nameLog = name?.let { "with name $it" } ?: "(default)"
        RiveLog.d(STATE_MACHINE_TAG) {
            "Deleting $stateMachineHandle $nameLog ($artboardHandle)"
        }
        riveWorker.deleteStateMachine(stateMachineHandle)
    }

    /**
     * Ensures this state machine has not been closed.
     *
     * @throws RiveResourceClosedException If this state machine has already been closed.
     */
    @Throws(RiveResourceClosedException::class)
    internal fun checkOpen() = closer.checkOpen()

    /**
     * Deletes this state machine and releases its resources.
     *
     * @throws RiveResourceClosedException If the owning Rive worker has been disposed.
     */
    @Throws(RiveResourceClosedException::class)
    override fun close() = closer.close()

    /** Whether this state machine has been closed. */
    override val closed: Boolean
        get() = closer.closed

    /**
     * Whether this state machine currently has no meaningful changes left to apply.
     *
     * A renderer can skip advancing and drawing while this is true. Calling [unsettle] changes it
     * to false synchronously for every renderer observing this state machine. After [close], a
     * retained flow has the terminal value `true` and receives no further updates.
     */
    internal val settled: StateFlow<Boolean> =
        riveWorker.stateMachineSettled(stateMachineHandle)

    companion object {
        /**
         * Creates a new [StateMachine] and suspends until its Rive worker confirms creation.
         *
         * This is the replacement for [fromArtboard]. Its temporary name avoids a
         * source-incompatible overload; it will be renamed to `fromArtboard` in 12.0 when the
         * unconfirmed API is removed.
         *
         * ⚠️ The lifetime of a successfully created state machine is managed by the caller. Make
         * sure to call [close] when you are done with it to release its resources.
         *
         * @param artboard The [Artboard] to instantiate the state machine from.
         * @param stateMachineName The name of the state machine to load. If null, the default state
         *    machine will be loaded.
         * @return The created state machine.
         * @throws RiveArtboardException If the artboard or requested state machine cannot be
         *    resolved.
         * @throws RiveResourceClosedException If [artboard] has been closed or its Rive worker has
         *    been disposed.
         * @throws CancellationException If the coroutine is cancelled before creation is
         *    confirmed.
         */
        @Throws(
            RiveArtboardException::class,
            RiveResourceClosedException::class,
            CancellationException::class
        )
        suspend fun create(
            artboard: Artboard,
            stateMachineName: String? = null
        ): StateMachine {
            val nameLog = stateMachineName?.let { "with name $it" } ?: "(default)"
            RiveLog.d(STATE_MACHINE_TAG) {
                "Creating state machine $nameLog (${artboard.artboardHandle}; ${artboard.fileHandle})"
            }
            return try {
                artboard.checkOpen()
                val handle = stateMachineName?.let { name ->
                    artboard.riveWorker.createStateMachineByNameConfirmed(
                        artboard.artboardHandle,
                        name
                    )
                } ?: artboard.riveWorker.createDefaultStateMachineConfirmed(
                    artboard.artboardHandle
                )
                RiveLog.d(STATE_MACHINE_TAG) {
                    "Created $handle $nameLog (${artboard.artboardHandle}; ${artboard.fileHandle})"
                }
                StateMachine(
                    handle,
                    artboard.riveWorker,
                    artboard.artboardHandle,
                    stateMachineName
                )
            } catch (ce: CancellationException) {
                RiveLog.d(STATE_MACHINE_TAG) {
                    "State machine creation was cancelled $nameLog " +
                        "(${artboard.artboardHandle}; ${artboard.fileHandle})"
                }
                throw ce
            } catch (e: Exception) {
                RiveLog.e(STATE_MACHINE_TAG, e) {
                    "Error creating state machine $nameLog " +
                        "(${artboard.artboardHandle}; ${artboard.fileHandle})"
                }
                throw e
            }
        }

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
         * @throws RiveResourceClosedException If [artboard] has been closed or its Rive worker has
         *    been disposed.
         * @deprecated Use [create]. This unconfirmed implementation will be removed in 12.0, when
         *    [create] will be renamed to `fromArtboard` as a suspending API.
         */
        @Throws(RiveResourceClosedException::class)
        @Deprecated(
            "Use create. This unconfirmed implementation will be removed in 12.0, when create " +
                "will be renamed to fromArtboard as a suspending API."
        )
        @Suppress("DEPRECATION")
        fun fromArtboard(
            artboard: Artboard,
            stateMachineName: String? = null
        ): StateMachine {
            artboard.checkOpen()
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
     * Requires this state machine to be owned by [worker].
     *
     * This compatibility check assumes callers have already verified that participating resources
     * are open.
     *
     * @param worker The worker required to own this state machine.
     * @throws RiveIncompatibleResourceException If this state machine is owned by another worker.
     */
    @Throws(RiveIncompatibleResourceException::class)
    internal fun requireOwnedBy(worker: RiveWorker) {
        if (riveWorker !== worker) {
            throw RiveIncompatibleResourceException(
                "StateMachine $stateMachineHandle is not owned by the required RiveWorker"
            )
        }
    }

    /**
     * Requires this state machine to have been created from [artboard].
     *
     * This compatibility check assumes callers have already verified that both resources are
     * open.
     *
     * @param artboard The artboard required to own this state machine.
     * @throws RiveIncompatibleResourceException If this state machine was created from another
     *    artboard.
     */
    @Throws(RiveIncompatibleResourceException::class)
    internal fun requireFromArtboard(artboard: Artboard) {
        if (riveWorker !== artboard.riveWorker || artboardHandle != artboard.artboardHandle) {
            throw RiveIncompatibleResourceException(
                "StateMachine $stateMachineHandle was not created from " +
                        "Artboard ${artboard.artboardHandle}"
            )
        }
    }

    /**
     * Marks this state machine as needing to be evaluated again without advancing it.
     *
     * This sets [settled] to false synchronously. It is safe to call repeatedly while the state
     * machine remains open; it will settle again when it has no meaningful changes left to apply.
     *
     * @throws RiveResourceClosedException If this state machine has been closed or its Rive worker
     *    has been disposed.
     * @throws IllegalStateException If the state machine is otherwise no longer registered with
     *    its worker.
     */
    @Throws(RiveResourceClosedException::class, IllegalStateException::class)
    internal fun unsettle() {
        closer.checkOpen()
        riveWorker.unsettleStateMachine(stateMachineHandle)
    }

    /**
     * Advance the state machine by the given delta time in nanoseconds.
     *
     * This is a fire-and-forget operation once the advance has been queued.
     *
     * @param deltaTime The delta time to advance the state machine by.
     * @throws RiveResourceClosedException If this state machine has been closed or its Rive worker
     *    has been disposed.
     */
    @Throws(RiveResourceClosedException::class)
    fun advance(deltaTime: Duration) {
        closer.checkOpen()
        riveWorker.advanceStateMachine(stateMachineHandle, deltaTime)
    }
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
 * @throws RiveResourceClosedException If [artboard] has been closed or its Rive worker has been
 *    disposed.
 * @deprecated Use [rememberStateMachineResult]. This implementation will be removed in 12.0, when
 *    [rememberStateMachineResult] will be renamed to `rememberStateMachine` and return a [Result].
 */
@Throws(RiveResourceClosedException::class)
@Deprecated(
    "Use rememberStateMachineResult. This implementation will be removed in 12.0, when " +
        "rememberStateMachineResult will be renamed to rememberStateMachine and return a Result."
)
@Suppress("DEPRECATION")
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

/**
 * Creates a [StateMachine] from [artboard] and exposes its creation state.
 *
 * The lifetime of a successfully created state machine is managed by this composable. It will
 * delete the state machine when it falls out of scope.
 *
 * This is the replacement for [rememberStateMachine]; its temporary name will become
 * `rememberStateMachine` in 12.0 when the unconfirmed API is removed.
 *
 * @param artboard The [Artboard] to instantiate the state machine from.
 * @param stateMachineName The name of the state machine to load. If null, the default state machine
 *    will be loaded.
 * @return The current creation result: loading, error, or success with the created [StateMachine].
 *    Changing [artboard] or [stateMachineName] synchronously returns loading while the replacement
 *    is created.
 */
@Composable
fun rememberStateMachineResult(
    artboard: Artboard,
    stateMachineName: String? = null,
): Result<StateMachine> = key(artboard, stateMachineName) {
    produceState<Result<StateMachine>>(Result.Loading) {
        val stateMachine = try {
            StateMachine.create(artboard, stateMachineName)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            value = Result.Error(e)
            return@produceState
        }

        value = Result.Success(stateMachine)
        awaitDispose { stateMachine.close() }
    }.value
}

package app.rive

import androidx.annotation.MainThread
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
import app.rive.core.ViewModelInstanceHandle
import app.rive.semantics.SemanticActionType
import app.rive.semantics.SemanticTreeModel
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
    /**
     * Coordinates declarative view model bindings for the enclosing state machine.
     *
     * It encapsulates the complete binding workflow: serializing updates, validating the desired
     * configuration before dispatch, caching and diffing it, queuing only changes, and finishing
     * each changed configuration with one bind command. Its first application always binds, even
     * when empty, so core can create and apply default instances.
     *
     * @param riveWorker The worker on which binding commands are queued.
     * @param stateMachineHandle The state machine whose bindings are coordinated.
     */
    @OptIn(ExperimentalRiveGlobalViewModels::class)
    private class Bindings(
        private val riveWorker: RiveWorker,
        private val stateMachineHandle: StateMachineHandle,
    ) {
        /**
         * An immutable, handle-only record of one explicitly supplied binding configuration.
         *
         * Avoiding [ViewModelInstance] references keeps their lifetimes caller-managed. A null
         * cached snapshot means no configuration has been applied; a snapshot with a null [main]
         * and empty [globals] means defaults were explicitly bound.
         *
         * @param main The handle of the explicitly supplied main instance, or null for its default.
         * @param globals Explicitly supplied global handles keyed by global view model name.
         */
        private data class BindingSnapshot(
            val main: ViewModelInstanceHandle?,
            val globals: Map<String, ViewModelInstanceHandle>,
        )

        private var applied: BindingSnapshot? = null

        /**
         * Applies one complete desired view model binding configuration if it has changed.
         *
         * @param main The explicitly supplied main view model instance, or null to create one from
         *    its authored default. Clearing a previous override creates a fresh default instance.
         * @param globals The explicitly supplied instances keyed by global view model name.
         *    Omitted slots create fresh instances from their authored defaults when first bound or
         *    after an explicit binding is removed.
         * @return true if commands were dispatched; false if the configuration was unchanged.
         * @throws RiveResourceClosedException If a supplied instance has been closed or the worker
         *    has been disposed.
         * @throws RiveIncompatibleResourceException If a supplied instance belongs to another
         *    worker.
         */
        @Throws(RiveResourceClosedException::class, RiveIncompatibleResourceException::class)
        @Synchronized
        fun apply(
            main: ViewModelInstance?,
            globals: Map<String, ViewModelInstance>,
        ): Boolean {
            val globalSnapshot = globals.toMap()
            main?.checkOpen()
            main?.requireOwnedBy(riveWorker)
            globalSnapshot.values.forEach { instance ->
                instance.checkOpen()
                instance.requireOwnedBy(riveWorker)
            }

            val next = BindingSnapshot(
                main = main?.instanceHandle,
                globals = globalSnapshot.mapValues { (_, instance) -> instance.instanceHandle },
            )
            val previous = applied
            if (previous == next) {
                return false
            }

            RiveLog.d(STATE_MACHINE_TAG) {
                "Binding view models to $stateMachineHandle: " +
                    "main override=${next.main ?: "none"}, " +
                    "global overrides=${next.globals.keys}"
            }

            if (previous?.main != next.main) {
                if (next.main != null) {
                    riveWorker.setMainViewModelInstance(stateMachineHandle, next.main)
                } else if (previous != null) {
                    riveWorker.clearMainViewModelInstance(stateMachineHandle)
                }
            }

            previous?.globals?.keys
                ?.minus(next.globals.keys)
                ?.forEach { name ->
                    riveWorker.clearGlobalViewModelInstance(stateMachineHandle, name)
                }
            next.globals.forEach { (name, handle) ->
                if (previous?.globals?.get(name) != handle) {
                    riveWorker.setGlobalViewModelInstance(stateMachineHandle, name, handle)
                }
            }

            riveWorker.bind(stateMachineHandle)
            applied = next
            return true
        }
    }

    private val bindings = Bindings(riveWorker, stateMachineHandle)

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
     * Applies the complete desired main and global view model binding configuration.
     *
     * Only changes from the last successfully queued configuration are dispatched. An initial
     * empty configuration still binds so core can create and apply default instances.
     *
     * Binding marks this state machine unsettled but does not evaluate it. If no renderer is
     * actively advancing the state machine, call [advance] before drawing or observing artboard or
     * state-machine effects produced by the new bindings. Advancing by 0 is sufficient to adopt the
     * bound values.
     *
     * @param main The explicitly supplied main view model instance, or null to create one from its
     *    authored default. Clearing a previous override creates a fresh default instance.
     * @param globals The explicitly supplied instances keyed by global view model name. Omitted
     *    slots create fresh instances from their authored defaults when first bound or after an
     *    explicit binding is removed.
     * @throws RiveResourceClosedException If this state machine or a supplied instance has been
     *    closed, or if the owning worker has been disposed.
     * @throws RiveIncompatibleResourceException If a supplied instance belongs to another worker.
     * @throws IllegalStateException If this state machine is no longer registered with its worker.
     */
    @Throws(
        RiveResourceClosedException::class,
        RiveIncompatibleResourceException::class,
        IllegalStateException::class,
    )
    internal fun bindViewModels(
        main: ViewModelInstance?,
        globals: Map<String, ViewModelInstance>,
    ) {
        checkOpen()
        if (bindings.apply(main, globals)) {
            unsettle()
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

    /**
     * Enable semantic tracking for this state machine.
     *
     * This operation enables core semantic diff production and marks the state machine unsettled so
     * an active renderer evaluates it. It does not advance the state machine or retrieve diffs.
     * Low-level callers must call [advance] followed by [drainSemanticsDiff]. The [Rive] composable
     * manages that sequence when its [RiveSemanticsMode] resolves to enabled.
     *
     * @throws RiveResourceClosedException If this state machine has been closed or its Rive worker
     *    has been disposed.
     */
    @ExperimentalRiveSemantics
    @Throws(RiveResourceClosedException::class)
    fun enableSemantics() {
        closer.checkOpen()
        riveWorker.enableSemantics(stateMachineHandle)
        unsettle()
    }

    /**
     * Drain all pending semantic diff records for this state machine.
     *
     * Queue this after [advance] so the drain observes that advance. The resulting diff is
     * delivered asynchronously and applied to [semanticTree] when the Rive worker's messages are
     * polled. Empty drains do not update the tree.
     *
     * The delivered semantic node bounds are mapped to view space based on [fit], [surfaceWidth],
     * and [surfaceHeight]. Bounds are physical pixels local to that viewport.
     *
     * Diffs are applied to [semanticTree]. To observe when a diff has been applied, collect
     * [SemanticTreeModel.versionFlow]. The version increments only when the applied diff changes
     * the tree. The flow may be collected from any thread, but tree contents must be read on the
     * Android main thread.
     *
     * @param fit The fit used when drawing the artboard.
     * @param surfaceWidth The view width in pixels.
     * @param surfaceHeight The view height in pixels.
     * @throws RiveResourceClosedException If this state machine has been closed or its Rive worker
     *    has been disposed.
     */
    @ExperimentalRiveSemantics
    @Throws(RiveResourceClosedException::class)
    fun drainSemanticsDiff(
        fit: Fit,
        surfaceWidth: Float,
        surfaceHeight: Float
    ) {
        closer.checkOpen()
        riveWorker.drainSemanticsDiff(stateMachineHandle, fit, surfaceWidth, surfaceHeight)
    }

    /**
     * Semantic tree maintained for this state machine.
     *
     * Updated by [drainSemanticsDiff]. Collect [SemanticTreeModel.versionFlow] to detect when an
     * applied diff changes this tree. The flow may be collected from any thread, but this property
     * and all tree contents must be accessed on the Android main thread.
     */
    @ExperimentalRiveSemantics
    @get:MainThread
    val semanticTree: SemanticTreeModel
        get() {
            closer.checkOpen()
            return riveWorker.semanticTree(stateMachineHandle)
        }

    /**
     * Fire a semantic action on the specified semantic node.
     *
     * This queues the action on the Rive worker and marks the state machine unsettled so an active
     * renderer evaluates it. Advance and drain the state machine afterward to publish any resulting
     * visual or semantic changes. The [Rive] composable schedules that work for accessibility
     * actions dispatched through its virtual Android hierarchy.
     *
     * @param nodeId The semantic node ID.
     * @param action The semantic action to fire.
     * @throws RiveResourceClosedException If this state machine has been closed or its Rive worker
     *    has been disposed.
     * @throws IllegalStateException If this state machine is no longer registered with its worker.
     */
    @ExperimentalRiveSemantics
    @Throws(RiveResourceClosedException::class, IllegalStateException::class)
    fun fireSemanticAction(nodeId: Int, action: SemanticActionType) {
        closer.checkOpen()
        riveWorker.fireSemanticAction(stateMachineHandle, nodeId, action)
        unsettle()
    }

    /**
     * Request accessibility focus on a semantic node.
     *
     * This queues a Rive-authored semantic focus request; it does not directly move Android
     * accessibility focus. The state machine is marked unsettled so an active renderer evaluates
     * the request; advance and drain afterward to publish resulting semantic state.
     *
     * @param nodeId The semantic node ID to focus.
     * @throws RiveResourceClosedException If this state machine has been closed or its Rive worker
     *    has been disposed.
     * @throws IllegalStateException If this state machine is no longer registered with its worker.
     */
    @ExperimentalRiveSemantics
    @Throws(RiveResourceClosedException::class, IllegalStateException::class)
    fun requestSemanticFocus(nodeId: Int) {
        closer.checkOpen()
        riveWorker.requestSemanticFocus(stateMachineHandle, nodeId)
        unsettle()
    }

    /**
     * Clear Rive runtime focus for this state machine.
     *
     * This clears focus held by Rive's authored focus graph. It does not directly clear Android
     * view focus, Compose focus, or TalkBack accessibility focus. The state machine is marked
     * unsettled so an active renderer evaluates the request; advance and drain afterward to publish
     * resulting semantic state.
     *
     * @throws RiveResourceClosedException If this state machine has been closed or its Rive worker
     *    has been disposed.
     * @throws IllegalStateException If this state machine is no longer registered with its worker.
     */
    @ExperimentalRiveSemantics
    @Throws(RiveResourceClosedException::class, IllegalStateException::class)
    fun clearSemanticFocus() {
        closer.checkOpen()
        riveWorker.clearSemanticFocus(stateMachineHandle)
        unsettle()
    }

    /**
     * Attempts to clear semantic focus during lifecycle cleanup.
     *
     * A closed state machine or disposed worker cannot retain semantic focus, so its
     * [RiveResourceClosedException] is ignored. Other failures still indicate a runtime invariant
     * violation and propagate to the caller.
     */
    internal fun clearSemanticFocusForLifecycleCleanup() {
        try {
            clearSemanticFocus()
        } catch (_: RiveResourceClosedException) {
            // The resource that could hold focus no longer exists.
        }
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

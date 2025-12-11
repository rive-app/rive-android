package app.rive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.rive.core.AudioHandle
import app.rive.core.COMMAND_QUEUE_TAG
import app.rive.core.CommandQueue
import app.rive.core.ComposeFrameTicker
import app.rive.core.FontHandle
import app.rive.core.ImageHandle

/**
 * A [CommandQueue] is the worker that runs Rive in a thread. It holds all of the state, including
 * assets ([images][ImageHandle], [audio][AudioHandle], and [fonts][FontHandle]), [RiveFile]s,
 * [artboards][Artboard], state machines, and [view model instances][ViewModelInstance].
 *
 * The lifetime of the command queue is managed by this composable. It will release the resources
 * allocated to the command queue when it falls out of scope.
 *
 * A command queue needs to be polled to receive messages from the command server. This composable
 * creates a poll loop that runs while the [Lifecycle] is in the [Lifecycle.State.RESUMED] state.
 * The poll rate is once per frame, which is typically 60 FPS.
 *
 * This function throws a [RuntimeException] if the command queue cannot be created. If you want to
 * handle failure gracefully, use [rememberCommandQueueOrNull] instead.
 *
 * @return The created [CommandQueue].
 * @throws RiveInitializationException If the command queue cannot be created for any reason.
 * @see CommandQueue
 * @see rememberCommandQueueOrNull
 */
@ExperimentalRiveComposeAPI
@Composable
@Throws(RiveInitializationException::class)
fun rememberCommandQueue(autoPoll: Boolean = true): CommandQueue {
    val errorState = remember { mutableStateOf<Throwable?>(null) }
    val commandQueue = rememberCommandQueueOrNull(errorState, autoPoll)
    return commandQueue ?: throw RiveInitializationException(
        "Failed to create CommandQueue",
        errorState.value
    )
}

/**
 * A nullable variant of [rememberCommandQueue] that returns null if the command queue cannot be
 * created.
 *
 * Use this variant if you want to handle the failure of command queue creation gracefully, which
 * may be desirable in production.
 *
 * @param errorState A mutable state that holds the error if the command queue creation fails.
 *    Useful if you want to display or pass the error.
 * @return The created [CommandQueue], or null if creation failed.
 * @see rememberCommandQueue
 */
@ExperimentalRiveComposeAPI
@Composable
fun rememberCommandQueueOrNull(
    errorState: MutableState<Throwable?> = mutableStateOf(null),
    autoPoll: Boolean = true,
): CommandQueue? {
    val lifecycleOwner = LocalLifecycleOwner.current
    val commandQueue = remember {
        runCatching { CommandQueue() }
            .onFailure {
                if (errorState.value == null) {
                    errorState.value = it
                }
                RiveLog.e(COMMAND_QUEUE_TAG) { "Failed to create command queue: ${it.message}" }
            }.getOrNull()
    }

    /**
     * Start polling the command queue for messages. This runs in a loop while the [Lifecycle] is in
     * the [Lifecycle.State.RESUMED] state.
     *
     * Uses [ComposeFrameTicker] as the implementation for frame timing.
     */
    LaunchedEffect(lifecycleOwner, commandQueue, autoPoll) {
        if (commandQueue == null || !autoPoll) return@LaunchedEffect

        commandQueue.beginPolling(lifecycleOwner.lifecycle, ComposeFrameTicker)
    }

    /** Disposes the command queue when it falls out of scope. */
    DisposableEffect(commandQueue) {
        if (commandQueue == null) return@DisposableEffect onDispose {}

        onDispose {
            commandQueue.release(COMMAND_QUEUE_TAG, "Compose dispose")
        }
    }

    return commandQueue
}

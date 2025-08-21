package app.rive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.rive.core.CommandQueue
import kotlinx.coroutines.isActive

const val COMMAND_QUEUE_TAG = "Rive/CQ"

/**
 * A [CommandQueue] is the worker that runs Rive in a thread. It holds all of the state, including
 * assets ([images][ImageHandle], [audio][AudioHandle], and [fonts][FontHandle]), [RiveFiles]s,
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
 * @throws RuntimeException If the command queue cannot be created for any reason.
 * @see CommandQueue
 * @see rememberCommandQueueOrNull
 */
@ExperimentalRiveComposeAPI
@Composable
@Throws(RuntimeException::class)
fun rememberCommandQueue(): CommandQueue {
    val errorState = remember { mutableStateOf<Throwable?>(null) }
    val commandQueue = rememberCommandQueueOrNull(errorState)
    return commandQueue ?: throw RuntimeException(
        errorState.value ?: RuntimeException("Failed to create CommandQueue")
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
    errorState: MutableState<Throwable?> = mutableStateOf(null)
): CommandQueue? {
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val commandQueue = remember(coroutineScope) {
        runCatching {
            CommandQueue(coroutineScope).also {
                RiveLog.d(COMMAND_QUEUE_TAG) { "Created command queue" }
            }
        }.onFailure {
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
     * `withFrameNanos` ties the loop to the Choreographer.
     */
    LaunchedEffect(lifecycleOwner, commandQueue) {
        if (commandQueue == null) return@LaunchedEffect

        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            RiveLog.d(COMMAND_QUEUE_TAG) { "Starting command queue polling" }
            while (isActive) {
                withFrameNanos {
                    commandQueue.pollMessages()
                }
            }
        }
    }

    /** Disposes the command queue when it falls out of scope. */
    DisposableEffect(commandQueue) {
        if (commandQueue == null) return@DisposableEffect onDispose { }

        onDispose {
            RiveLog.d(COMMAND_QUEUE_TAG) { "Releasing command queue (remaining ref count before release: ${commandQueue.refCount})" }
            commandQueue.release()
        }
    }

    return commandQueue
}

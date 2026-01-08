package app.rive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.rive.core.AudioEngine
import app.rive.core.AudioHandle
import app.rive.core.ComposeFrameTicker
import app.rive.core.FontHandle
import app.rive.core.ImageHandle
import app.rive.core.RiveWorker
import kotlinx.coroutines.awaitCancellation

const val RIVE_WORKER_TAG = "Rive/Worker"

/**
 * A [RiveWorker] is the worker that runs Rive in a thread. It holds all of the state, including
 * assets ([images][ImageHandle], [audio][AudioHandle], and [fonts][FontHandle]), [RiveFile]s,
 * [artboards][Artboard], state machines, and [view model instances][ViewModelInstance].
 *
 * The lifetime of the Rive worker is managed by this composable. It will release the resources
 * allocated to the Rive worker when it falls out of scope.
 *
 * A Rive worker needs to be polled to receive messages from the command server. This composable
 * creates a poll loop that runs while the [Lifecycle] is in the [Lifecycle.State.RESUMED] state.
 * The poll rate is once per frame, which is typically 60 FPS.
 *
 * This function throws a [RuntimeException] if the Rive worker cannot be created. If you want to
 * handle failure gracefully, use [rememberRiveWorkerOrNull] instead.
 *
 * @return The created [RiveWorker].
 * @throws RiveInitializationException If the Rive worker cannot be created for any reason.
 * @see RiveWorker
 * @see rememberRiveWorkerOrNull
 */
@Composable
@Throws(RiveInitializationException::class)
fun rememberRiveWorker(autoPoll: Boolean = true): RiveWorker {
    val errorState = remember { mutableStateOf<Throwable?>(null) }
    val riveWorker = rememberRiveWorkerOrNull(errorState, autoPoll)
    return riveWorker ?: throw RiveInitializationException(
        "Failed to create Rive worker",
        errorState.value
    )
}

/**
 * A nullable variant of [rememberRiveWorker] that returns null if the Rive worker cannot be
 * created.
 *
 * Use this variant if you want to handle the failure of Rive worker creation gracefully, which may
 * be desirable in production.
 *
 * @param errorState A mutable state that holds the error if the Rive worker creation fails. Useful
 *    if you want to display or pass the error.
 * @return The created [RiveWorker], or null if creation failed.
 * @see rememberRiveWorker
 */
@Composable
fun rememberRiveWorkerOrNull(
    errorState: MutableState<Throwable?> = mutableStateOf(null),
    autoPoll: Boolean = true,
): RiveWorker? {
    val lifecycleOwner = LocalLifecycleOwner.current
    val worker = remember {
        runCatching { RiveWorker() }
            .onFailure {
                if (errorState.value == null) {
                    errorState.value = it
                }
                RiveLog.e(RIVE_WORKER_TAG) { "Failed to create Rive worker: ${it.message}" }
            }.getOrNull()
    }

    /**
     * Start polling the Rive worker for messages. This runs in a loop while the [Lifecycle] is in
     * the [Lifecycle.State.RESUMED] state.
     *
     * Uses [ComposeFrameTicker] as the implementation for frame timing.
     */
    LaunchedEffect(lifecycleOwner, worker, autoPoll) {
        if (worker == null || !autoPoll) return@LaunchedEffect

        worker.beginPolling(lifecycleOwner.lifecycle, ComposeFrameTicker)
    }

    /**
     * Manage audio engine start/stop state based on the surrounding lifecycle. Acquires a reference
     * when RESUMED and releases when exiting RESUMED.
     */
    LaunchedEffect(worker, lifecycleOwner) {
        if (worker == null) return@LaunchedEffect

        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            AudioEngine.acquire()
            try {
                // Wait while RESUMED - the coroutine will be cancelled when lifecycle exits RESUMED
                awaitCancellation()
            } finally {
                // Release the audio engine reference when exiting RESUMED state
                AudioEngine.release()
            }
        }
    }

    /** Disposes the Rive worker when it falls out of scope. */
    DisposableEffect(worker) {
        if (worker == null) return@DisposableEffect onDispose {}

        onDispose {
            worker.release(RIVE_WORKER_TAG, "Compose dispose")
        }
    }

    return worker
}

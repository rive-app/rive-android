package app.rive.core

import android.view.Choreographer
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.AndroidUiDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** A way of getting frame callbacks every vsync. */
fun interface FrameTicker {
    /** @param onFrame A callback that is invoked each vsync with the frame time in nanoseconds. */
    suspend fun withFrame(onFrame: (Long) -> Unit)
}

/** A [FrameTicker] that uses [Choreographer] to get frame callbacks. */
val ChoreographerFrameTicker = FrameTicker { onFrame ->
    withFrameNanosChoreographer { frameTimeNs -> onFrame(frameTimeNs) }
}

/** A [FrameTicker] that uses [withFrameNanos] to get frame callbacks. */
val ComposeFrameTicker = FrameTicker { onFrame ->
    withContext(AndroidUiDispatcher.Main) {
        withFrameNanos { frameTimeNs -> onFrame(frameTimeNs) }
    }
}

/**
 * An API compatible version of [withFrameNanos] from Compose that uses [Choreographer].
 *
 * @param onFrame A callback that is invoked with the frame time in nanoseconds.
 * @return The result of [onFrame].
 */
suspend fun <R> withFrameNanosChoreographer(onFrame: (Long) -> R): R {
    // suspendCancellableCoroutine is used here to create a continuation that can be resumed
    // when the frame callback is invoked, while also allowing for cancellation.
    val frameTimeNs = suspendCancellableCoroutine { cont ->
        val choreographer = Choreographer.getInstance()
        val onFrameCallback = Choreographer.FrameCallback { timeNs ->
            cont.resume(timeNs)
        }
        // Assume that we always post the callback to continue the loop...
        choreographer.postFrameCallback(onFrameCallback)
        // ... but remove it if the coroutine is cancelled before the next frame.
        cont.invokeOnCancellation { choreographer.removeFrameCallback(onFrameCallback) }
    }
    return onFrame(frameTimeNs)
}

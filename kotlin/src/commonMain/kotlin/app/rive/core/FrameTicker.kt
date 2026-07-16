package app.rive.core

import androidx.compose.runtime.withFrameNanos

/** A way of getting frame callbacks every vsync. */
fun interface FrameTicker {
    /** @param onFrame A callback that is invoked each vsync with the frame time in nanoseconds. */
    suspend fun withFrame(onFrame: (Long) -> Unit)
}

/**
 * A [FrameTicker] that uses [withFrameNanos] to get frame callbacks.
 *
 * The calling coroutine context must contain a
 * [MonotonicFrameClock][androidx.compose.runtime.MonotonicFrameClock], which is the case inside
 * composition effects such as `LaunchedEffect`.
 */
val ComposeFrameTicker = FrameTicker { onFrame ->
    withFrameNanos { frameTimeNs -> onFrame(frameTimeNs) }
}

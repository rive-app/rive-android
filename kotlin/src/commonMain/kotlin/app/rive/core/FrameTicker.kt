package app.rive.core

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.withFrameNanos
import app.rive.monotonicTimeNanos
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay

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

/** Tick period when the coroutine context has no frame clock (~60 Hz). */
private const val FALLBACK_FRAME_MILLIS = 16L

/**
 * The default [FrameTicker]: uses the coroutine context's [MonotonicFrameClock] when one is
 * present (composition effects, Compose UI scopes), and otherwise ticks on a fixed ~60 Hz
 * delay. Safe to call from non-Compose scopes such as `lifecycleScope`. On Android,
 * `ChoreographerFrameTicker` remains available for vsync-aligned ticking outside Compose.
 */
val DefaultFrameTicker = FrameTicker { onFrame ->
    if (currentCoroutineContext()[MonotonicFrameClock] != null) {
        withFrameNanos { frameTimeNs -> onFrame(frameTimeNs) }
    } else {
        delay(FALLBACK_FRAME_MILLIS)
        onFrame(monotonicTimeNanos())
    }
}

package app.rive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.Layout
import app.rive.core.RiveWorker

// Real desktop rendering (offscreen blit) is a follow-up phase. Unreachable today: worker
// creation throws before this composable can be reached.
@Composable
internal actual fun RivePlatformSurface(
    worker: RiveWorker,
    frameRate: RiveFrameRate,
    frameRateActive: Boolean,
    onPresenterChanged: (SurfacePresenter?) -> Unit,
    onFrameCaptured: ((ImageBitmap) -> Unit)?,
    modifier: Modifier,
) {
    Layout(content = {}, modifier = modifier) { _, constraints ->
        layout(constraints.minWidth, constraints.minHeight) {}
    }
}

internal actual fun monotonicTimeNanos(): Long = System.nanoTime()

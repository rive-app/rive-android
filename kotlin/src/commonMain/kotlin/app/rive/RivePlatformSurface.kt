package app.rive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import app.rive.core.ArtboardHandle
import app.rive.core.RiveSurface
import app.rive.core.RiveWorker
import app.rive.core.StateMachineHandle

/**
 * A platform render target that [Rive] draws into.
 *
 * Created and owned by [RivePlatformSurface]; the common draw loop only advances and draws
 * through it.
 */
internal interface SurfacePresenter {
    /** Current width of the render target in physical pixels. */
    val width: Int

    /** Current height of the render target in physical pixels. */
    val height: Int

    /** Backing surface, used for `Fit.Layout` artboard resizing. */
    val riveSurface: RiveSurface

    /**
     * Draws one frame.
     *
     * On Android this queues an asynchronous GPU present; on desktop it renders offscreen and
     * blits the pixels into the composition.
     */
    fun draw(
        artboardHandle: ArtboardHandle,
        stateMachineHandle: StateMachineHandle,
        fit: Fit,
        clearColor: Int,
    )
}

/**
 * Hosts the platform's render target for [Rive] and reports its lifecycle.
 *
 * @param worker The worker that renders into the surface.
 * @param frameRate Requested frame rate; platforms may use it as a hint (e.g. Android's view
 *    frame-rate hint).
 * @param frameRateActive Whether the frame-rate hint should currently apply (playing and not
 *    settled).
 * @param onPresenterChanged Invoked with a new [SurfacePresenter] when the platform surface
 *    becomes available or its size changes, and with `null` when it is destroyed.
 * @param onFrameCaptured When non-null, invoked once per surface with the first rendered frame.
 * @param modifier Modifier for the platform surface element.
 */
@Composable
internal expect fun RivePlatformSurface(
    worker: RiveWorker,
    frameRate: RiveFrameRate,
    frameRateActive: Boolean,
    onPresenterChanged: (SurfacePresenter?) -> Unit,
    onFrameCaptured: ((ImageBitmap) -> Unit)?,
    modifier: Modifier,
)

/** Current monotonic time in nanoseconds, on the same time base as Compose frame callbacks. */
internal expect fun monotonicTimeNanos(): Long

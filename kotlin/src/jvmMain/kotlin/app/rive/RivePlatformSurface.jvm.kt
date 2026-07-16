package app.rive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.IntSize
import app.rive.core.ArtboardHandle
import app.rive.core.RiveSurface
import app.rive.core.RiveWorker
import app.rive.core.StateMachineHandle
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

private const val BLIT_TAG = "Rive/DesktopSurface"

/**
 * Desktop [SurfacePresenter]: renders offscreen through the worker's Vulkan image surface,
 * reads the frame back, and installs the pixels into a Skia bitmap that Compose draws.
 */
private class DesktopSurfacePresenter(
    private val worker: RiveWorker,
    override val riveSurface: RiveSurface,
    private val onFrame: (ImageBitmap) -> Unit,
) : SurfacePresenter {
    override val width: Int get() = riveSurface.width
    override val height: Int get() = riveSurface.height

    private val pixels = ByteArray(width * height * 4)
    private val flipped = ByteArray(width * height * 4)

    override fun draw(
        artboardHandle: ArtboardHandle,
        stateMachineHandle: StateMachineHandle,
        fit: Fit,
        clearColor: Int,
    ) {
        if (riveSurface.closed) return
        worker.drawToBuffer(
            artboardHandle,
            stateMachineHandle,
            riveSurface,
            pixels,
            width,
            height,
            fit,
            clearColor
        )
        // The Vulkan offscreen readback returns rows bottom-up; write them top-down.
        val rowBytes = width * 4
        for (row in 0 until height) {
            pixels.copyInto(
                flipped,
                destinationOffset = (height - 1 - row) * rowBytes,
                startIndex = row * rowBytes,
                endIndex = (row + 1) * rowBytes,
            )
        }
        val bitmap = Bitmap()
        val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
        bitmap.allocPixels(info)
        bitmap.installPixels(info, flipped, width * 4)
        onFrame(bitmap.asComposeImageBitmap())
    }
}

/**
 * Desktop [RivePlatformSurface]: sizes an offscreen Vulkan image surface to the layout and
 * draws each read-back frame as an [ImageBitmap].
 */
@Composable
internal actual fun RivePlatformSurface(
    worker: RiveWorker,
    frameRate: RiveFrameRate,
    frameRateActive: Boolean,
    onPresenterChanged: (SurfacePresenter?) -> Unit,
    onFrameCaptured: ((ImageBitmap) -> Unit)?,
    modifier: Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    val currentOnPresenterChanged by rememberUpdatedState(onPresenterChanged)
    val currentOnFrameCaptured by rememberUpdatedState(onFrameCaptured)

    // Image surfaces are fixed-size: recreate the surface (and presenter) per size.
    val surface = remember(worker, size) {
        if (size.width <= 0 || size.height <= 0) null
        else worker.createImageSurface(size.width, size.height)
    }

    DisposableEffect(surface) {
        if (surface == null) {
            currentOnPresenterChanged(null)
            return@DisposableEffect onDispose {}
        }
        RiveLog.d(BLIT_TAG) { "Desktop surface available (${surface.width} x ${surface.height})" }
        var frameCaptureSent = false
        val presenter = DesktopSurfacePresenter(worker, surface) { newFrame ->
            frame = newFrame
            if (!frameCaptureSent) {
                frameCaptureSent = true
                currentOnFrameCaptured?.invoke(newFrame)
            }
        }
        currentOnPresenterChanged(presenter)
        onDispose {
            currentOnPresenterChanged(null)
            surface.close()
        }
    }

    Layout(
        content = {},
        modifier = modifier
            .onSizeChanged { size = it }
            .drawBehind {
                frame?.let { drawImage(it) }
            }
    ) { _, constraints ->
        layout(constraints.maxWidth, constraints.maxHeight) {}
    }
}

internal actual fun monotonicTimeNanos(): Long = System.nanoTime()

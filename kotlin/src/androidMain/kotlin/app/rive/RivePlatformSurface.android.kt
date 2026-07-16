package app.rive

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import app.rive.core.ArtboardHandle
import app.rive.core.RiveSurface
import app.rive.core.RiveWorker
import app.rive.core.StateMachineHandle
import app.rive.core.SurfaceTextureSurface
import app.rive.core.createRiveSurface

private const val PLATFORM_SURFACE_TAG = "Rive/UI"

/** [SurfacePresenter] that presents into an Android window surface via the worker. */
private class AndroidSurfacePresenter(
    private val worker: RiveWorker,
    override val riveSurface: RiveSurface,
) : SurfacePresenter {
    override val width: Int get() = riveSurface.width
    override val height: Int get() = riveSurface.height

    override fun draw(
        artboardHandle: ArtboardHandle,
        stateMachineHandle: StateMachineHandle,
        fit: Fit,
        clearColor: Int,
    ) = worker.draw(artboardHandle, stateMachineHandle, riveSurface, fit, clearColor)
}

/**
 * Android [RivePlatformSurface]: hosts a [TextureView] whose [SurfaceTexture] backs the
 * [RiveSurface] that Rive renders into.
 *
 * In [LocalInspectionMode] (Android Studio previews, layoutlib test runners) there is no real
 * surface or Android `.so`; frames render offscreen through the desktop library and blit into
 * the composition instead.
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
    if (LocalInspectionMode.current) {
        RiveInspectionSurface(worker, onPresenterChanged, onFrameCaptured, modifier)
        return
    }

    var surface by remember { mutableStateOf<RiveSurface?>(null) }

    /** Clean up for the surface. */
    DisposableEffect(surface) {
        val nonNullSurface = surface ?: return@DisposableEffect onDispose {}
        onDispose {
            nonNullSurface.close()
        }
    }

    val currentOnPresenterChanged by rememberUpdatedState(onPresenterChanged)
    val currentOnFrameCaptured by rememberUpdatedState(onFrameCaptured)
    var frameCallbackSent by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureView(context).apply {
                isOpaque = false

                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        newSurfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        RiveLog.d(PLATFORM_SURFACE_TAG) { "Surface texture available ($width x $height)" }
                        val newSurface = worker.createRiveSurface(
                            SurfaceTextureSurface(newSurfaceTexture, width, height)
                        )
                        surface = newSurface
                        currentOnPresenterChanged(AndroidSurfacePresenter(worker, newSurface))
                        // Because this is a new surface, we send a fresh callback
                        frameCallbackSent = false
                    }

                    override fun onSurfaceTextureDestroyed(destroyedSurfaceTexture: SurfaceTexture): Boolean {
                        RiveLog.d(PLATFORM_SURFACE_TAG) { "Surface texture destroyed (final release deferred to RenderContext disposal)" }
                        surface = null
                        currentOnPresenterChanged(null)
                        frameCallbackSent = false
                        // False here means that we are responsible for destroying the surface texture.
                        // This happens when the RiveSurface is closed.
                        return false
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        RiveLog.d(PLATFORM_SURFACE_TAG) { "Surface texture size changed ($width x $height)" }
                        val resizedSurface = surface ?: return
                        resizedSurface.resize(width, height)
                        // Re-report so the common layer observes the new dimensions.
                        currentOnPresenterChanged(AndroidSurfacePresenter(worker, resizedSurface))
                        frameCallbackSent = false
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
                        // Only dispatch once per surface, and only when a real frame is available
                        if (!frameCallbackSent && currentOnFrameCaptured != null) {
                            val bmp = bitmap
                            if (bmp != null) {
                                frameCallbackSent = true
                                // Post the callback to the next frame to ensure the bitmap is fully rendered.
                                // Prevents race conditions where the callback is invoked before the
                                // draw command has completed rendering to the surface.
                                post {
                                    val callback = currentOnFrameCaptured ?: return@post
                                    val frame = bitmap
                                        ?: error("Bitmap no longer available; surface may have been destroyed")
                                    callback(frame.asImageBitmap())
                                }
                            }
                        }
                    }
                }
            }
        },
        update = { textureView ->
            textureView.applyRequestedFrameRateHint(
                frameRate = frameRate,
                active = frameRateActive
            )
        }
    )
}

internal actual fun monotonicTimeNanos(): Long = System.nanoTime()

/**
 * [SurfacePresenter] for previews: draws to an offscreen buffer through the desktop library and
 * converts the RGBA readback into an Android [android.graphics.Bitmap]-backed [ImageBitmap].
 */
private class InspectionSurfacePresenter(
    private val worker: RiveWorker,
    override val riveSurface: RiveSurface,
    private val onFrame: (ImageBitmap) -> Unit,
) : SurfacePresenter {
    override val width: Int get() = riveSurface.width
    override val height: Int get() = riveSurface.height

    private val pixels = ByteArray(width * height * 4)
    private val argb = IntArray(width * height)

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
        var i = 0
        var pixel = 0
        while (i < pixels.size) {
            val r = pixels[i].toInt() and 0xFF
            val g = pixels[i + 1].toInt() and 0xFF
            val b = pixels[i + 2].toInt() and 0xFF
            val a = pixels[i + 3].toInt() and 0xFF
            val row = pixel / width
            val col = pixel % width
            argb[(height - 1 - row) * width + col] = (a shl 24) or (r shl 16) or (g shl 8) or b
            pixel++
            i += 4
        }
        val bitmap = android.graphics.Bitmap.createBitmap(
            argb,
            width,
            height,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        onFrame(bitmap.asImageBitmap())
    }
}

@Composable
private fun RiveInspectionSurface(
    worker: RiveWorker,
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
        var frameCaptureSent = false
        val presenter = InspectionSurfacePresenter(worker, surface) { newFrame ->
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

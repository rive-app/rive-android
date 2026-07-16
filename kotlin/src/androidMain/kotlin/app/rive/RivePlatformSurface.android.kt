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

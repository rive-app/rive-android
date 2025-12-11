package app.rive

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.rive.core.RebuggerWrapper
import app.rive.core.RiveSurface
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Fit
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.nanoseconds

private const val GENERAL_TAG = "Rive/UI"
private const val STATE_MACHINE_TAG = "Rive/UI/SM"
private const val DRAW_TAG = "Rive/UI/Draw"

@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "The Rive Compose API is experimental and may change in the future. Opt-in is required."
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.TYPEALIAS)
annotation class ExperimentalRiveComposeAPI

/**
 * Represents the result of an operation - typically loading - that can be in a loading, error,
 * or success state. This includes Rive file loading. The Success result must be unwrapped to the
 * value, e.g. through Kotlin's when/is statements.
 */
sealed interface Result<out T> {
    object Loading : Result<Nothing>
    data class Error(val throwable: Throwable) : Result<Nothing>
    data class Success<T>(val value: T) : Result<T>

    @Composable
    fun <T, R> Result<T>.andThen(
        onSuccess: @Composable (T) -> Result<R>
    ): Result<R> = when (this) {
        is Loading -> Loading
        is Error -> Error(this.throwable)
        is Success -> onSuccess(this.value)
    }
}

/** Function type for getting a Bitmap. */
typealias GetBitmapFun = () -> Bitmap

/**
 * The main composable for rendering a Rive file's artboard and state machine.
 *
 * Internally, RiveUI uses a [TextureView] to create and manage a [Surface] for rendering.
 *
 * The composable will advance the state machine and draw the artboard on every frame while the
 * [Lifecycle] is in the [Lifecycle.State.RESUMED] state. It will also handle pointer input events
 * to influence the state machine, such as pointer down, move, and up events.
 *
 * A RiveUI composable can enter a settled state, where it stops advancing the state machine. It
 * will be restarted when influenced by other events, such as pointer input or view model instance
 * changes.
 *
 * @param file The [RiveFile] that created the artboard and state machine.
 * @param modifier The [Modifier] to apply to the composable.
 * @param playing Whether the state machine should advance. When true (default), the state machine
 *    will advance on each frame. When false, the advancement loop will not activate.
 * @param artboard The [Artboard] to render. If null, the default artboard will be used.
 * @param stateMachine The [StateMachine] to use. If null, the default state machine will be
 *    created.
 * @param viewModelInstance The [ViewModelInstance] to bind to the state machine. If null, no view
 *    model instance will be bound.
 * @param fit The [Fit] to use for the artboard. Defaults to [Fit.CONTAIN].
 * @param alignment The [Alignment] to use for the artboard. Defaults to [Alignment.CENTER].
 * @param clearColor The color to clear the surface with before drawing. Defaults to transparent.
 * @param onBitmapAvailable Optional callback that is invoked when the first bitmap frame is
 *    available. The callback provides a function to get the current [Bitmap] from the underlying
 *    [TextureView]. This can be used for snapshot testing or storing rendered output. The bitmap
 *    getter is only valid while the surface is active.
 */
@ExperimentalRiveComposeAPI
@Composable
fun RiveUI(
    file: RiveFile,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
    artboard: Artboard? = null,
    stateMachine: StateMachine? = null,
    viewModelInstance: ViewModelInstance? = null,
    fit: Fit = Fit.CONTAIN,
    alignment: Alignment = Alignment.CENTER,
    clearColor: Int = Color.Transparent.toArgb(),
    onBitmapAvailable: ((getBitmap: GetBitmapFun) -> Unit)? = null,
) {
    RiveLog.v(GENERAL_TAG) { "RiveUI Recomposing" }
    val lifecycleOwner = LocalLifecycleOwner.current

    val commandQueue = file.commandQueue

    /** Use provided artboard or create a default one. */
    val artboardToUse = remember(file.fileHandle, artboard) {
        artboard ?: Artboard.fromFile(file)
    }
    val artboardHandle = artboardToUse.artboardHandle

    /** Clean up the artboard if we created it internally. */
    DisposableEffect(artboardToUse) {
        onDispose {
            if (artboard == null) {
                artboardToUse.close()
            }
        }
    }

    /** Use provided state machine or create a default one. */
    val stateMachineToUse = remember(file.fileHandle, artboardHandle, stateMachine) {
        stateMachine ?: StateMachine.fromArtboard(
            artboardToUse,
            null
        )
    }
    val stateMachineHandle = stateMachineToUse.stateMachineHandle
    var isSettled by remember(stateMachineHandle) { mutableStateOf(false) }

    /** Clean up for the state machine if we created it internally. */
    DisposableEffect(stateMachineToUse) {
        onDispose {
            if (stateMachine == null) {
                stateMachineToUse.close()
            }
        }
    }

    var surface by remember { mutableStateOf<RiveSurface?>(null) }
    var surfaceWidth by remember { mutableIntStateOf(0) }
    var surfaceHeight by remember { mutableIntStateOf(0) }

    val currentOnBitmapAvailable by rememberUpdatedState(onBitmapAvailable)
    var bitmapCallbackSent by remember { mutableStateOf(false) }

    // In debug builds, output the reasons for recomposition
    RebuggerWrapper(
        trackMap = mapOf(
            "file" to file,
            "artboardHandle" to artboardHandle,
            "stateMachineHandle" to stateMachineHandle,
            "surface" to surface,
            "fit" to fit,
            "alignment" to alignment,
            "lifecycleOwner" to lifecycleOwner,
            "artboard" to artboard,
            "stateMachine" to stateMachine,
            "playing" to playing,
        )
    )

    /** Bind the view model instance to the state machine. */
    LaunchedEffect(stateMachineHandle, viewModelInstance) {
        if (viewModelInstance == null) {
            RiveLog.d(VM_INSTANCE_TAG) { "No view model instance to bind for $stateMachineHandle" }
            return@LaunchedEffect
        }

        RiveLog.d(VM_INSTANCE_TAG) { "Binding view model instance ${viewModelInstance.instanceHandle}" }
        commandQueue.bindViewModelInstance(
            stateMachineHandle,
            viewModelInstance.instanceHandle
        )

        // Advance to exit the "Entry" state and apply initial values
        commandQueue.advanceStateMachine(stateMachineHandle, 0.nanoseconds)

        // Assigning a view model instance unsettles the state machine
        isSettled = false

        if (!playing) {
            commandQueue.draw(
                artboardHandle,
                stateMachineHandle,
                fit,
                alignment,
                surface!!,
                clearColor
            )
        }

        // Subscribe to the instance's dirty flow to unsettle when properties change
        viewModelInstance.dirtyFlow.collect {
            RiveLog.v(VM_INSTANCE_TAG) { "View model instance dirty, unsettling $stateMachineHandle" }
            isSettled = false
        }
    }

    /** Clean up for the surface. */
    DisposableEffect(surface) {
        val nonNullSurface = surface ?: return@DisposableEffect onDispose {}
        onDispose {
            commandQueue.destroyRiveSurface(nonNullSurface)
        }
    }

    /** Listen for settle events for this state machine. */
    LaunchedEffect(stateMachineHandle) {
        commandQueue.settledFlow
            .filter { it.handle == stateMachineHandle.handle }
            .collect {
                RiveLog.v(STATE_MACHINE_TAG) { "State machine $stateMachineHandle settled" }
                isSettled = true
            }
    }

    /**
     * Changing the fit, alignment, or clear color unsettles the state machine, forcing a re-draw.
     */
    LaunchedEffect(fit, alignment, clearColor) {
        isSettled = false
    }

    /** Drawing loop while RESUMED. */
    LaunchedEffect(
        lifecycleOwner,
        surface,
        artboardHandle,
        stateMachineHandle,
        fit,
        alignment,
        clearColor,
        playing,
    ) {
        if (surface == null) {
            RiveLog.d(DRAW_TAG) { "Surface is null, skipping drawing" }
            return@LaunchedEffect
        }
        if (!playing) {
            RiveLog.d(DRAW_TAG) { "Playing is false, skipping advancement loop" }

            commandQueue.draw(
                artboardHandle,
                stateMachineHandle,
                fit,
                alignment,
                surface!!,
                clearColor
            )

            return@LaunchedEffect
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            RiveLog.d(DRAW_TAG) { "Starting drawing with $artboardHandle and $stateMachineHandle" }
            var lastFrameTime = 0.nanoseconds
            while (isActive) {
                val deltaTime = withFrameNanos { frameTimeNs ->
                    val frameTime = frameTimeNs.nanoseconds
                    (if (lastFrameTime == 0.nanoseconds) 0.nanoseconds else frameTime - lastFrameTime).also {
                        lastFrameTime = frameTime
                    }
                }

                // Skip advance and draw when settled
                if (isSettled) {
                    continue
                }

                commandQueue.advanceStateMachine(stateMachineHandle, deltaTime)
                commandQueue.draw(
                    artboardHandle,
                    stateMachineHandle,
                    fit,
                    alignment,
                    surface!!,
                    clearColor
                )
            }
            RiveLog.d(DRAW_TAG) { "Ending drawing with $artboardHandle and $stateMachineHandle" }
        }
    }

    AndroidView(
        factory = { context ->
            TextureView(context).apply {
                isOpaque = false

                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        newSurfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        RiveLog.d(GENERAL_TAG) { "Surface texture available ($width x $height)" }
                        surface = commandQueue.createRiveSurface(newSurfaceTexture)
                        surfaceWidth = width
                        surfaceHeight = height
                        // Because this is a new surface, we send a fresh callback
                        bitmapCallbackSent = false
                    }

                    override fun onSurfaceTextureDestroyed(destroyedSurfaceTexture: SurfaceTexture): Boolean {
                        RiveLog.d(GENERAL_TAG) { "Surface texture destroyed (final release deferred to RenderContext disposal)" }
                        surface = null
                        bitmapCallbackSent = false
                        // False here means that we are responsible for destroying the surface texture
                        // This happens in RenderContext::close(), called from CommandQueue::destroyRiveSurface
                        return false
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        RiveLog.d(GENERAL_TAG) { "Surface texture size changed ($width x $height)" }
                        surfaceWidth = width
                        surfaceHeight = height
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
                        // Only dispatch once per surface, and only when a real frame is available
                        if (!bitmapCallbackSent) {
                            val bmp = bitmap
                            if (bmp != null) {
                                bitmapCallbackSent = true
                                currentOnBitmapAvailable?.invoke {
                                    // Getter is safe because we only expose it after first non-null frame
                                    bitmap
                                        ?: error("Bitmap no longer available; surface may have been destroyed")
                                }
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier.then(
            Modifier.pointerInput(
                stateMachineHandle,
                fit,
                alignment,
                surfaceWidth,
                surfaceHeight
            ) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()

                        // Pointer events unsettle the state machine
                        isSettled = false

                        val pointerFn = when (event.type) {
                            PointerEventType.Move -> commandQueue::pointerMove
                            PointerEventType.Release -> commandQueue::pointerUp
                            PointerEventType.Press -> commandQueue::pointerDown
                            PointerEventType.Exit -> commandQueue::pointerExit
                            else -> continue // Ignore other pointer events
                        }
                        event.changes.forEach { change ->
                            val pointerPosition = change.position
                            pointerFn(
                                stateMachineHandle,
                                fit,
                                alignment,
                                surfaceWidth.toFloat(),
                                surfaceHeight.toFloat(),
                                change.id.value.toInt(),
                                pointerPosition.x,
                                pointerPosition.y
                            )
                        }
                    }
                }
            }
        )
    )
}

package app.rive

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive

const val GENERAL_TAG = "RiveUI"
const val STATE_MACHINE_TAG = "RiveUI/SM"
const val VM_INSTANCE_TAG = "RiveUI/VMI"
const val SURFACE_TAG = "RiveUI/Surface"
const val DRAW_TAG = "RiveUI/Draw"

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
}

internal fun <T> lazyDeferred(
    parentScope: CoroutineScope,
    block: suspend () -> T
): Lazy<Deferred<T>> =
    lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        parentScope.async(start = CoroutineStart.LAZY) {
            block()
        }
    }

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
 * @param artboard The [Artboard] to render. If null, the default artboard will be used.
 * @param stateMachineName The name of the state machine to use. If null, the default state machine
 *    will be used.
 * @param viewModelInstance The [ViewModelInstance] to bind to the state machine. If null, no view
 *    model instance will be bound.
 * @param fit The [Fit] to use for the artboard. Defaults to [Fit.CONTAIN].
 * @param alignment The [Alignment] to use for the artboard. Defaults to [Alignment.CENTER].
 * @param clearColor The color to clear the surface with before drawing. Defaults to transparent.
 */
@ExperimentalRiveComposeAPI
@Composable
fun RiveUI(
    file: RiveFile,
    modifier: Modifier = Modifier,
    artboard: Artboard? = null,
    stateMachineName: String? = null,
    viewModelInstance: ViewModelInstance? = null,
    fit: Fit = Fit.CONTAIN,
    alignment: Alignment = Alignment.CENTER,
    clearColor: Int = Color.Transparent.toArgb()
) {
    RiveLog.v(GENERAL_TAG) { "RiveUI Recomposing" }
    val lifecycleOwner = LocalLifecycleOwner.current

    val commandQueue = file.commandQueue

    val artboardHandle = remember(file.fileHandle, artboard) {
        artboard?.artboardHandle ?: commandQueue.createDefaultArtboard(file.fileHandle)
    }
    val stateMachineHandle = remember(artboardHandle, stateMachineName) {
        val handle = if (stateMachineName != null)
            commandQueue.createStateMachineByName(artboardHandle, stateMachineName)
        else
            commandQueue.createDefaultStateMachine(artboardHandle)

        RiveLog.d(STATE_MACHINE_TAG) { "Created $handle with name $stateMachineName (${artboardHandle}; ${file.fileHandle})" }

        handle
    }
    var isSettled by remember(stateMachineHandle) { mutableStateOf(false) }

    var surface by remember { mutableStateOf<RiveSurface?>(null) }
    var surfaceWidth by remember { mutableIntStateOf(0) }
    var surfaceHeight by remember { mutableIntStateOf(0) }
    val surfaceListener = remember {
        object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                newSurfaceTexture: SurfaceTexture, width: Int, height: Int
            ) {
                val newSurface = Surface(newSurfaceTexture)
                RiveLog.d(SURFACE_TAG) { "Creating rendering surface" }
                surface = commandQueue.createRiveSurface(newSurface)
                surfaceWidth = width
                surfaceHeight = height
            }

            override fun onSurfaceTextureDestroyed(destroyedSurfaceTexture: SurfaceTexture): Boolean {
                surface = null
                return true
            }

            override fun onSurfaceTextureSizeChanged(
                surfaceTexture: SurfaceTexture, width: Int, height: Int
            ) {
                surfaceWidth = width
                surfaceHeight = height
            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
        }
    }

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
            "stateMachineName" to stateMachineName,
        )
    )

    /** Clean up for the state machine. */
    DisposableEffect(stateMachineHandle) {
        onDispose {
            RiveLog.d(STATE_MACHINE_TAG) { "Deleting $stateMachineHandle with name $stateMachineName ($artboardHandle; ${file.fileHandle})" }
            commandQueue.deleteStateMachine(stateMachineHandle)
        }
    }

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

        // Assigning a view model instance unsettles the state machine
        isSettled = false

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
            RiveLog.d(SURFACE_TAG) { "Deleting surface" }
            nonNullSurface.dispose()
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
    ) {
        if (surface == null) {
            RiveLog.d(DRAW_TAG) { "Surface is null, skipping drawing" }
            return@LaunchedEffect
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            RiveLog.d(DRAW_TAG) { "Starting drawing with $artboardHandle and $stateMachineHandle" }
            var lastFrameTimeNs = 0L
            while (isActive) {
                val deltaTimeNs = withFrameNanos { frameTimeNs ->
                    (if (lastFrameTimeNs == 0L) 0L else frameTimeNs - lastFrameTimeNs).also {
                        lastFrameTimeNs = frameTimeNs
                    }
                }

                // Skip advance and draw when settled
                if (isSettled) {
                    continue
                }

                commandQueue.advanceStateMachine(stateMachineHandle, deltaTimeNs)
                commandQueue.draw(
                    artboardHandle,
                    stateMachineHandle,
                    fit,
                    alignment,
                    surface!!,
                    clearColor
                )
            }
        }
    }

    AndroidView(
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = surfaceListener
                isOpaque = false
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
                        val pointerPosition = event.changes.first().position
                        pointerFn(
                            stateMachineHandle,
                            fit,
                            alignment,
                            surfaceWidth.toFloat(),
                            surfaceHeight.toFloat(),
                            pointerPosition.x,
                            pointerPosition.y
                        )
                    }
                }
            }
        )
    )
}

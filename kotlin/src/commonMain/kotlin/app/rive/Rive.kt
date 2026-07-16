package app.rive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputFilter
import androidx.compose.ui.input.pointer.PointerInputModifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.rive.RivePointerInputMode.Consume
import app.rive.RivePointerInputMode.PassThrough
import app.rive.core.RenderingDefaults
import app.rive.core.traceSection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.nanoseconds

private const val GENERAL_TAG = "Rive/UI"
private const val STATE_MACHINE_TAG = "Rive/UI/SM"
private const val DRAW_TAG = "Rive/UI/Draw"

/**
 * Controls how a Rive composable participates in Compose pointer input dispatch.
 * - [Consume]: Rive handles pointer events and consumes them, preventing parent/ancestor gesture
 *   detectors (e.g., scroll) from also acting.
 * - [Observe]: Rive handles pointer events but does not consume them. Parent/ancestor gesture
 *   detectors may also react.
 * - [PassThrough]: Rive handles pointer events and also shares them with any sibling composables
 *   positioned underneath it without consuming. Useful if your Rive file is an overlay with
 *   transparent sections that should allow pointer events through.
 */
enum class RivePointerInputMode {
    Consume,
    Observe,
    PassThrough,
}

/**
 * The main composable for rendering a Rive file's artboard and state machine.
 *
 * The composable will advance the state machine and draw the artboard on every frame while the
 * [Lifecycle] is in the [Lifecycle.State.RESUMED] state. It will also handle pointer input events
 * to influence the state machine, such as pointer down, move, and up events.
 *
 * A Rive composable can enter a settled state, where it stops advancing the state machine. It
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
 * @param fit The [Fit] to use for the artboard. Defaults to [Fit.Contain].
 * @param backgroundColor The color to clear the surface with before drawing. Defaults to
 *    transparent.
 * @param pointerInputMode Controls how pointer events are handled and consumed by Rive. See
 *    [RivePointerInputMode]. Default is [RivePointerInputMode.Consume].
 * @param frameRate Controls how often Rive advances and draws while [playing] is true. Defaults to
 *    [RiveFrameRate.Unbounded], which renders on every platform frame callback. On supported
 *    Android versions, capped rates are also used as an advisory view frame-rate hint.
 * @param onFrameCaptured Optional callback that is invoked once per surface with the first
 *    rendered frame as an [ImageBitmap]. This can be used for snapshot testing or storing
 *    rendered output.
 */
@Composable
fun Rive(
    file: RiveFile,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
    artboard: Artboard? = null,
    stateMachine: StateMachine? = null,
    viewModelInstance: ViewModelInstance? = null,
    fit: Fit = RenderingDefaults.defaultFit(),
    backgroundColor: Int = RenderingDefaults.CLEAR_COLOR,
    pointerInputMode: RivePointerInputMode = Consume,
    frameRate: RiveFrameRate = RiveFrameRate.Unbounded,
    onFrameCaptured: ((ImageBitmap) -> Unit)? = null,
) {
    RiveLog.v(GENERAL_TAG) { "Rive Recomposing" }
    val lifecycleOwner = LocalLifecycleOwner.current

    val riveWorker = file.riveWorker

    /** Use provided artboard or create a default one. */
    val artboardToUse = artboard ?: rememberArtboard(file)
    val artboardHandle = artboardToUse.artboardHandle

    /** Use provided state machine or create a default one. */
    val stateMachineToUse = stateMachine ?: rememberStateMachine(artboardToUse)
    val stateMachineHandle = stateMachineToUse.stateMachineHandle
    var isSettled by remember(stateMachineHandle) { mutableStateOf(false) }

    var presenter by remember { mutableStateOf<SurfacePresenter?>(null) }
    var surfaceWidth by remember { mutableIntStateOf(0) }
    var surfaceHeight by remember { mutableIntStateOf(0) }

    /** Bind the view model instance to the state machine. */
    LaunchedEffect(stateMachineHandle, viewModelInstance) {
        if (viewModelInstance == null) {
            RiveLog.d(VM_INSTANCE_TAG) { "No view model instance to bind for $stateMachineHandle" }
            return@LaunchedEffect
        }

        RiveLog.d(VM_INSTANCE_TAG) { "Binding view model instance ${viewModelInstance.instanceHandle}" }
        riveWorker.bindViewModelInstance(
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

    /** Listen for settle events for this state machine. */
    LaunchedEffect(stateMachineHandle) {
        riveWorker.settledFlow
            .filter { it.handle == stateMachineHandle.handle }
            .collect {
                RiveLog.v(STATE_MACHINE_TAG) { "State machine $stateMachineHandle settled" }
                isSettled = true
            }
    }

    /**
     * Changing the fit, alignment, layout scale factor, or clear color unsettles the state machine,
     * forcing a re-draw.
     */
    LaunchedEffect(fit, backgroundColor) {
        RiveLog.d(STATE_MACHINE_TAG) {
            "State machine $stateMachineHandle unsettled due to parameter change"
        }
        isSettled = false
    }

    /** Resize artboard based on fit parameter. */
    LaunchedEffect(fit, presenter, surfaceWidth, surfaceHeight) {
        val activeSurface = presenter?.riveSurface ?: return@LaunchedEffect
        when (fit) {
            is Fit.Layout -> {
                traceSection("Rive/Layout/ResizeArtboard") {
                    RiveLog.d(GENERAL_TAG) { "Resizing artboard to $surfaceWidth x $surfaceHeight" }
                    artboardToUse.resizeArtboard(activeSurface, fit.scaleFactor)
                }
            }

            else -> {
                traceSection("Rive/Layout/ResetArtboardSize") {
                    RiveLog.d(GENERAL_TAG) { "Resetting artboard size" }
                    artboardToUse.resetArtboardSize()
                }
            }
        }
    }

    /** Drawing loop while RESUMED. */
    LaunchedEffect(
        lifecycleOwner,
        presenter,
        artboardHandle,
        stateMachineHandle,
        viewModelInstance,
        fit,
        backgroundColor,
        playing,
        frameRate,
    ) {
        if (presenter == null) {
            RiveLog.d(DRAW_TAG) { "Surface is null, skipping drawing" }
            return@LaunchedEffect
        }
        if (!playing) {
            RiveLog.d(DRAW_TAG) {
                "Playing is false. Advancing by 0, drawing once, and skipping advancement loop."
            }

            traceSection("Rive/Frame") {
                traceSection("Rive/Frame/Advance") {
                    // Advance the state machine once to exit the "Entry" state and apply initial values,
                    // including any pending artboard resizes from the fit mode.
                    stateMachineToUse.advance(0.nanoseconds)
                }
                val drawPresenter = presenter ?: run {
                    RiveLog.d(DRAW_TAG) { "Surface was released before draw, skipping frame" }
                    return@traceSection
                }
                traceSection("Rive/Frame/Draw") {
                    drawPresenter.draw(
                        artboardHandle,
                        stateMachineHandle,
                        fit,
                        backgroundColor
                    )
                }
            }

            return@LaunchedEffect
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            RiveLog.d(DRAW_TAG) { "Starting drawing with $artboardHandle and $stateMachineHandle" }
            val framePacer = RiveFramePacer(frameRate)
            var lastFrameTimeNs = 0L
            while (isActive) {
                if (isSettled) {
                    traceSection("Rive/Frame/SettledSuspend") {
                        snapshotFlow { isSettled }.first { !it }
                    }
                    lastFrameTimeNs = 0L
                    framePacer.reset()
                    continue
                }

                val frameDelay = framePacer.delayBeforeNextFrame(monotonicTimeNanos())
                if (frameDelay > ZERO) {
                    delay(frameDelay)
                }
                if (isSettled) {
                    continue
                }

                // Because we cannot break the outer loop directly from inside a traceSection lambda
                var stopDrawLoop = false

                val frameTimeNs = withFrameNanos { frameTimeNs -> frameTimeNs }

                // Settled events can arrive while withFrameNanos is suspended.
                if (isSettled) {
                    continue
                }
                // FPS cap gate: skip platform frames that arrive before the next Rive frame is due.
                if (!framePacer.tryScheduleFrame(frameTimeNs)) {
                    continue
                }

                val deltaTime = if (lastFrameTimeNs == 0L) {
                    ZERO
                } else {
                    (frameTimeNs - lastFrameTimeNs).nanoseconds
                }
                lastFrameTimeNs = frameTimeNs

                traceSection("Rive/Frame") {
                    val drawPresenter = presenter
                    if (drawPresenter == null) {
                        RiveLog.d(DRAW_TAG) { "Surface was released during draw, stopping draw loop" }
                        stopDrawLoop = true
                        return@traceSection
                    }
                    traceSection("Rive/Frame/Advance") {
                        riveWorker.advanceStateMachine(stateMachineHandle, deltaTime)
                    }
                    traceSection("Rive/Frame/Draw") {
                        drawPresenter.draw(
                            artboardHandle,
                            stateMachineHandle,
                            fit,
                            backgroundColor
                        )
                    }
                }
                if (stopDrawLoop) {
                    return@repeatOnLifecycle
                }
            }
            RiveLog.d(DRAW_TAG) { "Ending drawing with $artboardHandle and $stateMachineHandle" }
        }
    }

    /**
     * A wrapper for the interior platform surface, since it may handle pointer inputs in a
     * non-standard way by passing through all touch events. This gives us a standard Composable to
     * handle pointer events. Effectively a Box, but without pulling in the dependency on the
     * Layout lib.
     */
    @Composable
    fun SingleChildLayout(
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) {
        Layout(
            content = content,
            modifier = modifier
        ) { measurables, constraints ->
            val placeable = measurables.single().measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.place(0, 0)
            }
        }
    }

    val passThroughInputModifier = object : PointerInputModifier {
        override val pointerInputFilter: PointerInputFilter =
            object : PointerInputFilter() {
                override fun onPointerEvent(
                    pointerEvent: PointerEvent,
                    pass: PointerEventPass,
                    bounds: IntSize
                ) {
                    traceSection("Rive/PointerInput") {
                        // Only handle the main pass so we don't double-dispatch.
                        if (pass != PointerEventPass.Main) return@traceSection

                        // Pointer events unsettle the state machine.
                        isSettled = false

                        val pointerFns = when (pointerEvent.type) {
                            PointerEventType.Move -> listOf(riveWorker::pointerMove)
                            // On release, Rive expects both up + exit (logically "exiting" on the Z axis).
                            PointerEventType.Release -> listOf(
                                riveWorker::pointerUp,
                                riveWorker::pointerExit
                            )

                            PointerEventType.Press -> listOf(riveWorker::pointerDown)
                            PointerEventType.Exit -> listOf(riveWorker::pointerExit)
                            else -> return@traceSection // Ignore other pointer events
                        }

                        pointerEvent.changes.forEach { change ->
                            val pointerPosition = change.position
                            pointerFns.forEach { fn ->
                                fn(
                                    stateMachineHandle,
                                    fit,
                                    surfaceWidth.toFloat(),
                                    surfaceHeight.toFloat(),
                                    change.id.value.toInt(),
                                    pointerPosition.x,
                                    pointerPosition.y
                                )
                            }
                            // Only consume in Consume mode. Observe/PassThrough do not consume.
                            if (pointerInputMode == Consume) {
                                change.consume()
                            }
                        }
                    }
                }

                override fun onCancel() {}
                override val shareWithSiblings: Boolean =
                    pointerInputMode == PassThrough
            }
    }

    SingleChildLayout(modifier = modifier.then(passThroughInputModifier)) {
        RivePlatformSurface(
            worker = riveWorker,
            frameRate = frameRate,
            frameRateActive = playing && !isSettled,
            onPresenterChanged = { newPresenter ->
                presenter = newPresenter
                surfaceWidth = newPresenter?.width ?: 0
                surfaceHeight = newPresenter?.height ?: 0
            },
            onFrameCaptured = onFrameCaptured,
            modifier = Modifier,
        )
    }
}

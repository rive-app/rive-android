package app.rive

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputFilter
import androidx.compose.ui.input.pointer.PointerInputModifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.rive.RivePointerInputMode.Consume
import app.rive.RivePointerInputMode.PassThrough
import app.rive.core.RebuggerWrapper
import app.rive.core.RenderingDefaults
import app.rive.core.RiveSurface
import app.rive.core.SurfaceTextureSurface
import app.rive.core.traceSection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.nanoseconds

private const val GENERAL_TAG = "Rive/UI"
private const val STATE_MACHINE_TAG = "Rive/UI/SM"
private const val DRAW_TAG = "Rive/UI/Draw"

/** Function type for getting a Bitmap. */
typealias GetBitmapFun = () -> Bitmap

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
 * Validates the Rive resources supplied to [Rive] before composition creates default resources.
 *
 * @param file The file whose content will be rendered.
 * @param artboard An optional artboard that must have been created from [file].
 * @param stateMachine An optional state machine created from [artboard]. A state machine cannot be
 *    supplied without its originating artboard.
 * @param viewModelInstance An optional view model instance owned by [file]'s worker. An instance
 *    from another file is compatible only when used with relative data-binding paths authored in
 *    the Rive editor; cross-file use with absolute paths is unsupported.
 * @throws RiveResourceClosedException If any supplied resource has been closed.
 * @throws RiveIncompatibleResourceException If [artboard], [stateMachine], or [viewModelInstance]
 *    cannot be used together, or if [stateMachine] is supplied without [artboard].
 */
@Throws(RiveResourceClosedException::class, RiveIncompatibleResourceException::class)
internal fun validateRiveResourceArguments(
    file: RiveFile,
    artboard: Artboard?,
    stateMachine: StateMachine?,
    viewModelInstance: ViewModelInstance?,
) {
    file.checkOpen()
    artboard?.checkOpen()
    stateMachine?.checkOpen()
    viewModelInstance?.checkOpen()
    artboard?.requireFromFile(file)
    if (stateMachine != null) {
        if (artboard == null) {
            throw RiveIncompatibleResourceException(
                "StateMachine ${stateMachine.stateMachineHandle} requires its originating Artboard"
            )
        }
        stateMachine.requireFromArtboard(artboard)
    }
    // Cross-file VMIs are supported through relative paths authored in the editor. Because the
    // binding mode is not available here, worker ownership is the compatibility boundary;
    // unsupported absolute cross-file bindings cannot be rejected synchronously.
    viewModelInstance?.requireOwnedBy(file.riveWorker)
}

/**
 * The main composable for rendering a Rive file's artboard and state machine.
 *
 * Internally, Rive uses a [TextureView] to create and manage a [android.view.Surface] for
 * rendering.
 *
 * The composable will advance the state machine and draw the artboard on every frame while the
 * [Lifecycle] is in the [Lifecycle.State.RESUMED] state. It will also handle pointer input events
 * to influence the state machine, such as pointer down, move, and up events.
 *
 * A Rive composable can enter a settled state, where it stops advancing the state machine. It
 * will be restarted when influenced by other events, such as pointer input or view model instance
 * changes.
 *
 * When [artboard] or [stateMachine] is null, its default is created asynchronously. This
 * composable emits no UI while either resource is loading and automatically recomposes when its
 * creation state changes. If implicit creation fails, it continues to emit no UI and does not
 * expose the error.
 *
 * To display loading or error UI, create the resources with [rememberArtboardResult] and
 * [rememberStateMachineResult], handle their [Result] states, and supply the successful [artboard]
 * and [stateMachine] here. Any resource left null retains the silent implicit-creation behavior
 * described above.
 *
 * @param file The [RiveFile] that created the artboard and state machine.
 * @param modifier The [Modifier] to apply to the composable.
 * @param playing Whether the state machine should advance. When true (default), the state machine
 *    will advance on each frame. When false, the advancement loop will not activate.
 * @param artboard The [Artboard] to render. If null, the default artboard will be used.
 * @param stateMachine The [StateMachine] to use. It must have been created from [artboard], which
 *    must also be supplied. If null, the default state machine for the selected artboard will be
 *    created.
 * @param viewModelInstance The [ViewModelInstance] to bind to the state machine. An instance from
 *    another file on the same Rive worker is supported only when the state machine uses relative
 *    data-binding paths authored in the Rive editor. Cross-file binding with absolute paths is
 *    unsupported. If null, no view model instance will be bound.
 * @param fit The [Fit] to use for the artboard. Defaults to [Fit.Contain].
 * @param backgroundColor The color to clear the surface with before drawing. Defaults to
 *    transparent.
 * @param pointerInputMode Controls how pointer events are handled and consumed by Rive. See
 *    [RivePointerInputMode]. Default is [RivePointerInputMode.Consume].
 * @param frameRate Controls how often Rive advances and draws while [playing] is true. Defaults to
 *    [RiveFrameRate.Unbounded], which renders on every platform frame callback. On supported
 *    Android versions, capped rates are also used as an advisory view frame-rate hint.
 * @param onBitmapAvailable Optional callback that is invoked when the first bitmap frame is
 *    available. The callback provides a function to get the current [Bitmap] from the underlying
 *    [TextureView]. This can be used for snapshot testing or storing rendered output. The bitmap
 *    getter is only valid while the surface is active.
 * @throws RiveResourceClosedException If [file] or a supplied Rive resource has been closed, or if
 *    the owning Rive worker has been disposed.
 * @throws RiveIncompatibleResourceException If a supplied resource cannot be used with [file] or
 *    the selected artboard.
 */
@Throws(RiveResourceClosedException::class, RiveIncompatibleResourceException::class)
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
    onBitmapAvailable: ((getBitmap: GetBitmapFun) -> Unit)? = null,
) {
    validateRiveResourceArguments(file, artboard, stateMachine, viewModelInstance)

    RiveLog.v(GENERAL_TAG) { "Rive Recomposing" }
    val lifecycleOwner = LocalLifecycleOwner.current

    val riveWorker = file.riveWorker

    /** Use the provided artboard or create and own a confirmed default one. */
    val artboardResult = artboard?.let { Result.Success(it) } ?: rememberArtboardResult(file)
    val artboardToUse = when (artboardResult) {
        is Result.Loading, is Result.Error -> return
        is Result.Success -> artboardResult.value
    }
    val artboardHandle = artboardToUse.artboardHandle

    /** Use the provided state machine or create and own a confirmed default one. */
    val stateMachineResult = stateMachine?.let { Result.Success(it) }
        ?: rememberStateMachineResult(artboardToUse)
    val stateMachineToUse = when (stateMachineResult) {
        is Result.Loading, is Result.Error -> return
        is Result.Success -> stateMachineResult.value
    }
    val stateMachineHandle = stateMachineToUse.stateMachineHandle
    val isSettled by stateMachineToUse.settled.collectAsState()

    var surface by remember { mutableStateOf<RiveSurface?>(null) }
    var surfaceWidth by remember { mutableIntStateOf(0) }
    var surfaceHeight by remember { mutableIntStateOf(0) }

    /** Clean up for the surface. */
    DisposableEffect(surface) {
        val nonNullSurface = surface ?: return@DisposableEffect onDispose {}
        onDispose {
            nonNullSurface.close()
        }
    }

    val currentOnBitmapAvailable by rememberUpdatedState(onBitmapAvailable)
    var bitmapCallbackSent by remember { mutableStateOf(false) }

    // In debug builds, output the reasons for recomposition
    RebuggerWrapper(
        trackMap = mapOf(
            "file" to file,
            "playing" to playing,
            "frameRate" to frameRate,
            "artboard" to artboard,
            "artboardHandle" to artboardHandle,
            "stateMachine" to stateMachine,
            "stateMachineHandle" to stateMachineHandle,
            "viewModelInstance" to viewModelInstance,
            "fit" to fit,
            "backgroundColor" to backgroundColor,
            "surface" to surface,
            "lifecycleOwner" to lifecycleOwner,
        )
    )

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
        stateMachineToUse.unsettle()

        // Subscribe to the instance's dirty flow to unsettle when properties change
        viewModelInstance.dirtyFlow.collect {
            RiveLog.v(VM_INSTANCE_TAG) { "View model instance dirty, unsettling $stateMachineHandle" }
            stateMachineToUse.unsettle()
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
        stateMachineToUse.unsettle()
    }

    /**
     * Update artboard sizing when the fit or surface changes, then unsettle the state machine so
     * the drawing loop can advance and render the updated layout.
     */
    LaunchedEffect(fit, surface, surfaceWidth, surfaceHeight) {
        val activeSurface = surface ?: return@LaunchedEffect
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
        // The queued resize only affects layout when the state machine advances again.
        stateMachineToUse.unsettle()
    }

    /** Start a fresh unsettled generation when playback is resumed. */
    LaunchedEffect(playing) {
        if (playing) {
            stateMachineToUse.unsettle()
        }
    }

    /** Drawing loop while RESUMED. */
    LaunchedEffect(
        lifecycleOwner,
        surface,
        artboardHandle,
        stateMachineHandle,
        viewModelInstance,
        fit,
        backgroundColor,
        playing,
        frameRate,
    ) {
        if (surface == null) {
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
                val drawSurface = surface ?: run {
                    RiveLog.d(DRAW_TAG) { "Surface was released before draw, skipping frame" }
                    return@traceSection
                }
                traceSection("Rive/Frame/Draw") {
                    riveWorker.draw(
                        artboardHandle,
                        stateMachineHandle,
                        drawSurface,
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

                val frameDelay = framePacer.delayBeforeNextFrame(System.nanoTime())
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
                    val drawSurface = surface
                    if (drawSurface == null) {
                        RiveLog.d(DRAW_TAG) { "Surface was released during draw, stopping draw loop" }
                        stopDrawLoop = true
                        return@traceSection
                    }
                    traceSection("Rive/Frame/Advance") {
                        riveWorker.advanceStateMachine(stateMachineHandle, deltaTime)
                    }
                    traceSection("Rive/Frame/Draw") {
                        riveWorker.draw(
                            artboardHandle,
                            stateMachineHandle,
                            drawSurface,
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
     * A wrapper for the interior AndroidView, since it handles pointer inputs in a non-standard way
     * by passing through all touch events. This gives us a standard Composable to handle pointer
     * events. Effectively a Box, but without pulling in the dependency on the Layout lib.
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
                        stateMachineToUse.unsettle()

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
                            surfaceWidth = width
                            surfaceHeight = height
                            surface = riveWorker.createRiveSurface(
                                SurfaceTextureSurface(newSurfaceTexture, width, height)
                            )
                            // Because this is a new surface, we send a fresh callback
                            bitmapCallbackSent = false
                        }

                        override fun onSurfaceTextureDestroyed(destroyedSurfaceTexture: SurfaceTexture): Boolean {
                            RiveLog.d(GENERAL_TAG) { "Surface texture destroyed (final release deferred to RenderContext disposal)" }
                            surface = null
                            bitmapCallbackSent = false
                            // False here means that we are responsible for destroying the surface texture.
                            // This happens when the RiveSurface is closed.
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
                            surface?.resize(width, height)
                            bitmapCallbackSent = false
                        }

                        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
                            // Only dispatch once per surface, and only when a real frame is available
                            if (!bitmapCallbackSent && currentOnBitmapAvailable != null) {
                                val bmp = bitmap
                                if (bmp != null) {
                                    bitmapCallbackSent = true
                                    // Post the callback to the next frame to ensure the bitmap is fully rendered.
                                    // Prevents race conditions where the callback is invoked before the
                                    // draw command has completed rendering to the surface.
                                    post {
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
                }
            },
            update = { textureView ->
                textureView.applyRequestedFrameRateHint(
                    frameRate = frameRate,
                    active = playing && !isSettled
                )
            }
        )
    }
}

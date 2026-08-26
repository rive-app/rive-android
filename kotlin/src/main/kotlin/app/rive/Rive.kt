package app.rive

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.rive.RivePointerInputMode.Consume
import app.rive.RivePointerInputMode.PassThrough
import app.rive.core.RebuggerWrapper
import app.rive.core.RenderingDefaults
import app.rive.core.RiveSurface
import app.rive.core.RiveWorker
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
 * When [artboard] or [stateMachine] is null, its default is created asynchronously. The rendering
 * surface remains composed while either resource is loading, but drawing and pointer input are
 * suspended until both resources are ready. Before the first successful draw the surface is blank;
 * during a replacement it retains its last rendered contents. Implicit creation errors are not
 * exposed.
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
    val readyResources = rememberReadyResources(file, artboard, stateMachine)

    // A surface can only be used by the worker that created it. Key both the surface host and the
    // resource effects so a worker change disposes the entire native presentation generation.
    key(riveWorker) {
        // In debug builds, output the reasons for recomposition.
        RebuggerWrapper(
            trackMap = mapOf(
                "file" to file,
                "playing" to playing,
                "frameRate" to frameRate,
                "artboard" to artboard,
                "artboardHandle" to readyResources?.artboard?.artboardHandle,
                "stateMachine" to stateMachine,
                "stateMachineHandle" to readyResources?.stateMachine?.stateMachineHandle,
                "viewModelInstance" to viewModelInstance,
                "fit" to fit,
                "backgroundColor" to backgroundColor,
                "lifecycleOwner" to lifecycleOwner,
            )
        )

        val surfaceState = rememberRiveSurfaceState()
        RiveSurfaceHost(
            riveWorker = riveWorker,
            surfaceState = surfaceState,
            resources = readyResources,
            modifier = modifier,
            fit = fit,
            pointerInputMode = pointerInputMode,
            frameRate = frameRate,
            playing = playing,
            onBitmapAvailable = onBitmapAvailable,
        )

        if (readyResources != null) {
            RiveResourceEffects(
                riveWorker = riveWorker,
                resources = readyResources,
                viewModelInstance = viewModelInstance,
                fit = fit,
                backgroundColor = backgroundColor,
                playing = playing,
                frameRate = frameRate,
                lifecycleOwner = lifecycleOwner,
                surface = surfaceState.surface,
                surfaceWidth = surfaceState.width,
                surfaceHeight = surfaceState.height,
            )
        }
    }
}

/** Resources that are confirmed ready for native operations in the current composition. */
private data class ReadyResources(
    val artboard: Artboard,
    val stateMachine: StateMachine,
)

/**
 * Resolves the artboard and state machine used by [Rive].
 *
 * Supplied resources are used directly. Missing resources are created asynchronously in dependency
 * order, with the default state machine waiting for its artboard. Loading and error results both
 * remain unavailable because [Rive] intentionally does not expose implicit-creation errors.
 *
 * @param file The file from which missing resources are created.
 * @param artboard The supplied artboard, or null to create the default.
 * @param stateMachine The supplied state machine, or null to create the selected artboard's
 *    default.
 * @return The confirmed resources, or null while creation is loading or after it fails.
 */
@Composable
private fun rememberReadyResources(
    file: RiveFile,
    artboard: Artboard?,
    stateMachine: StateMachine?,
): ReadyResources? {
    val artboardResult = artboard?.let { Result.Success(it) } ?: rememberArtboardResult(file)
    val resolvedArtboard = (artboardResult as? Result.Success)?.value ?: return null

    val stateMachineResult = stateMachine?.let { Result.Success(it) }
        ?: rememberStateMachineResult(resolvedArtboard)
    val resolvedStateMachine = (stateMachineResult as? Result.Success)?.value ?: return null

    return ReadyResources(resolvedArtboard, resolvedStateMachine)
}

/** Mutable worker-scoped surface state shared by rendering effects and the surface host. */
private class RiveSurfaceState {
    var surface by mutableStateOf<RiveSurface?>(null)
    var width by mutableIntStateOf(0)
    var height by mutableIntStateOf(0)
}

/**
 * Remembers the surface state for the current worker-keyed presentation generation.
 *
 * @return The state shared by [RiveSurfaceHost] and [RiveResourceEffects].
 */
@Composable
private fun rememberRiveSurfaceState(): RiveSurfaceState = remember { RiveSurfaceState() }

/**
 * Adds Rive pointer dispatch for one confirmed state machine generation.
 *
 * @receiver The modifier to augment, returned unchanged when [stateMachine] is null.
 * @param riveWorker The worker that dispatches pointer commands.
 * @param stateMachine The state machine to influence, or null while resources are unavailable.
 * @param fit The fit used to map surface coordinates into the artboard.
 * @param surfaceWidth The current surface width in pixels.
 * @param surfaceHeight The current surface height in pixels.
 * @param pointerInputMode The pointer dispatch and consumption behavior.
 * @return This modifier with Rive pointer input appended when [stateMachine] is available.
 */
private fun Modifier.rivePointerInput(
    riveWorker: RiveWorker,
    stateMachine: StateMachine?,
    fit: Fit,
    surfaceWidth: Int,
    surfaceHeight: Int,
    pointerInputMode: RivePointerInputMode,
): Modifier {
    val activeStateMachine = stateMachine ?: return this
    val stateMachineHandle = activeStateMachine.stateMachineHandle
    return then(
        object : PointerInputModifier {
            override val pointerInputFilter: PointerInputFilter =
                object : PointerInputFilter() {
                    override fun onPointerEvent(
                        pointerEvent: PointerEvent,
                        pass: PointerEventPass,
                        bounds: IntSize,
                    ) {
                        traceSection("Rive/PointerInput") {
                            // Only handle the main pass so we don't double-dispatch.
                            if (pass != PointerEventPass.Main) return@traceSection

                            activeStateMachine.unsettle()

                            val pointerFunctions = when (pointerEvent.type) {
                                PointerEventType.Move -> listOf(riveWorker::pointerMove)
                                // Rive expects up and exit when a pointer is released along the Z
                                // axis.
                                PointerEventType.Release -> listOf(
                                    riveWorker::pointerUp,
                                    riveWorker::pointerExit,
                                )

                                PointerEventType.Press -> listOf(riveWorker::pointerDown)
                                PointerEventType.Exit -> listOf(riveWorker::pointerExit)
                                else -> return@traceSection
                            }

                            pointerEvent.changes.forEach { change ->
                                val pointerPosition = change.position
                                pointerFunctions.forEach { pointerFunction ->
                                    pointerFunction(
                                        stateMachineHandle,
                                        fit,
                                        surfaceWidth.toFloat(),
                                        surfaceHeight.toFloat(),
                                        change.id.value.toInt(),
                                        pointerPosition.x,
                                        pointerPosition.y,
                                    )
                                }
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
    )
}

/**
 * Hosts the worker-owned rendering surface and its Compose pointer-input bridge.
 *
 * The host remains composed while [resources] are loading so a same-worker replacement retains
 * the existing surface and its last rendered frame. Pointer input is installed only while one
 * confirmed resource generation is available.
 *
 * @param riveWorker The worker that creates and owns the rendering surface.
 * @param surfaceState The surface state shared with resource-dependent effects.
 * @param resources The confirmed generation used for pointer input and settlement, or null.
 * @param modifier The modifier applied to the single-child surface layout.
 * @param fit The fit used to map pointer coordinates into the artboard.
 * @param pointerInputMode The pointer dispatch and consumption behavior.
 * @param frameRate The requested rendering rate used for the Android view hint.
 * @param playing Whether the state machine should advance continuously.
 * @param onBitmapAvailable The optional callback for the first bitmap on each surface.
 */
@Composable
private fun RiveSurfaceHost(
    riveWorker: RiveWorker,
    surfaceState: RiveSurfaceState,
    resources: ReadyResources?,
    modifier: Modifier,
    fit: Fit,
    pointerInputMode: RivePointerInputMode,
    frameRate: RiveFrameRate,
    playing: Boolean,
    onBitmapAvailable: ((getBitmap: GetBitmapFun) -> Unit)?,
) {
    val activeSurface = surfaceState.surface
    val surfaceWidth = surfaceState.width
    val surfaceHeight = surfaceState.height
    val isSettled = resources?.stateMachine?.settled?.collectAsState()?.value ?: true

    // Close the worker-owned surface when it is replaced or this host leaves composition.
    DisposableEffect(activeSurface) {
        val surfaceToClose = activeSurface ?: return@DisposableEffect onDispose {}
        onDispose { surfaceToClose.close() }
    }

    val currentOnBitmapAvailable by rememberUpdatedState(onBitmapAvailable)
    var bitmapCallbackSent by remember { mutableStateOf(false) }

    val surfaceModifier = modifier.rivePointerInput(
        riveWorker = riveWorker,
        stateMachine = resources?.stateMachine,
        fit = fit,
        surfaceWidth = surfaceWidth,
        surfaceHeight = surfaceHeight,
        pointerInputMode = pointerInputMode,
    )

    // Layout provides a standard Compose pointer-input parent for the pass-through AndroidView.
    Layout(
        content = {
            AndroidView(
                factory = { context ->
                    TextureView(context).apply {
                        isOpaque = false

                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                newSurfaceTexture: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) {
                                RiveLog.d(GENERAL_TAG) {
                                    "Surface texture available ($width x $height)"
                                }
                                surfaceState.width = width
                                surfaceState.height = height
                                surfaceState.surface = riveWorker.createRiveSurface(
                                    SurfaceTextureSurface(newSurfaceTexture, width, height)
                                )
                                bitmapCallbackSent = false
                            }

                            override fun onSurfaceTextureDestroyed(
                                destroyedSurfaceTexture: SurfaceTexture,
                            ): Boolean {
                                RiveLog.d(GENERAL_TAG) {
                                    "Surface texture destroyed (final release deferred to " +
                                        "RenderContext disposal)"
                                }
                                surfaceState.surface = null
                                bitmapCallbackSent = false
                                // RiveSurface remains responsible for destroying the
                                // SurfaceTexture when the render context releases it.
                                return false
                            }

                            override fun onSurfaceTextureSizeChanged(
                                surfaceTexture: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) {
                                RiveLog.d(GENERAL_TAG) {
                                    "Surface texture size changed ($width x $height)"
                                }
                                surfaceState.width = width
                                surfaceState.height = height
                                surfaceState.surface?.resize(width, height)
                                bitmapCallbackSent = false
                            }

                            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
                                // Dispatch once per surface and only after a real frame is ready.
                                if (!bitmapCallbackSent && currentOnBitmapAvailable != null) {
                                    val currentBitmap = bitmap
                                    if (currentBitmap != null) {
                                        bitmapCallbackSent = true
                                        post {
                                            currentOnBitmapAvailable?.invoke {
                                                // The getter is valid only while this surface is
                                                // active.
                                                bitmap ?: error(
                                                    "Bitmap no longer available; surface may have " +
                                                        "been destroyed"
                                                )
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
                        active = playing && !isSettled,
                    )
                },
            )
        },
        modifier = surfaceModifier,
    ) { measurables, constraints ->
        val placeable = measurables.single().measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    }
}

/**
 * Runs the effects that operate on a confirmed artboard and state machine generation.
 *
 * Removing this composable while replacement resources load cancels every resource-dependent
 * effect without removing the worker-scoped rendering surface.
 *
 * @param riveWorker The worker that owns [resources] and [surface].
 * @param resources The artboard and state machine generation to operate on.
 * @param viewModelInstance The optional view model instance to bind.
 * @param fit The fit used for sizing, pointer mapping, and drawing.
 * @param backgroundColor The color used to clear each frame.
 * @param playing Whether the state machine should advance continuously.
 * @param frameRate The requested drawing rate.
 * @param lifecycleOwner The lifecycle that controls the drawing loop.
 * @param surface The current worker-owned rendering surface, or null while it is unavailable.
 * @param surfaceWidth The current surface width in pixels.
 * @param surfaceHeight The current surface height in pixels.
 */
@Composable
private fun RiveResourceEffects(
    riveWorker: RiveWorker,
    resources: ReadyResources,
    viewModelInstance: ViewModelInstance?,
    fit: Fit,
    backgroundColor: Int,
    playing: Boolean,
    frameRate: RiveFrameRate,
    lifecycleOwner: LifecycleOwner,
    surface: RiveSurface?,
    surfaceWidth: Int,
    surfaceHeight: Int,
) {
    val artboard = resources.artboard
    val stateMachine = resources.stateMachine

    BindViewModelInstanceEffect(
        riveWorker = riveWorker,
        stateMachine = stateMachine,
        viewModelInstance = viewModelInstance,
    )
    UnsettleOnPresentationChangeEffect(
        riveWorker = riveWorker,
        stateMachine = stateMachine,
        fit = fit,
        backgroundColor = backgroundColor,
    )
    UnsettleOnPlaybackStartEffect(
        riveWorker = riveWorker,
        stateMachine = stateMachine,
        playing = playing,
    )
    UpdateArtboardLayoutEffect(
        riveWorker = riveWorker,
        artboard = artboard,
        stateMachine = stateMachine,
        fit = fit,
        surface = surface,
        surfaceWidth = surfaceWidth,
        surfaceHeight = surfaceHeight,
    )
    DrawRiveFramesEffect(
        riveWorker = riveWorker,
        lifecycleOwner = lifecycleOwner,
        surface = surface,
        artboard = artboard,
        stateMachine = stateMachine,
        viewModelInstance = viewModelInstance,
        fit = fit,
        backgroundColor = backgroundColor,
        playing = playing,
        frameRate = frameRate,
    )
}

/**
 * Binds a view model instance to one state machine generation and observes its dirty state.
 *
 * @param riveWorker The worker that owns [stateMachine] and [viewModelInstance].
 * @param stateMachine The state machine to bind and unsettle.
 * @param viewModelInstance The view model instance to bind, or null when none is configured.
 */
@Composable
private fun BindViewModelInstanceEffect(
    riveWorker: RiveWorker,
    stateMachine: StateMachine,
    viewModelInstance: ViewModelInstance?,
) {
    val stateMachineHandle = stateMachine.stateMachineHandle
    LaunchedEffect(riveWorker, stateMachine, viewModelInstance) {
        if (viewModelInstance == null) {
            RiveLog.d(VM_INSTANCE_TAG) { "No view model instance to bind for $stateMachineHandle" }
            return@LaunchedEffect
        }

        RiveLog.d(VM_INSTANCE_TAG) {
            "Binding view model instance ${viewModelInstance.instanceHandle}"
        }
        riveWorker.bindViewModelInstance(
            stateMachineHandle,
            viewModelInstance.instanceHandle,
        )

        // Assigning a view model instance unsettles the state machine.
        stateMachine.unsettle()

        // Subscribe to the instance's dirty flow to unsettle when properties change.
        viewModelInstance.dirtyFlow.collect {
            RiveLog.v(VM_INSTANCE_TAG) {
                "View model instance dirty, unsettling $stateMachineHandle"
            }
            stateMachine.unsettle()
        }
    }
}

/**
 * Unsettles one state machine generation when presentation parameters change.
 *
 * @param riveWorker The worker that owns [stateMachine].
 * @param stateMachine The state machine that requires a fresh draw.
 * @param fit The current fit mode.
 * @param backgroundColor The current frame-clear color.
 */
@Composable
private fun UnsettleOnPresentationChangeEffect(
    riveWorker: RiveWorker,
    stateMachine: StateMachine,
    fit: Fit,
    backgroundColor: Int,
) {
    val stateMachineHandle = stateMachine.stateMachineHandle
    LaunchedEffect(riveWorker, stateMachine, fit, backgroundColor) {
        RiveLog.d(STATE_MACHINE_TAG) {
            "State machine $stateMachineHandle unsettled due to parameter change"
        }
        stateMachine.unsettle()
    }
}

/**
 * Unsettles one state machine generation when playback begins or resumes.
 *
 * @param riveWorker The worker that owns [stateMachine].
 * @param stateMachine The state machine to unsettle.
 * @param playing Whether continuous playback is enabled.
 */
@Composable
private fun UnsettleOnPlaybackStartEffect(
    riveWorker: RiveWorker,
    stateMachine: StateMachine,
    playing: Boolean,
) = LaunchedEffect(riveWorker, stateMachine, playing) {
    if (playing) {
        stateMachine.unsettle()
    }
}

/**
 * Updates one artboard generation for the current surface layout and schedules a fresh draw.
 *
 * @param riveWorker The worker that owns [artboard], [stateMachine], and [surface].
 * @param artboard The artboard whose dimensions should be updated.
 * @param stateMachine The state machine that applies the resulting layout.
 * @param fit The fit mode that determines whether the artboard is resized or reset.
 * @param surface The current rendering surface, or null while unavailable.
 * @param surfaceWidth The current surface width in pixels.
 * @param surfaceHeight The current surface height in pixels.
 */
@Composable
private fun UpdateArtboardLayoutEffect(
    riveWorker: RiveWorker,
    artboard: Artboard,
    stateMachine: StateMachine,
    fit: Fit,
    surface: RiveSurface?,
    surfaceWidth: Int,
    surfaceHeight: Int,
) = LaunchedEffect(
        riveWorker,
        artboard,
        stateMachine,
        fit,
        surface,
        surfaceWidth,
        surfaceHeight,
    ) {
        val activeSurface = surface ?: return@LaunchedEffect
        when (fit) {
            is Fit.Layout -> {
                traceSection("Rive/Layout/ResizeArtboard") {
                    RiveLog.d(GENERAL_TAG) {
                        "Resizing artboard to $surfaceWidth x $surfaceHeight"
                    }
                    artboard.resizeArtboard(activeSurface, fit.scaleFactor)
                }
            }

            else -> {
                traceSection("Rive/Layout/ResetArtboardSize") {
                    RiveLog.d(GENERAL_TAG) { "Resetting artboard size" }
                    artboard.resetArtboardSize()
                }
            }
        }
        // The queued resize only affects layout when the state machine advances again.
        stateMachine.unsettle()
    }

/**
 * Draws one confirmed resource generation while its lifecycle is resumed.
 *
 * @param riveWorker The worker that owns [artboard], [stateMachine], and [surface].
 * @param lifecycleOwner The lifecycle that controls continuous drawing.
 * @param surface The current rendering surface, or null while unavailable.
 * @param artboard The artboard to draw.
 * @param stateMachine The state machine to advance and draw.
 * @param viewModelInstance The bound view model instance, included in effect identity.
 * @param fit The fit used when drawing.
 * @param backgroundColor The color used to clear each frame.
 * @param playing Whether to draw once or advance continuously.
 * @param frameRate The requested drawing rate.
 */
@Composable
private fun DrawRiveFramesEffect(
    riveWorker: RiveWorker,
    lifecycleOwner: LifecycleOwner,
    surface: RiveSurface?,
    artboard: Artboard,
    stateMachine: StateMachine,
    viewModelInstance: ViewModelInstance?,
    fit: Fit,
    backgroundColor: Int,
    playing: Boolean,
    frameRate: RiveFrameRate,
) {
    val artboardHandle = artboard.artboardHandle
    val stateMachineHandle = stateMachine.stateMachineHandle
    LaunchedEffect(
        riveWorker,
        lifecycleOwner,
        surface,
        artboard,
        stateMachine,
        viewModelInstance,
        fit,
        backgroundColor,
        playing,
        frameRate,
    ) {
        val activeSurface = surface ?: run {
            RiveLog.d(DRAW_TAG) { "Surface is null, skipping drawing" }
            return@LaunchedEffect
        }
        if (activeSurface.closed) {
            RiveLog.d(DRAW_TAG) { "Surface is closed, skipping drawing" }
            return@LaunchedEffect
        }
        if (!playing) {
            RiveLog.d(DRAW_TAG) {
                "Playing is false. Advancing by 0, drawing once, and skipping advancement loop."
            }

            traceSection("Rive/Frame") {
                traceSection("Rive/Frame/Advance") {
                    // Advance once to exit the Entry state and apply initial values, including any
                    // pending artboard resize from the fit mode.
                    stateMachine.advance(0.nanoseconds)
                }
                traceSection("Rive/Frame/Draw") {
                    riveWorker.draw(
                        artboardHandle,
                        stateMachineHandle,
                        activeSurface,
                        fit,
                        backgroundColor,
                    )
                }
            }

            return@LaunchedEffect
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            RiveLog.d(DRAW_TAG) {
                "Starting drawing with $artboardHandle and $stateMachineHandle"
            }
            val framePacer = RiveFramePacer(frameRate)
            var lastFrameTimeNs = 0L
            while (isActive) {
                if (stateMachine.settled.value) {
                    traceSection("Rive/Frame/SettledSuspend") {
                        stateMachine.settled.first { !it }
                    }
                    lastFrameTimeNs = 0L
                    framePacer.reset()
                    continue
                }

                val frameDelay = framePacer.delayBeforeNextFrame(System.nanoTime())
                if (frameDelay > ZERO) {
                    delay(frameDelay)
                }
                if (stateMachine.settled.value) {
                    continue
                }

                val frameTimeNs = withFrameNanos { frameTimeNs -> frameTimeNs }

                // Settled events can arrive while withFrameNanos is suspended.
                if (stateMachine.settled.value) {
                    continue
                }
                // FPS cap gate: skip platform frames that arrive before the next Rive frame is due.
                if (!framePacer.tryScheduleFrame(frameTimeNs)) {
                    continue
                }
                // The surface can close while this coroutine is suspended for a frame. The
                // captured instance remains the effect key until cancellation is observed.
                if (activeSurface.closed) {
                    RiveLog.d(DRAW_TAG) { "Surface was released, stopping draw loop" }
                    return@repeatOnLifecycle
                }

                val deltaTime = if (lastFrameTimeNs == 0L) {
                    ZERO
                } else {
                    (frameTimeNs - lastFrameTimeNs).nanoseconds
                }
                lastFrameTimeNs = frameTimeNs

                traceSection("Rive/Frame") {
                    traceSection("Rive/Frame/Advance") {
                        riveWorker.advanceStateMachine(stateMachineHandle, deltaTime)
                    }
                    traceSection("Rive/Frame/Draw") {
                        riveWorker.draw(
                            artboardHandle,
                            stateMachineHandle,
                            activeSurface,
                            fit,
                            backgroundColor,
                        )
                    }
                }
            }
            RiveLog.d(DRAW_TAG) {
                "Ending drawing with $artboardHandle and $stateMachineHandle"
            }
        }
    }
}

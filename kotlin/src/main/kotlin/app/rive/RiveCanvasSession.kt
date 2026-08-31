package app.rive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.view.MotionEvent
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.ColorInt
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.rive.core.CheckableAutoCloseable
import app.rive.core.ChoreographerFrameTicker
import app.rive.core.CloseOnce
import app.rive.core.CommandQueue
import app.rive.core.FrameTicker
import app.rive.core.RenderingDefaults
import app.rive.core.RiveWorker
import app.rive.core.StateMachineHandle
import app.rive.core.traceSection
import app.rive.semantics.AndroidAccessibilityStateProvider
import app.rive.semantics.RiveSemanticsModeController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.nanoseconds

private typealias PointerFn = (StateMachineHandle, Fit, Float, Float, Int, Float, Float) -> Unit

/**
 * Marks APIs that use experimental hardware bitmap rendering.
 *
 * Hardware bitmap rendering is experimental and subject to change. Consumers must opt in explicitly
 * to acknowledge the unstable API before using these declarations.
 */
@RequiresOptIn(
    message = "Hardware bitmap rendering is experimental and subject to change.",
    level = RequiresOptIn.Level.ERROR
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY
)
annotation class ExperimentalHardwareBitmapRendering

/**
 * An entry point for Rive to render to a hardware accelerated [Canvas] using hardware bitmaps.
 *
 * ⚠️ This class must be [closed][close] when you no longer need it to free its resources. Call this
 * only from the main thread.
 *
 * The session manages an advance and render loop after calling [beginPlaying]. While this render
 * loop will produce bitmaps, it is the caller's responsibility to present them on a canvas
 * using [draw]. Collect [frameAvailable] to know when new frames are available after rendering.
 * Use [semantics] to make that loop drain semantic updates after each advance. Observe the
 * resulting tree through the supplied [stateMachine]'s [StateMachine.semanticTree].
 * The session does not install an accessibility node provider or merge Rive nodes into a
 * client-owned provider. The Canvas host is responsible for mapping the tree into its own
 * accessibility hierarchy and for enforcing any modal behavior outside the Rive subtree.
 *
 * Callers must pass touch events to the session with [onTouchEvent] to apply them to the state
 * machine. Coordinates of the events are expected to be in the same space as the destination canvas
 * of [draw], and will be mapped into the render region.
 *
 * All instance methods are expected to be called on Android's main thread. Calling instance methods
 * after [close] throws [RiveResourceClosedException] (except repeated [close], which remains
 * idempotent).
 *
 * Call [setRegion] with the destination rectangle where frames should be presented. The render
 * buffer dimensions are derived from this region's width and height.
 *
 * The main [viewModelInstance] and [globalViewModelInstances] are bound eagerly during
 * initialization. Omitted instances are created from their authored defaults and are not exposed
 * to the caller; create and supply explicit instances when their properties must be read or
 * changed from application code. The configuration is fixed for the lifetime of the session;
 * create a new session to use different bindings.
 *
 * A [stateMachine] should not be shared by multiple active sessions. Each session operates on the
 * same mutable native state machine: advancing it in either session affects both, and binding a
 * different view model instance in one session replaces the binding observed by the other. Use a
 * separately instantiated artboard and state machine for each concurrently active session when
 * their playback or data-binding state must be independent.
 *
 * This session is backed by a [HardwareRenderBuffer]. It owns that buffer's lifecycle and frame
 * loop, but not the supplied Rive resources. These must be created and closed by the caller, and
 * must outlive this session. The session verifies that the supplied resources are open and have
 * compatible ownership and ancestry, but cannot otherwise verify their native initialization.
 *
 * The supplied [riveWorker] must be polled for messages. This is done separately from this session
 * using [RiveWorker.beginPolling][CommandQueue.beginPolling], so that the caller can manage the
 * worker lifecycle and share it across multiple sessions.
 *
 * @param context Android context used to observe platform accessibility state when [semantics] is
 *    [RiveSemanticsMode.Automatic]. The session retains only the application context. Requiring it
 *    explicitly keeps automatic behavior synchronized without relying on process-global state.
 * @param riveWorker The Rive worker that holds the resources to render. See the note above
 *    regarding polling.
 * @param artboard The artboard to render. Must be from the supplied [riveWorker].
 * @param stateMachine The state machine to advance and render. Must be from the supplied
 *    [artboard] and should not be used by another active rendering session.
 * @param viewModelInstance The optional main view model instance to bind to the state machine. A
 *    null value creates a main instance from its authored default, when one is available. It must
 *    be from the supplied [riveWorker]. An instance from another file is supported only when the
 *    state machine uses relative data-binding paths authored in the Rive editor. Cross-file binding
 *    with absolute paths is unsupported.
 * @param fit The [Fit] to use when rendering. This controls how the artboard is scaled to fit the
 *    target surface. Defaults to [RenderingDefaults.defaultFit].
 * @param clearColor The color used to clear the draw region before drawing each frame. Defaults to
 *    [RenderingDefaults.CLEAR_COLOR].
 * @param semantics Controls whether Rive-authored semantics are produced. Defaults to
 *    [RiveSemanticsMode.Off]. Automatic mode follows Android's accessibility-enabled state.
 *    Enabling semantics requires opting into [ExperimentalRiveSemantics].
 * @param globalViewModelInstances Explicit global view model instances keyed by global view model
 *    name. Omitted names create instances from their authored defaults. Implicit instances are not
 *    exposed. The map is snapshotted during construction, and every instance must be from the
 *    supplied [riveWorker]. An instance from another file is supported only when the state machine
 *    uses relative data-binding paths authored in the Rive editor. Cross-file binding with absolute
 *    paths is unsupported. Invalid global names and native binding failures are reported
 *    asynchronously through command queue logging.
 * @param initializationToken Internal token that distinguishes the shared primary constructor from
 *    its stable and experimental public overloads.
 * @throws RiveResourceClosedException If the Rive worker has been disposed or a supplied Rive
 *    resource has been closed.
 * @throws RiveIncompatibleResourceException If the supplied resources do not have compatible
 *    ownership or ancestry.
 * @throws IllegalStateException If hardware rendering is unsupported.
 */
@ExperimentalHardwareBitmapRendering
@RequiresApi(Build.VERSION_CODES.Q)
class RiveCanvasSession private constructor(
    context: Context,
    private val riveWorker: RiveWorker,
    private val artboard: Artboard,
    private val stateMachine: StateMachine,
    private val viewModelInstance: ViewModelInstance?,
    private val fit: Fit,
    @param:ColorInt private val clearColor: Int,
    semantics: RiveSemanticsMode,
    globalViewModelInstances: Map<String, ViewModelInstance>,
    @Suppress("UNUSED_PARAMETER") initializationToken: Unit,
) : CheckableAutoCloseable {
    /**
     * Creates a canvas session using authored defaults for every global view model.
     *
     * @param context Android context used to observe platform accessibility state.
     * @param riveWorker The Rive worker that owns the supplied resources.
     * @param artboard The artboard to render.
     * @param stateMachine The state machine to advance and render.
     * @param viewModelInstance The optional main view model instance to bind.
     * @param fit The fit used when rendering.
     * @param clearColor The color used to clear the draw region before each frame.
     * @param semantics Controls whether Rive-authored semantics are produced.
     * @throws RiveResourceClosedException If the worker or a supplied resource has been closed.
     * @throws RiveIncompatibleResourceException If the resources have incompatible ownership or
     *    ancestry.
     * @throws IllegalStateException If hardware rendering is unsupported.
     */
    @Throws(
        RiveResourceClosedException::class,
        RiveIncompatibleResourceException::class,
        IllegalStateException::class,
    )
    constructor(
        context: Context,
        riveWorker: RiveWorker,
        artboard: Artboard,
        stateMachine: StateMachine,
        viewModelInstance: ViewModelInstance? = null,
        fit: Fit = RenderingDefaults.defaultFit(),
        @ColorInt clearColor: Int = RenderingDefaults.CLEAR_COLOR,
        semantics: RiveSemanticsMode = RiveSemanticsMode.Off,
    ) : this(
        context = context,
        riveWorker = riveWorker,
        artboard = artboard,
        stateMachine = stateMachine,
        viewModelInstance = viewModelInstance,
        fit = fit,
        clearColor = clearColor,
        semantics = semantics,
        globalViewModelInstances = emptyMap(),
        initializationToken = Unit,
    )

    /**
     * Creates a canvas session with explicit global view model bindings.
     *
     * @param context Android context used to observe platform accessibility state.
     * @param riveWorker The Rive worker that owns the supplied resources.
     * @param artboard The artboard to render.
     * @param stateMachine The state machine to advance and render.
     * @param viewModelInstance The optional main view model instance to bind.
     * @param fit The fit used when rendering.
     * @param clearColor The color used to clear the draw region before each frame.
     * @param semantics Controls whether Rive-authored semantics are produced.
     * @param globalViewModelInstances Explicit global view model instances keyed by global view
     *    model name.
     * @throws RiveResourceClosedException If the worker or a supplied resource has been closed.
     * @throws RiveIncompatibleResourceException If the resources have incompatible ownership or
     *    ancestry.
     * @throws IllegalStateException If hardware rendering is unsupported.
     */
    @ExperimentalRiveGlobalViewModels
    @Throws(
        RiveResourceClosedException::class,
        RiveIncompatibleResourceException::class,
        IllegalStateException::class,
    )
    constructor(
        context: Context,
        riveWorker: RiveWorker,
        artboard: Artboard,
        stateMachine: StateMachine,
        viewModelInstance: ViewModelInstance? = null,
        fit: Fit = RenderingDefaults.defaultFit(),
        @ColorInt clearColor: Int = RenderingDefaults.CLEAR_COLOR,
        semantics: RiveSemanticsMode = RiveSemanticsMode.Off,
        globalViewModelInstances: Map<String, ViewModelInstance>,
    ) : this(
        context = context,
        riveWorker = riveWorker,
        artboard = artboard,
        stateMachine = stateMachine,
        viewModelInstance = viewModelInstance,
        fit = fit,
        clearColor = clearColor,
        semantics = semantics,
        globalViewModelInstances = globalViewModelInstances,
        initializationToken = Unit,
    )

    companion object {
        private const val TAG = "Rive/CanvasSession"

        /**
         * @return true if the current device supports hardware bitmaps, which are required for
         *    RiveCanvasSession.
         */
        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
        fun isSupported(): Boolean = HardwareRenderBuffer.isSupported()
    }

    private val globalViewModelInstancesSnapshot = globalViewModelInstances.toMap()
    private val boundViewModelInstances = buildList {
        viewModelInstance?.let(::add)
        addAll(globalViewModelInstancesSnapshot.values)
    }.distinctBy(ViewModelInstance::instanceHandle)

    init {
        riveWorker.checkOpen()
        artboard.checkOpen()
        stateMachine.checkOpen()
        viewModelInstance?.checkOpen()
        globalViewModelInstancesSnapshot.values.forEach(ViewModelInstance::checkOpen)
        artboard.requireOwnedBy(riveWorker)
        stateMachine.requireFromArtboard(artboard)
        viewModelInstance?.requireOwnedBy(riveWorker)
        globalViewModelInstancesSnapshot.values.forEach { instance ->
            instance.requireOwnedBy(riveWorker)
        }
        check(isSupported()) {
            "RiveCanvasSession requires API 29+ hardware bitmap support"
        }
        RiveLog.d(TAG) {
            "Creating RiveCanvasSession with artboard '${artboard.name}'" +
                    " and state machine '${stateMachine.name}'"
        }
    }

    /** Cached paint used to clear the canvas before drawing each frame. */
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = clearColor
    }

    /** Memoized pointer functions to avoid allocating lambdas during pointer event handling. */
    private val pointerDownFn: PointerFn = riveWorker::pointerDown
    private val pointerMoveFn: PointerFn = riveWorker::pointerMove
    private val pointerUpFn: PointerFn = riveWorker::pointerUp
    private val pointerExitFn: PointerFn = riveWorker::pointerExit

    /** Completed when [close] is called, used to stop [beginPlaying]. */
    private val closeSignal = CompletableDeferred<Unit>()
    private val closer = CloseOnce("RiveCanvasSession") {
        try {
            semanticsModeController.close()
            if (semanticsEnabled) {
                stateMachine.clearSemanticFocusForLifecycleCleanup()
            }
        } finally {
            // Session-owned rendering resources must be released even if the caller closed a
            // supplied Rive resource before closing this session.
            closeSignal.complete(Unit)
            renderBufferState.value = null
            renderBuffer.also { renderBuffer = null }?.close()
            latestBitmap = null
            renderRegion.setEmpty()
            isPlaying = false
        }
    }

    /**
     * Stops playback, clears authored semantic focus when enabled, and releases session resources.
     *
     * Semantic focus cleanup is best effort if the supplied state machine or its Rive worker was
     * closed first. Repeated calls are safe and perform no additional work.
     *
     * @throws IllegalStateException If semantics were enabled but the state machine is no longer
     *    registered with its worker.
     */
    @MainThread
    @Throws(IllegalStateException::class)
    override fun close() = closer.close()

    /** Whether this canvas session has been closed. */
    override val closed: Boolean
        get() = closer.closed

    /**
     * The underlying render buffer used to render frames. This is recreated when the render region
     * dimensions change. Always set to hardware mode.
     */
    private var renderBuffer: HardwareRenderBuffer? = null
    private val renderBufferState = MutableStateFlow<HardwareRenderBuffer?>(null)

    /**
     * Most recently published frame from [renderBuffer], cached for synchronous [draw] calls. This
     * is intentionally retained so draw can present the last known frame when no newer frame has
     * arrived yet.
     */
    private var latestBitmap: Bitmap? = null

    private val _frameAvailable = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Emits whenever a newly published render frame becomes visible to this session.
     *
     * Hosts should collect this to invalidate their drawing target, then call [draw].
     */
    val frameAvailable: SharedFlow<Unit> = _frameAvailable

    /** Render region in the destination canvas. Updated via [setRegion]. */
    private val renderRegion = Rect()

    /**
     * Whether the advance and render loop is currently running. Used to prevent concurrent loops
     * from multiple beginPlaying calls.
     */
    private var isPlaying = false

    /** Whether session-managed advances should be followed by a semantic diff drain. */
    private var semanticsEnabled = false

    init {
        stateMachine.bindViewModels(viewModelInstance, globalViewModelInstancesSnapshot)
    }

    private val semanticsModeController = RiveSemanticsModeController(
        initialMode = semantics,
        provider = AndroidAccessibilityStateProvider(context.applicationContext),
        onEnabledChanged = ::setSemanticsEnabled,
    )

    /**
     * Controls whether this session produces and drains Rive-authored semantics.
     *
     * [RiveSemanticsMode.Automatic] follows Android accessibility state through the [Context]
     * supplied to the constructor. Disabling stops session-managed drains and clears authored
     * semantic focus. Rive core does not currently disable semantic tracking after it has first
     * been enabled. While enabled, every session-managed advance is followed by a drain using the
     * current render-buffer dimensions. Observe applied changes through [StateMachine.semanticTree]
     * on the supplied state machine.
     *
     * Published bounds are physical pixels local to the render region. A host exposing those
     * bounds in a larger view must add the render region's left and top offset. A valid render
     * region is required before [beginPlaying] can advance or drain.
     *
     * @throws RiveResourceClosedException If this session, state machine, or Rive worker has been
     *    closed.
     * @throws IllegalStateException If disabling attempts to clear a state machine that is no
     *    longer registered with its worker.
     */
    @ExperimentalRiveSemantics
    @set:MainThread
    var semantics: RiveSemanticsMode
        get() = semanticsModeController.mode
        @Throws(RiveResourceClosedException::class, IllegalStateException::class)
        set(value) {
            closer.checkOpen()
            semanticsModeController.mode = value
        }

    /** Updates session behavior after the configured semantics mode resolves. */
    private fun setSemanticsEnabled(enabled: Boolean) {
        if (semanticsEnabled == enabled) {
            return
        }
        if (enabled) {
            stateMachine.enableSemantics()
        } else {
            stateMachine.clearSemanticFocusForLifecycleCleanup()
        }
        semanticsEnabled = enabled
    }

    /**
     * Update the target draw region for rendering. This should be called at least once to
     * initialize the session, and whenever the region changes.
     *
     * If the region's width or height changed, the underlying hardware render buffer is recreated.
     * This is an expensive operation and should only be done when necessary.
     *
     * A region with width or height of 0 is treated as a "not renderable" state: the current render
     * buffer is released and subsequent frames are skipped until a non-zero region is set.
     *
     * For [Fit.Layout], size changes also update artboard sizing immediately. To observe the new
     * sizing in rendered output, either [beginPlaying] must be actively running, or the caller
     * must advance the supplied [stateMachine] manually (e.g. [StateMachine.advance] with 0 delta)
     * before drawing.
     *
     * If only the region position changed, drawing and pointer mapping update without recreation.
     *
     * @throws RiveResourceClosedException If this session or a supplied Rive resource has been
     *    closed, or if the owning Rive worker has been disposed.
     * @throws IllegalStateException If the state machine is no longer registered with its worker.
     * @throws IllegalArgumentException If the region has negative width or height.
     * @throws RiveRenderException If a replacement hardware render surface cannot be created.
     */
    @MainThread
    @Throws(
        RiveResourceClosedException::class,
        IllegalStateException::class,
        IllegalArgumentException::class,
        RiveRenderException::class
    )
    fun setRegion(region: Rect) {
        closer.checkOpen()
        require(region.width() >= 0 && region.height() >= 0) {
            "Region must have non-negative dimensions: $region"
        }
        val width = region.width()
        val height = region.height()
        if (width == 0 || height == 0) {
            if (renderRegion == region && renderBuffer == null && latestBitmap == null) {
                return
            }
            RiveLog.v(TAG) {
                "Render region has a 0 dimension: $region; clearing render state"
            }
            renderRegion.set(region)
            renderBufferState.value = null
            renderBuffer.also { renderBuffer = null }?.close()
            latestBitmap = null
            stateMachine.unsettle()
            return
        }
        if (renderRegion == region) {
            return
        }

        val dimensionsChanged =
            renderRegion.width() != width || renderRegion.height() != height
        if (dimensionsChanged) {
            RiveLog.i(TAG) {
                "Updating render region to $region; recreating render buffer and unsettling state machine"
            }
        }
        val oldBuffer = renderBuffer
        val newBuffer = if (dimensionsChanged) {
            HardwareRenderBuffer(width, height, riveWorker)
        } else {
            oldBuffer
        }
        renderBuffer = newBuffer
        renderBufferState.value = newBuffer
        renderRegion.set(region)
        if (dimensionsChanged) {
            if (fit is Fit.Layout) {
                traceSection("Rive/Layout/ResizeArtboard") {
                    newBuffer?.let { activeBuffer ->
                        artboard.resizeArtboard(activeBuffer.surface, fit.scaleFactor)
                    }
                }
            }
            // Unsettle the state machine so that the next frame will advance and render with the
            // new sizing.
            stateMachine.unsettle()
            latestBitmap = null
        }

        if (oldBuffer != null && oldBuffer !== newBuffer) {
            oldBuffer.close()
        }
    }

    /**
     * Runs the advance and render loop while [lifecycle] is RESUMED.
     *
     * The caller is expected to launch this from their own coroutine scope. It blocks the calling
     * coroutine with a ticker loop, so it should typically be launched in a separate scope from the
     * caller's main work.
     *
     * If the session is closed while this is running, the loop will exit, the function will return,
     * and the session will stop advancing and rendering frames.
     *
     * Ensure [setRegion] has been called with a valid region before calling this. Without a valid
     * region, the state machine will not be advanced and no frames will be rendered.
     *
     * @throws RiveResourceClosedException If this session or a Rive resource used while rendering
     *    has been closed, or if the owning Rive worker has been disposed.
     * @throws IllegalStateException If this session is already playing or the state machine is no
     *    longer registered with its worker.
     * @throws RiveRenderException If hardware first-frame publication times out or image
     *    acquisition fails.
     * @throws CancellationException If the coroutine is cancelled while playing.
     */
    @MainThread
    @Throws(
        RiveResourceClosedException::class,
        IllegalStateException::class,
        RiveRenderException::class,
        CancellationException::class
    )
    suspend fun beginPlaying(
        lifecycle: Lifecycle,
        ticker: FrameTicker = ChoreographerFrameTicker
    ) {
        closer.checkOpen()
        check(!isPlaying) {
            "beginPlaying() is already running for this RiveCanvasSession"
        }
        isPlaying = true
        stateMachine.unsettle()

        try {
            /* Host for a number of jobs:
             * - viewModelDirtyCollectors: Observe the bound view model instances' dirty flows
             * - frameAvailableCollector: Updates latest session bitmap from buffer publications
             * - renderLoop: Runs the advance and render loop while lifecycle is RESUMED
             * - closeWatcher: Cancel the render loop when the session is closed
             */
            coroutineScope {
                // Observe every explicitly bound instance so either main or global data changes
                // can wake a state machine that previously settled.
                val viewModelDirtyCollectors = boundViewModelInstances.map { instance ->
                    launch {
                        instance.dirtyFlow.collect {
                            RiveLog.v(TAG) {
                                "View model instance ${instance.instanceHandle} dirty, " +
                                        "unsettling ${stateMachine.stateMachineHandle}"
                            }
                            stateMachine.unsettle()
                        }
                    }
                }

                val frameAvailableCollector = launch {
                    renderBufferState
                        .collectLatest { activeBuffer ->
                            activeBuffer ?: return@collectLatest
                            activeBuffer.frameAvailable.collect {
                                traceSection("$TAG/PublishLatestBitmap") {
                                    /* Hardware publication is async, so consume the latest buffer
                                     * snapshot when signaled rather than assuming render->read
                                     * synchrony. */
                                    val bitmap = traceSection("$TAG/ToBitmap") {
                                        activeBuffer.consumeLatestBitmap()
                                    }
                                    if (bitmap != null && latestBitmap !== bitmap) {
                                        latestBitmap = bitmap
                                        _frameAvailable.tryEmit(Unit)
                                    }
                                }
                            }
                        }
                }

                // Advance and render loop that runs every frame while the lifecycle is RESUMED.
                val renderLoop = launch {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        RiveLog.d(TAG) {
                            "Starting drawing with ${artboard.artboardHandle} and " +
                                    "${stateMachine.stateMachineHandle}"
                        }
                        var lastFrameTimeNs = 0L
                        var loggedNoBuffer = false

                        while (isActive && !closer.closed) {
                            ticker.withFrame { frameTimeNs ->
                                if (closer.closed) {
                                    return@withFrame
                                }

                                val deltaNs = if (lastFrameTimeNs == 0L) {
                                    0L
                                } else {
                                    frameTimeNs - lastFrameTimeNs
                                }
                                lastFrameTimeNs = frameTimeNs

                                traceSection("Rive/Frame") {
                                    val activeBuffer = renderBuffer
                                    if (activeBuffer == null) {
                                        if (!loggedNoBuffer) {
                                            RiveLog.w(TAG) {
                                                "No render buffer available; call setRegion(...) " +
                                                        "with a valid size before beginPlaying."
                                            }
                                            loggedNoBuffer = true
                                        }
                                        traceSection("Rive/Frame/NoBuffer") { Unit }
                                        return@traceSection
                                    }
                                    loggedNoBuffer = false

                                    if (!stateMachine.settled.value) {
                                        traceSection("Rive/Frame/Advance") {
                                            stateMachine.advance(deltaNs.nanoseconds)
                                        }
                                        if (semanticsEnabled) {
                                            traceSection("Rive/Frame/DrainSemantics") {
                                                stateMachine.drainSemanticsDiff(
                                                    fit = fit,
                                                    surfaceWidth = activeBuffer.width.toFloat(),
                                                    surfaceHeight = activeBuffer.height.toFloat(),
                                                )
                                            }
                                        }
                                        traceSection("Rive/Frame/Draw") {
                                            // Dispatch an async render to the active buffer.
                                            activeBuffer.render(
                                                artboard = artboard,
                                                stateMachine = stateMachine,
                                                fit = fit,
                                                clearColor = clearColor
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        RiveLog.d(TAG) {
                            "Ending drawing with ${artboard.artboardHandle} and " +
                                    "${stateMachine.stateMachineHandle}"
                        }
                    }
                }

                // If close is called, cancel the render loop, triggering the below join
                val closeWatcher = launch {
                    closeSignal.await()
                    renderLoop.cancel()
                }

                /* Suspend the outer coroutineScope on the render loop. Joins when:
                 * - The lifecycle is DESTROYED
                 * - close() cancels the job
                 * - The parent scope's job is canceled
                 * - An exception is thrown in the render loop
                 */
                try {
                    renderLoop.join()
                } finally {
                    closeWatcher.cancelAndJoin()
                    frameAvailableCollector.cancelAndJoin()
                    viewModelDirtyCollectors.forEach { collector ->
                        collector.cancelAndJoin()
                    }
                }
            }
        } finally {
            isPlaying = false
        }
    }

    /**
     * Draws the latest rendered bitmap into the configured draw region of the canvas, if available.
     *
     * If no frame is available yet, only the draw-region clear color is painted.
     *
     * ⚠️ The supplied canvas must be hardware accelerated.
     *
     * @throws RiveResourceClosedException If this session has been closed.
     * @throws IllegalArgumentException If the destination canvas is not hardware-accelerated.
     */
    @MainThread
    @Throws(RiveResourceClosedException::class, IllegalArgumentException::class)
    fun draw(canvas: Canvas) {
        closer.checkOpen()
        require(canvas.isHardwareAccelerated) {
            "RiveCanvasSession requires a hardware-accelerated canvas to draw hardware bitmaps"
        }

        if (renderRegion.isEmpty) {
            return
        }

        canvas.drawRect(renderRegion, clearPaint)
        if (latestBitmap == null) {
            return
        }

        traceSection("Rive/Frame/Present/DrawBitmap") {
            latestBitmap?.let { bitmap ->
                canvas.drawBitmap(bitmap, null, renderRegion, null)
            }
        }
    }

    /**
     * Forwards touch events to the state machine pointer APIs.
     *
     * Coordinates are interpreted in the same canvas space as [draw], then mapped into the
     * configured draw region set with [setRegion] before forwarding to Rive.
     *
     * @return true when this session handled the event type.
     * @throws RiveResourceClosedException If this session has been closed or the owning Rive worker
     *    has been disposed.
     */
    @MainThread
    @Throws(RiveResourceClosedException::class)
    fun onTouchEvent(event: MotionEvent): Boolean {
        closer.checkOpen()

        if (renderRegion.isEmpty) {
            return false
        }

        val surfaceWidth = renderRegion.width().toFloat()
        val surfaceHeight = renderRegion.height().toFloat()

        fun containsInRegion(x: Float, y: Float): Boolean =
            x >= renderRegion.left && x < renderRegion.right &&
                    y >= renderRegion.top && y < renderRegion.bottom

        fun dispatchPointer(index: Int, pointerFn: PointerFn) {
            val xInRegion = event.getX(index) - renderRegion.left
            val yInRegion = event.getY(index) - renderRegion.top
            val pointerId = event.getPointerId(index)
            pointerFn(
                stateMachine.stateMachineHandle,
                fit,
                surfaceWidth,
                surfaceHeight,
                pointerId,
                xInRegion,
                yInRegion
            )
        }

        val handled = traceSection("Rive/PointerInput") {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN -> {
                    val actionX = event.getX(event.actionIndex)
                    val actionY = event.getY(event.actionIndex)
                    if (!containsInRegion(actionX, actionY)) {
                        return@traceSection false
                    }
                    dispatchPointer(event.actionIndex, pointerDownFn)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    repeat(event.pointerCount) { index ->
                        val pointerFn =
                            if (containsInRegion(event.getX(index), event.getY(index))) {
                                pointerMoveFn
                            } else {
                                pointerExitFn
                            }
                        dispatchPointer(index, pointerFn)
                    }
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP -> {
                    dispatchPointer(event.actionIndex, pointerUpFn)
                    dispatchPointer(event.actionIndex, pointerExitFn)
                    true
                }

                MotionEvent.ACTION_CANCEL,
                MotionEvent.ACTION_OUTSIDE -> {
                    repeat(event.pointerCount) { index ->
                        dispatchPointer(index, pointerExitFn)
                    }
                    true
                }

                else -> false
            }
        }

        if (handled) {
            stateMachine.unsettle()
        }

        return handled
    }
}

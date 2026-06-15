package app.rive

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
import app.rive.core.ChoreographerFrameTicker
import app.rive.core.CloseOnce
import app.rive.core.CommandQueue
import app.rive.core.FrameTicker
import app.rive.core.RenderingDefaults
import app.rive.core.RiveWorker
import app.rive.core.StateMachineHandle
import app.rive.core.traceSection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
 *
 * Callers must pass touch events to the session with [onTouchEvent] to apply them to the state
 * machine. Coordinates of the events are expected to be in the same space as the destination canvas
 * of [draw], and will be mapped into the render region.
 *
 * All instance methods are expected to be called on Android's main thread. Calling instance methods
 * after [close] throws [IllegalStateException] (except repeated [close], which remains idempotent).
 *
 * Call [setRegion] with the destination rectangle where frames should be presented. The render
 * buffer dimensions are derived from this region's width and height.
 *
 * If [viewModelInstance] is supplied, this session binds it eagerly during initialization.
 *
 * This session is backed by a [HardwareRenderBuffer]. It owns that buffer's lifecycle and frame
 * loop, but not the supplied Rive resources. These must be created and closed by the caller, and
 * must outlive this session. The session will check that the supplied resources are from the
 * same [RiveWorker] instance, but does not check that they are valid or properly initialized.
 *
 * The supplied [riveWorker] must be polled for messages. This is done separately from this session
 * using [RiveWorker.beginPolling][CommandQueue.beginPolling], so that the caller can manage the
 * worker lifecycle and share it across multiple sessions.
 *
 * @param riveWorker The Rive worker that holds the resources to render. See the note above
 *    regarding polling.
 * @param artboard The artboard to render. Must be from the supplied [riveWorker].
 * @param stateMachine The state machine to advance and render. Must be from the supplied
 *    [artboard].
 * @param viewModelInstance An optional view model instance to bind to the state machine. Must be
 *    from the supplied [riveWorker].
 * @param fit The [Fit] to use when rendering. This controls how the artboard is scaled to fit the
 *    target surface. Defaults to [RenderingDefaults.defaultFit].
 * @param clearColor The color used to clear the draw region before drawing each frame. Defaults to
 *    [RenderingDefaults.CLEAR_COLOR].
 */
@ExperimentalHardwareBitmapRendering
@RequiresApi(Build.VERSION_CODES.Q)
class RiveCanvasSession(
    private val riveWorker: RiveWorker,
    private val artboard: Artboard,
    private val stateMachine: StateMachine,
    private val viewModelInstance: ViewModelInstance? = null,
    private val fit: Fit = RenderingDefaults.defaultFit(),
    @param:ColorInt private val clearColor: Int = RenderingDefaults.CLEAR_COLOR,
) : AutoCloseable {
    companion object {
        private const val TAG = "Rive/CanvasSession"

        /**
         * @return true if the current device supports hardware bitmaps, which are required for
         *    RiveCanvasSession.
         */
        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
        fun isSupported(): Boolean = HardwareRenderBuffer.isSupported()
    }

    init {
        check(isSupported()) {
            "RiveCanvasSession requires API 29+ hardware bitmap support"
        }
        require(artboard.isOwnedBy(riveWorker)) {
            "RiveCanvasSession artboard must use the same RiveWorker"
        }
        require(stateMachine.isOwnedBy(riveWorker)) {
            "RiveCanvasSession state machine must use the same RiveWorker"
        }
        require(stateMachine.isFromArtboard(artboard)) {
            "RiveCanvasSession state machine must be created from the supplied artboard"
        }
        require(viewModelInstance?.isOwnedBy(riveWorker) != false) {
            "RiveCanvasSession view model instance must use the same RiveWorker"
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
        closeSignal.complete(Unit)
        renderBufferState.value = null
        renderBuffer.also { renderBuffer = null }?.close()
        latestBitmap = null
        renderRegion.setEmpty()
        settled = false
        isPlaying = false
    }

    @MainThread
    override fun close() = closer.close()

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
     * Whether the state machine is settled, set from the worker's settled flow. When true, the
     * advance and render steps are skipped.
     */
    private var settled = false

    /**
     * Whether the advance and render loop is currently running. Used to prevent concurrent loops
     * from multiple beginPlaying calls.
     */
    private var isPlaying = false

    init {
        viewModelInstance?.let { instance ->
            RiveLog.d(TAG) { "Binding view model instance ${instance.instanceHandle}" }
            riveWorker.bindViewModelInstance(
                stateMachine.stateMachineHandle,
                instance.instanceHandle
            )
        }
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
     * @throws IllegalStateException If this session has been closed.
     * @throws IllegalArgumentException If the region has negative width or height.
     */
    @MainThread
    @Throws(IllegalStateException::class, IllegalArgumentException::class)
    fun setRegion(region: Rect) {
        check(!closer.closed) { "RiveCanvasSession is closed" }
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
            settled = false
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
            settled = false
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
     * @throws IllegalStateException If this session has been closed at the time of calling or is
     *    already playing, or if its active render surface is closed while submitting render work.
     * @throws RiveRenderException If hardware first-frame publication times out or image
     *    acquisition fails.
     */
    @MainThread
    @Throws(IllegalStateException::class, RiveRenderException::class)
    suspend fun beginPlaying(
        lifecycle: Lifecycle,
        ticker: FrameTicker = ChoreographerFrameTicker
    ) {
        check(!closer.closed) { "RiveCanvasSession is closed" }
        check(!isPlaying) {
            "beginPlaying() is already running for this RiveCanvasSession"
        }
        isPlaying = true

        try {
            /* Host for a number of jobs:
             * - settledCollector: Observes the worker's settled flow
             * - viewModelDirtyCollector: Observes the view model instance's dirty flow
             * - frameAvailableCollector: Updates latest session bitmap from buffer publications
             * - renderLoop: Runs the advance and render loop while lifecycle is RESUMED
             * - closeWatcher: Cancel the render loop when the session is closed
             */
            coroutineScope {
                // Observe the worker's settled flow to track when the state machine is settled.
                // This is used to skip advance and render when the state machine is idle.
                val settledCollector = launch {
                    riveWorker.settledFlow
                        .filter { it.handle == stateMachine.stateMachineHandle.handle }
                        .collect {
                            RiveLog.v(TAG) {
                                "State machine ${stateMachine.stateMachineHandle} is settled"
                            }
                            settled = true
                        }
                }

                // If a view model instance is bound, also observe its dirty flow to know when to
                // un-settle the state machine when the view model instance is updated.
                val viewModelDirtyCollector = if (viewModelInstance != null) {
                    launch {
                        viewModelInstance.dirtyFlow.collect {
                            RiveLog.v(TAG) {
                                "View model instance dirty, unsettling ${stateMachine.stateMachineHandle}"
                            }
                            settled = false
                        }
                    }
                } else {
                    null
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

                                    if (!settled) {
                                        traceSection("Rive/Frame/Advance") {
                                            stateMachine.advance(deltaNs.nanoseconds)
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
                    settledCollector.cancelAndJoin()
                    viewModelDirtyCollector?.cancelAndJoin()
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
     * @throws IllegalStateException If this session has been closed.
     * @throws IllegalArgumentException If the destination canvas is not hardware-accelerated.
     */
    @MainThread
    @Throws(IllegalStateException::class, IllegalArgumentException::class)
    fun draw(canvas: Canvas) {
        check(!closer.closed) { "RiveCanvasSession is closed" }
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
     * @throws IllegalStateException If this session has been closed.
     */
    @MainThread
    @Throws(IllegalStateException::class)
    fun onTouchEvent(event: MotionEvent): Boolean {
        check(!closer.closed) { "RiveCanvasSession is closed" }

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
            settled = false
        }

        return handled
    }
}

package app.rive.runtime.kotlin

import android.annotation.TargetApi
import android.content.Context
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.*
import androidx.annotation.RawRes
import androidx.annotation.VisibleForTesting
import app.rive.runtime.kotlin.ResourceType.Companion.makeMaybeResource
import app.rive.runtime.kotlin.controllers.ControllerState
import app.rive.runtime.kotlin.controllers.ControllerStateManagement
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.*
import app.rive.runtime.kotlin.core.errors.RiveException
import app.rive.runtime.kotlin.renderers.RendererMetrics
import app.rive.runtime.kotlin.renderers.RendererSkia
import com.android.volley.NetworkResponse
import com.android.volley.ParseError
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.Volley
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.util.*
import kotlin.math.min


/**
 * This view aims to provide the most straightforward way to get rive animations into your application.
 *
 * Simply add the view to your activities, and you are be good to to!
 *
 * Very simple animations can be configured completely from a layout file. We also also expose a
 * thin api layer to allow more control over how animations are playing.
 *
 * All of this is built upon the core [rive animation wrappers][app.rive.runtime.kotlin.core],
 * which are designed to expose our c++ rive animation runtimes directly, and can be used
 * directly for the most flexibility.
 *
 *
 * Xml [attrs] can be used to set initial values for many
 * - Provide the [resource][R.styleable.RiveAnimationView_riveResource] to load as a rive file, this can be done later with [setRiveResource], [setRiveBytes], or [setRiveFile].
 * - Alternatively, provide the [url][R.styleable.RiveAnimationView_riveUrl] to load as a rive file over HTTP.
 * - Determine the [artboard][R.styleable.RiveAnimationView_riveArtboard] to use, this defaults to the first artboard in the file.
 * - Enable or disable [autoplay][R.styleable.RiveAnimationView_riveAutoPlay] to start the animation as soon as its available, or leave it to false to control its playback later. defaults to enabled.
 * - Configure [alignment][R.styleable.RiveAnimationView_riveAlignment] to specify how the animation should be aligned to its container.
 * - Configure [fit][R.styleable.RiveAnimationView_riveFit] to specify how and if the animation should be resized to fit its container.
 * - Configure [loop mode][R.styleable.RiveAnimationView_riveLoop] to configure if animations should loop, play once, or ping-pong back and forth. Defaults to the setup in the rive file.
 */
open class RiveAnimationView(context: Context, attrs: AttributeSet? = null) :
    RiveTextureView(context, attrs),
    Observable<RiveFileController.Listener> {

    companion object {
        // Static Tag for Logging.
        const val TAG = "RiveAnimationView"

        // Default attribute values.
        const val alignmentIndexDefault = 4
        const val fitIndexDefault = 1
        const val loopIndexDefault = 3
    }

    open val defaultAutoplay = true

    var controller: RiveFileController

    val artboardRenderer: RiveArtboardRenderer?
        get() {
            if (renderer is RiveArtboardRenderer?) {
                return renderer as RiveArtboardRenderer?
            }

            throw TypeCastException(
                "Expected RiveArtboardRenderer but got ${
                    renderer?.javaClass?.simpleName ?: "NULL"
                }"
            )
        }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val rendererAttributes: RendererAttributes

    var fit: Fit
        get() = controller.fit
        set(value) {
            // Not replacing with controller b/c it's currently calling `start()`
            artboardRenderer?.fit = value
        }

    var alignment: Alignment
        get() = controller.alignment
        set(value) {
            // Not replacing with controller b/c it's currently calling `start()`
            artboardRenderer?.alignment = value
        }

    /**
     * Getter for the loaded [Rive file][File].
     */
    val file: File?
        get() = controller.file

    /**
     * Helper for determining performance metrics.
     */
    private var frameMetricsListener: Window.OnFrameMetricsAvailableListener? = null
    private val bounds = RectF()

    /**
     * Getter/Setter for the currently loaded artboard Name
     * Setting a new name, will load the new artboard & depending on [autoplay] play them
     */
    var artboardName: String?
        get() = artboardRenderer!!.artboardName
        set(name) {
            artboardRenderer?.setArtboardByName(name)
        }

    /**
     * Getter/Setter for [autoplay].
     */
    var autoplay: Boolean
        get() = controller.autoplay
        set(value) {
            controller.autoplay = value
        }

    /**
     * Get the currently loaded [animation instances][LinearAnimationInstance].
     */
    val animations: List<LinearAnimationInstance>
        get() = controller.animations

    /**
     * Get the currently loaded [state machine instances][StateMachineInstance].
     */
    val stateMachines: List<StateMachineInstance>
        get() = controller.stateMachines

    /**
     * Get the currently playing [animation instances][LinearAnimationInstance].
     */
    val playingAnimations: HashSet<LinearAnimationInstance>
        get() = controller.playingAnimations

    /**
     * Get the currently playing [state machine instances][StateMachineInstance].
     */
    val playingStateMachines: HashSet<StateMachineInstance>
        get() = controller.playingStateMachines

    /**
     * Tracks the renderer attributes that need to be applied when this [View] within its lifecycle.
     */
    class RendererAttributes(
        alignmentIndex: Int = alignmentIndexDefault,
        fitIndex: Int = fitIndexDefault,
        loopIndex: Int = loopIndexDefault,
        var autoplay: Boolean,
        val riveTraceAnimations: Boolean = false,
        var artboardName: String?,
        var animationName: String?,
        var stateMachineName: String?,
        var resource: ResourceType?,
    ) {
        var alignment: Alignment = Alignment.fromIndex(alignmentIndex)
        var fit: Fit = Fit.fromIndex(fitIndex)
        var loop: Loop = Loop.fromIndex(loopIndex)
    }

    init {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.RiveAnimationView,
            0, 0
        ).apply {
            try {
                val resId = getResourceId(R.styleable.RiveAnimationView_riveResource, -1)
                val resUrl = getString(R.styleable.RiveAnimationView_riveUrl)
                // Give priority to loading a local resource.
                val resourceFromValue = makeMaybeResource(
                    if (resId == -1) resUrl else resId
                )

                rendererAttributes = RendererAttributes(
                    alignmentIndex = getInteger(R.styleable.RiveAnimationView_riveAlignment, 4),
                    fitIndex = getInteger(R.styleable.RiveAnimationView_riveFit, 1),
                    loopIndex = getInteger(R.styleable.RiveAnimationView_riveLoop, 3),
                    autoplay =
                    getBoolean(R.styleable.RiveAnimationView_riveAutoPlay, defaultAutoplay),
                    riveTraceAnimations =
                    getBoolean(R.styleable.RiveAnimationView_riveTraceAnimations, false),
                    artboardName = getString(R.styleable.RiveAnimationView_riveArtboard),
                    animationName = getString(R.styleable.RiveAnimationView_riveAnimation),
                    stateMachineName = getString(R.styleable.RiveAnimationView_riveStateMachine),
                    resource = resourceFromValue,
                )

                controller = RiveFileController(
                    loop = rendererAttributes.loop,
                    autoplay = rendererAttributes.autoplay,
                )

                /** We can't initialize the File here just yet, because if the View doesn't get
                 * allocated (i.e. onAttachedToWindow never gets called), the the File won't be
                 * de-allocated.
                 */
            } finally {
                recycle()
            }
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        super.onSurfaceTextureSizeChanged(surface, width, height)
        artboardRenderer!!.targetBounds = RectF(0.0f, 0.0f, width.toFloat(), height.toFloat())
    }

    override fun onSurfaceTextureAvailable(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int
    ) {
        super.onSurfaceTextureAvailable(surfaceTexture, width, height)
        artboardRenderer!!.targetBounds = RectF(0.0f, 0.0f, width.toFloat(), height.toFloat())
    }

    private fun loadFileFromResource(onComplete: (File) -> Unit) {
        when (val resource = rendererAttributes.resource) {
            null -> Log.w(TAG, "loadResource: no resource to load")
            // Passes the resource through: ownership is coming from elsewhere.
            is ResourceType.ResourceRiveFile -> onComplete(resource.file)
            // loadFromNetwork() releases after onComplete() is called.
            is ResourceType.ResourceUrl -> loadFromNetwork(resource.url, onComplete)
            is ResourceType.ResourceBytes -> {
                val file = File(resource.bytes)
                onComplete(file)
                // Don't retain the handle.
                file.release()
            }

            is ResourceType.ResourceId -> resources.openRawResource(resource.id).use {
                val file = File(it.readBytes())
                onComplete(file)
                // Don't retain the handle.
                file.release()
            }
        }
    }

    @Deprecated(
        "Use loadFromNetwork to avoid memory leaks.",
        replaceWith = ReplaceWith("loadFromNetwork(url) { /* onComplete() */}")
    )
    private fun loadHttp(url: String) {
        val queue = Volley.newRequestQueue(context)
        val stringRequest = RiveFileRequest(
            url,
            { file -> artboardRenderer?.setRiveFile(file) },
            { throw IOException("Unable to download Rive file $url") }
        )
        queue.add(stringRequest)
    }

    private fun loadFromNetwork(url: String, onComplete: (File) -> Unit) {
        val queue = Volley.newRequestQueue(context)
        val stringRequest = RiveFileRequest(
            url,
            {
                onComplete(it)
                it.release()
            },
            { throw IOException("Unable to download Rive file $url") }
        )
        queue.add(stringRequest)
    }

    /**
     * Pauses all playing [animation instance][LinearAnimationInstance].
     */
    fun pause() {
        artboardRenderer?.stop()
        controller.pause()
        stopFrameMetrics()
    }

    /**
     * Pauses any [animation instances][LinearAnimationInstance] for [animations][Animation] with
     * any of the provided [names][animationNames].
     *
     * Advanced: Multiple [animation instances][LinearAnimationInstance] can running the same
     * [animation][Animation]
     */
    fun pause(animationNames: List<String>, areStateMachines: Boolean = false) {
        controller.pause(animationNames, areStateMachines)
    }


    /**
     * Pauses any [animation instances][LinearAnimationInstance] for an [animation][Animation]
     * called [animationName].
     *
     * Advanced: Multiple [animation instances][LinearAnimationInstance] can running the same
     * [animation][Animation]
     */
    fun pause(animationName: String, isStateMachine: Boolean = false) {
        controller.pause(animationName, isStateMachine)
    }

    /**
     * Stops all [animation instances][LinearAnimationInstance].
     *
     * Animations Instances will be disposed of completely.
     * Subsequent plays will create new [animation instances][LinearAnimationInstance]
     * for the [animations][Animation] in the file.
     */
    fun stop() {
        controller.stopAnimations()
        stopFrameMetrics()
    }

    /**
     * Stops any [animation instances][LinearAnimationInstance] for [animations][Animation] with
     * any of the provided [names][animationNames].
     *
     * Animations Instances will be disposed of completely.
     * Subsequent plays will create new [animation instances][LinearAnimationInstance]
     * for the [animations][Animation] in the file.
     *
     * Advanced: Multiple [animation instances][LinearAnimationInstance] can running the same
     * [animation][Animation]
     */
    fun stop(animationNames: List<String>, areStateMachines: Boolean = false) {
        controller.stopAnimations(animationNames, areStateMachines)
    }

    /**
     * Stops any [animation instances][LinearAnimationInstance] for an [animation][Animation]
     * called [animationName].
     *
     * Animations Instances will be disposed of completely.
     * Subsequent plays will create new [animation instances][LinearAnimationInstance]
     * for the [animations][Animation] in the file.
     *
     * Advanced: Multiple [animation instances][LinearAnimationInstance] can running the same
     * [animation][Animation]
     */
    fun stop(animationName: String, isStateMachine: Boolean = false) {
        controller.stopAnimations(animationName, isStateMachine)
    }

    /**
     * Plays the first [animations][Animation] found for a [File].
     *
     * @experimental Optionally provide a [loop mode][Loop] to overwrite the animations configured loop mode.
     * Already playing animation instances will be updated to this loop mode if provided.
     *
     * @experimental Optionally provide a [direction][Direction] to set the direction an animation is playing in.
     * Already playing animation instances will be updated to this direction immediately.
     * Backwards animations will start from the end.
     *
     * @experimental Optionally provide a [settleInitialState][Boolean] to inform the state machine to settle its
     * state on initialization by determining its starting state based of the initial input values.
     *
     * For [animations][Animation] without an [animation instance][LinearAnimationInstance] one will be created and played.
     */
    fun play(
        loop: Loop = Loop.AUTO,
        direction: Direction = Direction.AUTO,
        settleInitialState: Boolean = true
    ) {
        rendererAttributes.apply {
            this.loop = loop
        }
        controller.play(loop, direction, settleInitialState)
    }

    /**
     * Plays any [animation instances][LinearAnimationInstance] for [animations][Animation] with
     * any of the provided [names][animationNames].
     *
     * see [play] for more details on options
     */
    fun play(
        animationNames: List<String>,
        loop: Loop = Loop.AUTO,
        direction: Direction = Direction.AUTO,
        areStateMachines: Boolean = false,
        settleInitialState: Boolean = true
    ) {
        rendererAttributes.apply {
            this.loop = loop
        }
        controller.play(
            animationNames,
            loop,
            direction,
            areStateMachines,
            settleInitialState
        )
    }

    /**
     * Plays any [animation instances][LinearAnimationInstance] for an [animation][Animation]
     * called [animationName].
     *
     * see [play] for more details on options
     */
    fun play(
        animationName: String,
        loop: Loop = Loop.AUTO,
        direction: Direction = Direction.AUTO,
        isStateMachine: Boolean = false,
        settleInitialState: Boolean = true
    ) {
        rendererAttributes.apply {
            this.animationName = if (isStateMachine) null else animationName
            this.stateMachineName = if (isStateMachine) animationName else null
            this.loop = loop
        }
        controller.play(
            animationName,
            loop,
            direction,
            isStateMachine,
            settleInitialState
        )
    }

    /**
     * Reset the view by resetting the current artboard, before any animations have been applied
     *
     * Note: this will respect [autoplay]
     */
    fun reset() {
        controller.reset()
        artboardRenderer?.reset()
    }

    /**
     * Fire the [SMITrigger] input called [inputName] on all active matching state machines
     */
    fun fireState(stateMachineName: String, inputName: String) {
        controller.fireState(stateMachineName, inputName)
    }

    /**
     * Update the state of the [SMIBoolean] input called [inputName] on all active matching state machines
     * to [value]
     */
    fun setBooleanState(stateMachineName: String, inputName: String, value: Boolean) {
        controller.setBooleanState(stateMachineName, inputName, value)
    }

    /**
     * Update the state of the [SMINumber] input called [inputName] on all active matching state machines
     * to [value]
     */
    fun setNumberState(stateMachineName: String, inputName: String, value: Float) {
        controller.setNumberState(stateMachineName, inputName, value)
    }

    /**
     * Check if the animation is currently playing
     */
    val isPlaying: Boolean
        get() = renderer?.isPlaying == true

    /**
     * Load the [resource Id][resId] as a rive file and load it into the view.
     *
     * - Optionally provide an [artboardName] to use, or the first artboard in the file.
     * - Optionally provide an [animationName] to load by default, playing without any suggested animations names will simply play the first animation
     * - Enable [autoplay] to start the animation without further prompts.
     * - Configure [alignment] to specify how the animation should be aligned to its container.
     * - Configure [fit] to specify how and if the animation should be resized to fit its container.
     * - Configure [loop] to configure if animations should loop, play once, or ping-pong back and forth. Defaults to the setup in the rive file.
     *
     * @throws [RiveException] if [artboardName] or [animationName] are set and do not exist in the file.
     */
    fun setRiveResource(
        @RawRes resId: Int,
        artboardName: String? = null,
        animationName: String? = null,
        stateMachineName: String? = null,
        autoplay: Boolean = artboardRenderer?.autoplay ?: defaultAutoplay,
        fit: Fit = Fit.CONTAIN,
        alignment: Alignment = Alignment.CENTER,
        loop: Loop = Loop.AUTO,
    ) {
        rendererAttributes.apply {
            this.artboardName = artboardName
            this.animationName = animationName
            this.stateMachineName = stateMachineName
            this.autoplay = autoplay
            this.fit = fit
            this.alignment = alignment
            this.loop = loop
            this.resource = makeMaybeResource(resId)
        }

        loadFileFromResource {
            controller.file = it
            controller.setupScene(rendererAttributes)
        }

        if (renderer != null) {
            configureRenderer()
        }
    }

    /**
     * Create a view file from a byte array and load it into the view
     *
     * - Optionally provide an [artboardName] to use, or the first artboard in the file.
     * - Optionally provide an [animationName] to load by default, playing without any suggested animations names will simply play the first animation
     * - Enable [autoplay] to start the animation without further prompts.
     * - Configure [alignment] to specify how the animation should be aligned to its container.
     * - Configure [fit] to specify how and if the animation should be resized to fit its container.
     * - Configure [loop] to configure if animations should loop, play once, or ping-pong back and forth. Defaults to the setup in the rive file.
     *
     * @throws [RiveException] if [artboardName] or [animationName] are set and do not exist in the file.
     */
    fun setRiveBytes(
        bytes: ByteArray,
        artboardName: String? = null,
        animationName: String? = null,
        stateMachineName: String? = null,
        autoplay: Boolean = artboardRenderer?.autoplay ?: defaultAutoplay,
        fit: Fit = Fit.CONTAIN,
        alignment: Alignment = Alignment.CENTER,
        loop: Loop = Loop.AUTO,
    ) {
        rendererAttributes.apply {
            this.artboardName = artboardName
            this.animationName = animationName
            this.stateMachineName = stateMachineName
            this.autoplay = autoplay
            this.fit = fit
            this.alignment = alignment
            this.loop = loop
            this.resource = makeMaybeResource(bytes)
        }


        loadFileFromResource {
            controller.file = it
            controller.setupScene(rendererAttributes)
        }
        if (renderer != null) {
            configureRenderer()
        }
    }

    /**
     * Set up this View to use the specified Rive [file]. The [file] has been initialized outside
     * this scope and the user passing it in is responsible for cleaning up its resources.
     *
     * - Optionally provide an [artboardName] to use, or the first artboard in the file.
     * - Optionally provide an [animationName] to load by default, playing without any suggested animations names will simply play the first animation
     * - Optionally provide a [stateMachineName] to load by default.
     * - Enable [autoplay] to start the animation without further prompts.
     * - Configure [fit] to specify how and if the animation should be resized to fit its container.
     * - Configure [alignment] to specify how the animation should be aligned to its container.
     * - Configure [loop] to configure if animations should loop, play once, or ping-pong back and forth. Defaults to the setup in the rive file.
     *
     * @throws [RiveException] if [artboardName] or [animationName] are set and do not exist in the file.
     */
    fun setRiveFile(
        file: File,
        artboardName: String? = null,
        animationName: String? = null,
        stateMachineName: String? = null,
        autoplay: Boolean = artboardRenderer?.autoplay ?: defaultAutoplay,
        fit: Fit = Fit.CONTAIN,
        alignment: Alignment = Alignment.CENTER,
        loop: Loop = Loop.AUTO,
    ) {
        rendererAttributes.apply {
            this.artboardName = artboardName
            this.animationName = animationName
            this.stateMachineName = stateMachineName
            this.autoplay = autoplay
            this.fit = fit
            this.alignment = alignment
            this.loop = loop
            this.resource = makeMaybeResource(file)
        }


        controller.file = file
        controller.setupScene(rendererAttributes)

        if (renderer != null) {
            configureRenderer()
        }
    }

    private fun configureRenderer() {
        // If trying to configure the renderer, we assume that it exists.
        artboardRenderer!!.apply {
            this.autoplay = rendererAttributes.autoplay
            this.animationName = rendererAttributes.animationName
            this.stateMachineName = rendererAttributes.stateMachineName
            this.artboardName = rendererAttributes.artboardName
            this.fit = rendererAttributes.fit
            this.alignment = rendererAttributes.alignment
            controller.file?.let { acquireFile(it) }
        }
    }

    override fun onDetachedFromWindow() {
        // Suspend the controller so we can resume playing on reattach.
        controller.isActive = false
        stopFrameMetrics()
        super.onDetachedFromWindow()
    }

    override fun createRenderer(): RendererSkia {
        // Make the Renderer again every time this is visible and reset its state.
        return RiveArtboardRenderer(
            trace = rendererAttributes.riveTraceAnimations,
            controller = controller,
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // If a File hasn't been set yet, try to initialize it.
        if (controller.file == null) {
            loadFileFromResource {
                controller.file = it
                controller.setupScene(rendererAttributes)
            }
        }

        configureRenderer()
        if (renderer!!.trace) {
            startFrameMetrics()
        }
        controller.isActive = true
        // We are attached, start the renderer just to see if we want to play
        renderer!!.start()
    }

    @ControllerStateManagement
    fun saveControllerState(): ControllerState {
        // Invalidate the old resource to prevent it from loading again.
        rendererAttributes.resource = null
        return controller.saveControllerState()
    }

    @ControllerStateManagement
    fun restoreControllerState(state: ControllerState) {
        controller.restoreControllerState(state)
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun startFrameMetrics() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            frameMetricsListener = RendererMetrics(activity).also {
                activity.window.addOnFrameMetricsAvailableListener(
                    it,
                    Handler(Looper.getMainLooper())
                )
            }
        } else {
            Log.w(
                TAG,
                "FrameMetrics is available with Android SDK 24 (Nougat) and higher"
            )
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun stopFrameMetrics() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            frameMetricsListener?.let {
                activity.window.removeOnFrameMetricsAvailableListener(it)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        if (renderer == null) {
            Log.w(TAG, "onMeasure(): Renderer not instantiated yet.")
            return
        }
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val providedWidth = when (widthMode) {
            MeasureSpec.UNSPECIFIED -> artboardRenderer!!.artboardBounds().width().toInt()
            else -> MeasureSpec.getSize(widthMeasureSpec)
        }

        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val providedHeight = when (heightMode) {
            MeasureSpec.UNSPECIFIED -> artboardRenderer!!.artboardBounds().height().toInt()
            else -> MeasureSpec.getSize(heightMeasureSpec)
        }

        bounds.set(0.0f, 0.0f, providedWidth.toFloat(), providedHeight.toFloat())
        // Lets work out how much space our artboard is going to actually use.
        val usedBounds = Rive.calculateRequiredBounds(
            artboardRenderer!!.fit,
            artboardRenderer!!.alignment,
            bounds,
            artboardRenderer!!.artboardBounds()
        )

        //Measure Width
        val width: Int = when (widthMode) {
            MeasureSpec.EXACTLY -> providedWidth
            MeasureSpec.AT_MOST -> min(usedBounds.width().toInt(), providedWidth)
            else ->
                usedBounds.width().toInt()
        }

        val height: Int = when (heightMode) {
            MeasureSpec.EXACTLY -> providedHeight
            MeasureSpec.AT_MOST -> min(usedBounds.height().toInt(), providedHeight)
            else ->
                usedBounds.height().toInt()
        }
        setMeasuredDimension(width, height)
    }

    override fun registerListener(listener: RiveFileController.Listener) {
        controller.registerListener(listener)
    }

    override fun unregisterListener(listener: RiveFileController.Listener) {
        controller.unregisterListener(listener)
    }

    /// could live in RiveTextureView, but that doesn't really know
    /// about the artboard renderer that knows about state machines?
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.apply {
            when (action) {
                MotionEvent.ACTION_MOVE -> artboardRenderer?.pointerEvent(
                    PointerEvents.POINTER_MOVE,
                    x,
                    y
                )

                MotionEvent.ACTION_CANCEL -> artboardRenderer?.pointerEvent(
                    PointerEvents.POINTER_UP,
                    x,
                    y
                )

                MotionEvent.ACTION_DOWN -> artboardRenderer?.pointerEvent(
                    PointerEvents.POINTER_DOWN,
                    x,
                    y
                )

                MotionEvent.ACTION_UP -> artboardRenderer?.pointerEvent(
                    PointerEvents.POINTER_UP,
                    x,
                    y
                )

                else -> {
                    Log.w(TAG, "onTouchEvent(): Renderer not instantiated yet.")
                }
            }
        }
        return true
    }
}

// Custom Volley request to download and create rive files over http
class RiveFileRequest(
    url: String,
    private val listener: Response.Listener<File>,
    errorListener: Response.ErrorListener
) : Request<File>(Method.GET, url, errorListener) {

    override fun deliverResponse(response: File) = listener.onResponse(response)

    override fun parseNetworkResponse(response: NetworkResponse?): Response<File> {
        return try {
            val bytes = response?.data ?: ByteArray(0)
            val file = File(bytes)
            Response.success(file, HttpHeaderParser.parseCacheHeaders(response))
        } catch (e: UnsupportedEncodingException) {
            Response.error(ParseError(e))
        }
    }
}

/**
 * Tracks which resource [RiveAnimationView] will load into the renderer.
 *
 * Use [makeMaybeResource] for making a new value passing in a resource.
 */

sealed class ResourceType {
    class ResourceId(val id: Int) : ResourceType()
    class ResourceUrl(val url: String) : ResourceType()
    class ResourceBytes(val bytes: ByteArray) : ResourceType()
    class ResourceRiveFile(val file: File) : ResourceType()

    companion object {
        /**
         * Factory function for making a [ResourceType].
         *
         * @throws [value] is an unknown type.
         */
        fun makeMaybeResource(value: Any?): ResourceType? {
            return when (value) {
                null -> null
                is Int -> ResourceId(value)
                is String -> ResourceUrl(value)
                is ByteArray -> ResourceBytes(value)
                is File -> ResourceRiveFile(value)
                else -> throw IllegalArgumentException(
                    "Incompatible type ${value.javaClass.simpleName}."
                )
            }
        }
    }
}


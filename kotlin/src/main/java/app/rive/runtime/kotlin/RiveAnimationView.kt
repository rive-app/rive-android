package app.rive.runtime.kotlin

import android.annotation.TargetApi
import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.*
import androidx.annotation.RawRes
import app.rive.runtime.kotlin.core.*
import app.rive.runtime.kotlin.renderers.RendererMetrics
import com.android.volley.NetworkResponse
import com.android.volley.ParseError
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.Volley
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.util.*


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
 * - Provide the [resource][R.styleable.RiveAnimationView_riveResource] to load as a rive file, this can be done later with [setRiveResource] or [setRiveFile].
 * - Alternatively, provide the [url][R.styleable.RiveAnimationView_riveUrl] to load as a rive file over HTTP.
 * - Determine the [artboard][R.styleable.RiveAnimationView_riveArtboard] to use, this defaults to the first artboard in the file.
 * - Enable or disable [autoplay][R.styleable.RiveAnimationView_riveAutoPlay] to start the animation as soon as its available, or leave it to false to control its playback later. defaults to enabled.
 * - Configure [alignment][R.styleable.RiveAnimationView_riveAlignment] to specify how the animation should be aligned to its container.
 * - Configure [fit][R.styleable.RiveAnimationView_riveFit] to specify how and if the animation should be resized to fit its container.
 * - Configure [loop mode][R.styleable.RiveAnimationView_riveLoop] to configure if animations should loop, play once, or pingpong back and forth. Defaults to the setup in the rive file.
 */
open class RiveAnimationView(context: Context, attrs: AttributeSet? = null) :
    RiveTextureView(context, attrs),
    Choreographer.FrameCallback,
    Observable<RiveArtboardRenderer.Listener> {

    companion object {
        // Static Tag for Logging.
        const val TAG = "RiveAnimationView"
    }

    open val defaultAutoplay = true

    public override val renderer: RiveArtboardRenderer;

    private var resourceId: Int? = null
    private var _detachedState: DetachedRiveState? = null


    var fit: Fit
        get() = renderer.fit
        set(value) {
            renderer.fit = value
        }

    var alignment: Alignment
        get() = renderer.alignment
        set(value) {
            renderer.alignment = value
        }

    /**
     * Getter for the loaded [Rive file][File].
     */
    val file: File?
        get() = renderer.file

    /**
     * Helper for determining performance metrics.
     */
    private var frameMetricsListener: Window.OnFrameMetricsAvailableListener? = null

    /**
     * Getter/Setter for the currently loaded artboard Name
     * Setting a new name, will load the new artboard & depending on [autoplay] play them
     */
    var artboardName: String?
        get() = renderer.artboardName
        set(name) {
            renderer.setArtboardByName(name)
        }

    /**
     * Getter/Setter for [autoplay].
     */
    var autoplay: Boolean
        get() = renderer.autoplay
        set(value) {
            renderer.autoplay = value
        }

    /**
     * Get the currently loaded [animation instances][LinearAnimationInstance].
     */
    val animations: List<LinearAnimationInstance>
        get() = renderer.animations

    /**
     * Get the currently loaded [state machine instances][StateMachineInstance].
     */
    val stateMachines: List<StateMachineInstance>
        get() = renderer.stateMachines

    /**
     * Get the currently playing [animation instances][LinearAnimationInstance].
     */
    val playingAnimations: HashSet<LinearAnimationInstance>
        get() = renderer.playingAnimations

    /**
     * Get the currently playing [state machine instances][StateMachineInstance].
     */
    val playingStateMachines: HashSet<StateMachineInstance>
        get() = renderer.playingStateMachines

    init {

        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.RiveAnimationView,
            0, 0
        ).apply {
            try {
                val alignmentIndex = getInteger(R.styleable.RiveAnimationView_riveAlignment, 4)
                val fitIndex = getInteger(R.styleable.RiveAnimationView_riveFit, 1)
                val loopIndex = getInteger(R.styleable.RiveAnimationView_riveLoop, 3)
                val autoplay =
                    getBoolean(R.styleable.RiveAnimationView_riveAutoPlay, defaultAutoplay)
                val riveTraceAnimations =
                    getBoolean(R.styleable.RiveAnimationView_riveTraceAnimations, false)
                val artboardName = getString(R.styleable.RiveAnimationView_riveArtboard)
                val animationName = getString(R.styleable.RiveAnimationView_riveAnimation)
                val stateMachineName = getString(R.styleable.RiveAnimationView_riveStateMachine)
                val resourceId = getResourceId(R.styleable.RiveAnimationView_riveResource, -1)
                val url = getString(R.styleable.RiveAnimationView_riveUrl)
                renderer = RiveArtboardRenderer(autoplay = autoplay, trace = riveTraceAnimations)

                if (resourceId != -1) {
                    setRiveResource(
                        resourceId,
                        alignment = Alignment.values()[alignmentIndex],
                        fit = Fit.values()[fitIndex],
                        loop = Loop.values()[loopIndex],
                        autoplay = autoplay,
                        artboardName = artboardName,
                        stateMachineName = stateMachineName,
                        animationName = animationName,
                    )
                } else {
                    renderer.alignment = Alignment.values()[alignmentIndex]
                    renderer.fit = Fit.values()[fitIndex]
                    renderer.loop = Loop.values()[loopIndex]
                    renderer.autoplay = autoplay
                    renderer.artboardName = artboardName
                    renderer.animationName = animationName
                    renderer.stateMachineName = stateMachineName
                    renderer.advance(0.0f)

                    // If a URL has been provided, initiate downloading
                    url?.let {
                        loadHttp(it)
                    }
                }
            } finally {
                recycle()
            }
        }
    }


    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        super.onSurfaceTextureSizeChanged(surface, width, height)
        renderer.targetBounds = AABB(width.toFloat(), height.toFloat())
    }


    override fun doFrame(frameTimeNanos: Long) {
        if (isRunning) {
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun loadHttp(url: String) {
        val queue = Volley.newRequestQueue(context)
        val stringRequest = RiveFileRequest(url,
            { file ->
                setRiveFile(
                    file,
                    renderer.artboardName,
                    renderer.animationName,
                    renderer.stateMachineName,
                    renderer.autoplay,
                    renderer.fit,
                    renderer.alignment,
                    renderer.loop
                )
            },
            { throw IOException("Unable to download Rive file $url") })
        queue.add(stringRequest)
    }

    /**
     * Pauses all playing [animation instance][LinearAnimationInstance].
     */
    fun pause() {
        renderer.pause()
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
        renderer.pause(animationNames, areStateMachines)
    }


    /**
     * Pauses any [animation instances][LinearAnimationInstance] for an [animation][Animation]
     * called [animationName].
     *
     * Advanced: Multiple [animation instances][LinearAnimationInstance] can running the same
     * [animation][Animation]
     */
    fun pause(animationName: String, isStateMachine: Boolean = false) {
        renderer.pause(animationName, isStateMachine)
    }

    /**
     * Stops all [animation instances][LinearAnimationInstance].
     *
     * Animations Instances will be disposed of completely.
     * Subsequent plays will create new [animation instances][LinearAnimationInstance]
     * for the [animations][Animation] in the file.
     */
    fun stop() {
        renderer.stopAnimations()
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
        renderer.stopAnimations(animationNames, areStateMachines)
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
        renderer.stopAnimations(animationName, isStateMachine)
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
     * For [animations][Animation] without an [animation instance][LinearAnimationInstance] one will be created and played.
     */
    fun play(
        loop: Loop = Loop.AUTO,
        direction: Direction = Direction.AUTO
    ) {
        renderer.play(loop, direction)
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
        areStateMachines: Boolean = false
    ) {
        renderer.play(animationNames, loop, direction, areStateMachines)
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
        direction: Direction = Direction.AUTO, isStateMachine: Boolean = false
    ) {
        renderer.play(animationName, loop, direction, isStateMachine)
    }

    /**
     * Reset the view by resetting the current artboard, before any animations have been applied
     *
     * Note: this will respect [autoplay]
     */
    fun reset() {
        renderer.reset()
    }

    /**
     * Fire the [SMITrigger] input called [inputName] on all active matching state machines
     */
    fun fireState(stateMachineName: String, inputName: String) {
        renderer.fireState(stateMachineName, inputName)
    }

    /**
     * Update the state of the [SMIBoolean] input called [inputName] on all active matching state machines
     * to [value]
     */
    fun setBooleanState(stateMachineName: String, inputName: String, value: Boolean) {
        renderer.setBooleanState(stateMachineName, inputName, value)
    }

    /**
     * Update the state of the [SMINumber] input called [inputName] on all active matching state machines
     * to [value]
     */
    fun setNumberState(stateMachineName: String, inputName: String, value: Float) {
        renderer.setNumberState(stateMachineName, inputName, value)
    }

    /**
     * Check if the animation is currently playing
     */
    val isPlaying: Boolean
        get() = renderer.isPlaying

    /**
     * Load the [resource Id][resId] as a rive file and load it into the view.
     *
     * - Optionally provide an [artboardName] to use, or the first artboard in the file.
     * - Optionally provide an [animationName] to load by default, playing without any suggested animations names will simply play the first animaiton
     * - Enable [autoplay] to start the animation without further prompts.
     * - Configure [alignment] to specify how the animation should be aligned to its container.
     * - Configure [fit] to specify how and if the animation should be resized to fit its container.
     * - Configure [loop] to configure if animations should loop, play once, or pingpong back and forth. Defaults to the setup in the rive file.
     *
     * @throws [RiveException] if [artboardName] or [animationName] are set and do not exist in the file.
     */
    fun setRiveResource(
        @RawRes resId: Int,
        artboardName: String? = null,
        animationName: String? = null,
        stateMachineName: String? = null,
        autoplay: Boolean = renderer.autoplay,
        fit: Fit = Fit.CONTAIN,
        alignment: Alignment = Alignment.CENTER,
        loop: Loop = Loop.AUTO,
    ) {
        resourceId = resId
        val bytes = resources.openRawResource(resId).readBytes()
        setRiveBytes(
            bytes,
            fit = fit,
            alignment = alignment,
            loop = loop,
            artboardName = artboardName,
            animationName = animationName,
            stateMachineName = stateMachineName,
            autoplay = autoplay
        )
    }

    /**
     * Create a view file from a byte array and load it into the view
     *
     * - Optionally provide an [artboardName] to use, or the first artboard in the file.
     * - Optionally provide an [animationName] to load by default, playing without any suggested animations names will simply play the first animaiton
     * - Enable [autoplay] to start the animation without further prompts.
     * - Configure [alignment] to specify how the animation should be aligned to its container.
     * - Configure [fit] to specify how and if the animation should be resized to fit its container.
     * - Configure [loop] to configure if animations should loop, play once, or pingpong back and forth. Defaults to the setup in the rive file.
     *
     * @throws [RiveException] if [artboardName] or [animationName] are set and do not exist in the file.
     */
    fun setRiveBytes(
        bytes: ByteArray,
        artboardName: String? = null,
        animationName: String? = null,
        stateMachineName: String? = null,
        autoplay: Boolean = renderer.autoplay,
        fit: Fit = Fit.CONTAIN,
        alignment: Alignment = Alignment.CENTER,
        loop: Loop = Loop.AUTO,
    ) {
        val file = File(bytes)
        setRiveFile(
            file,
            fit = fit,
            alignment = alignment,
            loop = loop,
            artboardName = artboardName,
            animationName = animationName,
            stateMachineName = stateMachineName,
            autoplay = autoplay
        )
    }

    /**
     * Load the [rive file][File] into the view.
     *
     * - Optionally provide an [artboardName] to use, this defaults to the first artboard in the file.
     * - Optionally provide an [animationName] to load by default, playing without any suggested animations names will simply play the first animations.
     * - Enable [autoplay] to start the animation without further prompts.
     * - Configure [alignment] to specify how the animation should be aligned to its container.
     * - Configure [fit] to specify how and if the animation should be resized to fit its container.
     * - Configure [loop] to configure if animations should loop, play once, or pingpong back and forth. Defaults to the setup in the rive file.
     *
     * @throws [RiveException] if [artboardName] or [animationName] are set and do not exist in the file.
     */
    private fun setRiveFile(
        file: File,
        artboardName: String?,
        animationName: String?,
        stateMachineName: String?,
        autoplay: Boolean,
        fit: Fit,
        alignment: Alignment,
        loop: Loop,
    ) {
        renderer.stopAnimations()
        renderer.clear()
        renderer.fit = fit
        renderer.alignment = alignment
        renderer.loop = loop
        renderer.autoplay = autoplay
        renderer.animationName = animationName
        renderer.stateMachineName = stateMachineName
        renderer.artboardName = artboardName

        renderer.setRiveFile(file)
        renderer.advance(0.0f)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        // Track the playing animations and state machines so we can resume them if the window is
        // attached.
        _detachedState = DetachedRiveState(
            playingAnimationsNames = playingAnimations.map { it.animation.name },
            playingStateMachineNames = playingStateMachines.map { it.stateMachine.name }
        )
        pause()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val detachedState = _detachedState
        if (detachedState != null) {
            play(detachedState.playingAnimationsNames, areStateMachines = false)
            play(detachedState.playingStateMachineNames, areStateMachines = true)
            _detachedState = null
        }

        Choreographer.getInstance().postFrameCallback(this)
//        startFrameMetrics()
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
                "FrameMetrics can work only with Android SDK 24 (Nougat) and higher"
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

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val providedWidth = when (widthMode) {
            MeasureSpec.UNSPECIFIED -> renderer?.artboardBounds()?.width?.toInt()
            else -> MeasureSpec.getSize(widthMeasureSpec)
        }

        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val providedHeight = when (heightMode) {
            MeasureSpec.UNSPECIFIED -> renderer?.artboardBounds()?.height?.toInt()
            else -> MeasureSpec.getSize(heightMeasureSpec)
        }

        // Lets work out how much space our artboard is going to actually use.
        val usedBounds = Rive.calculateRequiredBounds(
            renderer.fit,
            renderer.alignment,
            AABB(providedWidth.toFloat(), providedHeight.toFloat()),
            renderer.artboardBounds()
        )

        //Measure Width
        val width: Int = when (widthMode) {
            MeasureSpec.EXACTLY -> providedWidth
            MeasureSpec.AT_MOST -> Math.min(usedBounds.width.toInt(), providedWidth)
            else ->
                usedBounds.width.toInt()
        }

        val height: Int = when (heightMode) {
            MeasureSpec.EXACTLY -> providedHeight
            MeasureSpec.AT_MOST -> Math.min(usedBounds.height.toInt(), providedHeight)
            else ->
                usedBounds.height.toInt()
        }
        setMeasuredDimension(width, height)
    }

    override fun registerListener(listener: RiveArtboardRenderer.Listener) {
        renderer.registerListener(listener)
    }

    override fun unregisterListener(listener: RiveArtboardRenderer.Listener) {
        renderer.unregisterListener(listener)
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

data class DetachedRiveState(
    val playingAnimationsNames: List<String>,
    val playingStateMachineNames: List<String>
)

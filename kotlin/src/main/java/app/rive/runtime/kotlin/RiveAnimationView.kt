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
import androidx.annotation.CallSuper
import androidx.annotation.RawRes
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewTreeLifecycleOwner
import app.rive.runtime.kotlin.ResourceType.Companion.makeMaybeResource
import app.rive.runtime.kotlin.controllers.ControllerState
import app.rive.runtime.kotlin.controllers.ControllerStateManagement
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.*
import app.rive.runtime.kotlin.core.errors.RiveException
import app.rive.runtime.kotlin.renderers.PointerEvents
import app.rive.runtime.kotlin.renderers.Renderer
import app.rive.runtime.kotlin.renderers.RendererMetrics
import app.rive.runtime.kotlin.renderers.RiveArtboardRenderer
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
 * Xml attrs [AttributeSet] can be used to set initial values for many
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
        const val alignmentIndexDefault = 4 /* Alignment.CENTER */
        const val fitIndexDefault = 1       /* Fit.CONTAIN */
        const val loopIndexDefault = 3      /* Loop.AUTO */
        const val traceAnimationsDefault = false
        const val shouldLoadCDNAssetsDefault = true
        val rendererIndexDefault = Rive.defaultRendererType.value
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
            controller.fit = value
        }

    var alignment: Alignment
        get() = controller.alignment
        set(value) {
            // Not replacing with controller b/c it's currently calling `start()`
            controller.alignment = value
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
        get() = controller.activeArtboard?.name
        set(name) {
            controller.selectArtboard(name)
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
     * The current [LifecycleOwner]. At the time of creation it will be the [Activity].
     */
    private var lifecycleOwner: LifecycleOwner? = getContextAsType<LifecycleOwner>()

    /**
     * Tracks the renderer attributes that need to be applied when this [View] within its lifecycle.
     */
    class RendererAttributes(
        alignmentIndex: Int = alignmentIndexDefault,
        fitIndex: Int = fitIndexDefault,
        loopIndex: Int = loopIndexDefault,
        rendererIndex: Int = rendererIndexDefault,
        var autoplay: Boolean,
        var riveTraceAnimations: Boolean = false,
        var artboardName: String?,
        var animationName: String?,
        var stateMachineName: String?,
        var resource: ResourceType?,
        var assetLoader: FileAssetLoader? = null,
    ) {
        companion object {
            /**
             * When a [name] is provided, it tries to build a [FileAssetLoader].
             * [name] needs to be a full class name, including the package (e.g. `java.lang.Thread`)
             *
             * This function expects that the classname provided is either a [FileAssetLoader], for
             * which the class has a constructor with no parameters, or a [ContextAssetLoader],
             * for which the constructor has a single parameter of type [Context].
             */
            fun assetLoaderFrom(name: String?, context: Context): FileAssetLoader? {
                return if (!name.isNullOrEmpty()) {
                    try {
                        val kClass = Class.forName(name).kotlin
                        val maybeContextAssetLoader =
                            kClass.constructors.find {
                                it.parameters.size == 1 && it.parameters.first().type.classifier == Context::class
                            }?.call(context.applicationContext)
                        if (maybeContextAssetLoader != null) {
                            return maybeContextAssetLoader as ContextAssetLoader
                        }
                        val maybeNoArgConstructor =
                            kClass.constructors.find { it.parameters.isEmpty() }
                        // Returns null if it doesn't exist.
                        return maybeNoArgConstructor?.call() as FileAssetLoader?
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to initialize AssetLoader from name: $name")
                        e.printStackTrace()
                        null
                    }
                } else {
                    null
                }
            }
        }

        var alignment: Alignment = Alignment.fromIndex(alignmentIndex)
        var fit: Fit = Fit.fromIndex(fitIndex)
        var loop: Loop = Loop.fromIndex(loopIndex)
        var rendererType: RendererType = RendererType.fromIndex(rendererIndex)
    }


    class Builder(internal val context: Context) {
        internal var alignment: Alignment? = null
        internal var fit: Fit? = null
        internal var loop: Loop? = null
        internal var rendererType: RendererType? = null
        internal var autoplay: Boolean? = null
        internal var traceAnimations: Boolean? = null
        internal var artboardName: String? = null
        internal var animationName: String? = null
        internal var stateMachineName: String? = null
        internal var assetLoader: FileAssetLoader? = null
        internal var shouldLoadCDNAssets: Boolean = shouldLoadCDNAssetsDefault

        internal var resource: Any? = null
        internal var resourceType: ResourceType? = null

        fun setAlignment(value: Alignment) = apply { alignment = value }
        fun setFit(value: Fit) = apply { fit = value }
        fun setLoop(value: Loop) = apply { loop = value }
        fun setRendererType(value: RendererType) = apply { rendererType = value }
        fun setAutoplay(value: Boolean) = apply { autoplay = value }
        fun setTraceAnimations(value: Boolean) = apply { traceAnimations = value }
        fun setArtboardName(value: String) = apply { artboardName = value }
        fun setAnimationName(value: String) = apply { animationName = value }
        fun setStateMachineName(value: String) = apply { stateMachineName = value }
        fun setResource(value: Any) = apply {
            resourceType = makeMaybeResource(value) // Will throw with invalid types.
            resource = value
        }

        fun setAssetLoader(value: FileAssetLoader) = apply { assetLoader = value }
        fun setShouldLoadCDNAssets(value: Boolean) = apply { shouldLoadCDNAssets = value }

        fun build(): RiveAnimationView {
            return RiveAnimationView(this)
        }
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
                // Try making a custom loader
                val customLoader = RendererAttributes.assetLoaderFrom(
                    getString(R.styleable.RiveAnimationView_riveAssetLoaderClass),
                    context.applicationContext
                )
                val shouldLoadCDNAssets = getBoolean(
                    R.styleable.RiveAnimationView_riveShouldLoadCDNAssets,
                    shouldLoadCDNAssetsDefault
                )

                rendererAttributes = RendererAttributes(
                    alignmentIndex = getInteger(
                        R.styleable.RiveAnimationView_riveAlignment,
                        alignmentIndexDefault
                    ),
                    fitIndex = getInteger(R.styleable.RiveAnimationView_riveFit, fitIndexDefault),
                    loopIndex = getInteger(
                        R.styleable.RiveAnimationView_riveLoop,
                        loopIndexDefault
                    ),
                    autoplay =
                    getBoolean(R.styleable.RiveAnimationView_riveAutoPlay, defaultAutoplay),
                    riveTraceAnimations =
                    getBoolean(
                        R.styleable.RiveAnimationView_riveTraceAnimations,
                        traceAnimationsDefault
                    ),
                    artboardName = getString(R.styleable.RiveAnimationView_riveArtboard),
                    animationName = getString(R.styleable.RiveAnimationView_riveAnimation),
                    stateMachineName = getString(R.styleable.RiveAnimationView_riveStateMachine),
                    resource = resourceFromValue,
                    rendererIndex = getInteger(
                        R.styleable.RiveAnimationView_riveRenderer,
                        rendererIndexDefault
                    ),
                    assetLoader = FallbackAssetLoader(
                        context = context.applicationContext,
                        loader = customLoader,
                        loadCDNAssets = shouldLoadCDNAssets,
                    )
                )

                controller = RiveFileController(
                    loop = rendererAttributes.loop,
                    autoplay = rendererAttributes.autoplay,
                )
                /**
                 * Attach the observer to give us lifecycle hooks
                 * N.B. we're attaching in the constructor because the View can be created without
                 * getting ever attached (e.g. in RecyclerViews) - we need to register the Observer
                 * right away. [lifecycleOwner] at this point is going to be the wrapping Activity.
                 */
                lifecycleOwner?.lifecycle?.addObserver(lifecycleObserver)

                // Initialize resource if we have one.
                resourceFromValue?.let {
                    // Immediately set these values on the controller:
                    // this is either going to be a [ResourceId] or a [ResourceUrl]
                    loadFileFromResource {
                        controller.file = it
                        controller.setupScene(rendererAttributes)
                    }
                }
            } finally {
                recycle()
            }
        }
    }

    constructor(builder: Builder) : this(builder.context) {
        // This is taken for granted: nothing is initializing the renderer in the main constructor.
        require(this.artboardRenderer == null)
        rendererAttributes.apply {
            rendererType = builder.rendererType ?: RendererType.fromIndex(rendererIndexDefault)
            autoplay = builder.autoplay ?: defaultAutoplay
            riveTraceAnimations = builder.traceAnimations ?: traceAnimationsDefault
            artboardName = builder.artboardName
            animationName = builder.animationName
            stateMachineName = builder.stateMachineName
            resource = builder.resourceType
            // Rearrange builder dependencies here.
            (assetLoader as FallbackAssetLoader).resetWith(builder)
            alignment = builder.alignment ?: this.alignment
            fit = builder.fit ?: this.fit
            loop = builder.loop ?: this.loop
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        super.onSurfaceTextureSizeChanged(surface, width, height)
        controller.targetBounds = RectF(0.0f, 0.0f, width.toFloat(), height.toFloat())
    }

    override fun onSurfaceTextureAvailable(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int
    ) {
        super.onSurfaceTextureAvailable(surfaceTexture, width, height)
        controller.targetBounds = RectF(0.0f, 0.0f, width.toFloat(), height.toFloat())
    }

    private fun loadFileFromResource(onComplete: (File) -> Unit) {
        when (val resource = rendererAttributes.resource) {
            null -> Log.w(TAG, "loadResource: no resource to load")
            // Passes the resource through: ownership is coming from elsewhere.
            is ResourceType.ResourceRiveFile -> onComplete(resource.file)
            // loadFromNetwork() releases after onComplete() is called.
            is ResourceType.ResourceUrl -> loadFromNetwork(resource.url, onComplete)
            is ResourceType.ResourceBytes -> {
                val file = File(
                    bytes = resource.bytes,
                    rendererType = rendererAttributes.rendererType,
                    fileAssetLoader = rendererAttributes.assetLoader,
                )
                onComplete(file)
                // Don't retain the handle.
                file.release()
            }

            is ResourceType.ResourceId -> resources.openRawResource(resource.id).use {
                val file = File(
                    bytes = it.readBytes(),
                    rendererType = rendererAttributes.rendererType,
                    fileAssetLoader = rendererAttributes.assetLoader,
                )
                onComplete(file)
                // Don't retain the handle.
                file.release()
            }
        }
    }

    private fun loadFromNetwork(url: String, onComplete: (File) -> Unit) {
        val queue = Volley.newRequestQueue(context.applicationContext)
        val stringRequest = RiveFileRequest(
            url,
            rendererAttributes.rendererType,
            {
                onComplete(it)
                it.release()
            },
            { throw IOException("Unable to download Rive file $url") },
            assetLoader = rendererAttributes.assetLoader
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
     * Pauses any [animation instances][LinearAnimationInstance] with any of the provided
     * [names][animationNames].
     *
     * Advanced: Multiple [animation instances][LinearAnimationInstance] can running the same
     * [animationNames]
     */
    fun pause(animationNames: List<String>, areStateMachines: Boolean = false) {
        controller.pause(animationNames, areStateMachines)
    }


    /**
     * Pauses any [animation instances][LinearAnimationInstance] called [animationName].
     *
     * Advanced: Multiple [animation instances][LinearAnimationInstance] can running the same
     * [animationName] Animation
     */
    fun pause(animationName: String, isStateMachine: Boolean = false) {
        controller.pause(animationName, isStateMachine)
    }

    /**
     * Stops all [animation instances][LinearAnimationInstance].
     *
     * Animations Instances will be disposed of completely.
     * Subsequent plays will create new [animation instances][LinearAnimationInstance]
     * for the animation in the file.
     */
    fun stop() {
        controller.stopAnimations()
        stopFrameMetrics()
    }

    /**
     * Stops any [animation instances][LinearAnimationInstance] with any of the provided
     * [names][animationNames].
     *
     * Animations Instances will be disposed of completely.
     * Subsequent plays will create new [animation instances][LinearAnimationInstance]
     * for the animations in the file.
     *
     * Advanced: Multiple [animation instances][LinearAnimationInstance] can run the same animation
     */
    fun stop(animationNames: List<String>, areStateMachines: Boolean = false) {
        controller.stopAnimations(animationNames, areStateMachines)
    }

    /**
     * Stops any [animation instances][LinearAnimationInstance] called [animationName].
     *
     * Animations Instances will be disposed of completely.
     * Subsequent plays will create new [animation instances][LinearAnimationInstance]
     *
     * Advanced: Multiple [animation instances][LinearAnimationInstance] can run the same animation.
     */
    fun stop(animationName: String, isStateMachine: Boolean = false) {
        controller.stopAnimations(animationName, isStateMachine)
    }

    /**
     * Restarts paused animations.
     * If no animations were playing it plays the first in the [File].
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
     * For any animation without an [animation instance][LinearAnimationInstance] one will be created and played.
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
     * Plays any [animation instances][LinearAnimationInstance] with any of the
     * provided [names][animationNames].
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
     * Plays any [animation instances][LinearAnimationInstance] called [animationName].
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
     * Fire the [SMITrigger] input called [inputName] on the nested artboard represented at [path]
     */
    fun fireStateAtPath(inputName: String, path: String) {
        controller.fireStateAtPath(inputName, path)
    }

    /**
     * Update the state of the [SMIBoolean] input called [inputName] on the nested artboard represented at [path]
     * to [value]
     */
    fun setBooleanStateAtPath(inputName: String, value: Boolean, path: String) {
        controller.setBooleanStateAtPath(inputName, value, path)
    }

    /**
     * Update the state of the [SMINumber] input called [inputName] on the nested artboard represented at [path]
     * to [value]
     */
    fun setNumberStateAtPath(inputName: String, value: Float, path: String) {
        controller.setNumberStateAtPath(inputName, value, path)
    }

    /**
     * Update multiple states at once supplying one or more [inputs]
     */
    fun setMultipleStates(vararg inputs: ChangedInput) {
        controller.queueInputs(*inputs)
    }

    /**
     * Get the current value for a text run named [textRunName] on the active artboard if it exists.
     */
    fun getTextRunValue(textRunName: String): String? = try {
        controller.getTextRunValue(textRunName)
    } catch (e: RiveException) {
        null
    }

    /**
     * Set the text value for a text run named [textRunName] to [textValue] on the active artboard
     * @throws RiveException if the text run does not exist.
     */
    fun setTextRunValue(textRunName: String, textValue: String) {
        controller.setTextRunValue(textRunName, textValue)
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
        autoplay: Boolean = controller.autoplay,
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
        autoplay: Boolean = controller.autoplay,
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
        autoplay: Boolean = controller.autoplay,
        fit: Fit = Fit.CONTAIN,
        alignment: Alignment = Alignment.CENTER,
        loop: Loop = Loop.AUTO,
    ) {
        if (file.rendererType != rendererAttributes.rendererType) {
            throw RiveException(
                "Incompatible Renderer types: file initialized with ${file.rendererType.name}" +
                        " but View is set up for ${rendererAttributes.rendererType.name}"
            )
        }
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

    }

    /**
     * Overrides the current asset loader.
     * It increases the [assetLoader] ref count by one, but its ownership is still
     * in the hands of the caller.
     *
     * A RiveView creates a [FallbackAssetLoader] by default.
     */
    fun setAssetLoader(assetLoader: FileAssetLoader?) {
        if (assetLoader == rendererAttributes.assetLoader) {
            return
        }

        val currentAssetLoader = rendererAttributes.assetLoader
        rendererAttributes.assetLoader = assetLoader

        currentAssetLoader?.release()
        assetLoader?.acquire()

        (lifecycleObserver as? RiveViewLifecycleObserver)?.let { depObserver ->
            currentAssetLoader?.let { old -> depObserver.remove(old) }
            assetLoader?.let { new -> depObserver.insert(new) }
        }
    }

    /**
     * Called from TextureView.onAttachedToWindow() - override for implementing a custom renderer.
     */
    override fun createRenderer(): Renderer {
        return RiveArtboardRenderer(
            trace = rendererAttributes.riveTraceAnimations,
            controller = controller,
            rendererType = rendererAttributes.rendererType,
        )
    }

    override fun createObserver(): LifecycleObserver {
        return RiveViewLifecycleObserver(
            dependencies = listOfNotNull(controller, rendererAttributes.assetLoader).toMutableList()
        )
    }

    /**
     * Ensure that the current [lifecycleOwner] is still valid in this View's tree.
     * Upon creation, the original [lifecycleOwner] is going to be the [Activity] containing this
     * [RiveAnimationView]. If the [View] is added within a different [LifecycleOwner] this needs
     * to be validated again.
     * This could arise when adding the [View] to a [Fragment].
     * If the [lifecycleOwner] has changed, this is swapped out.
     * Calling addObserver again will trigger a onCreate/onStart/onResume again.
     */
    private fun validateLifecycleOwner() {
        val currentLifecycleOwner = ViewTreeLifecycleOwner.get(this)
        currentLifecycleOwner?.let {
            if (it != lifecycleOwner) {
                lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
                lifecycleOwner = currentLifecycleOwner
                lifecycleOwner?.lifecycle?.addObserver(lifecycleObserver)
            }
        }

    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        validateLifecycleOwner()
        // If a File hasn't been set yet, try to initialize it.
        if (controller.file == null) {
            loadFileFromResource {
                controller.file = it
                controller.setupScene(rendererAttributes)
            }
        }

        if (renderer!!.trace) {
            startFrameMetrics()
        }
        controller.isActive = true
        // We are attached, start the renderer just to see if we want to play
        renderer!!.start()
    }

    override fun onDetachedFromWindow() {
        // Deactivate the controller before onDetachedFromWindow().
        controller.isActive = false
        stopFrameMetrics()
        super.onDetachedFromWindow()
    }

    @ControllerStateManagement
    fun saveControllerState(): ControllerState? {
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
            MeasureSpec.UNSPECIFIED -> controller.artboardBounds.width().toInt()
            else -> MeasureSpec.getSize(widthMeasureSpec)
        }

        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val providedHeight = when (heightMode) {
            MeasureSpec.UNSPECIFIED -> controller.artboardBounds.height().toInt()
            else -> MeasureSpec.getSize(heightMeasureSpec)
        }

        bounds.set(0.0f, 0.0f, providedWidth.toFloat(), providedHeight.toFloat())
        // Lets work out how much space our artboard is going to actually use.
        val usedBounds = Rive.calculateRequiredBounds(
            controller.fit,
            controller.alignment,
            bounds,
            controller.artboardBounds
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

    /**
     * Adds a [RiveEventListener] to get notified on [RiveEvent]s
     *
     * Remove with: [removeEventListener]
     */
    fun addEventListener(listener: RiveFileController.RiveEventListener) {
        controller.addEventListener(listener)
    }

    /**
     * Removes the [listener]
     */
    fun removeEventListener(listener: RiveFileController.RiveEventListener) {
        controller.removeEventListener(listener)
    }


    // Handoff to Controller which knows about artboards & state machines.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        event.apply {
            when (action) {
                MotionEvent.ACTION_MOVE -> controller.pointerEvent(
                    PointerEvents.POINTER_MOVE,
                    x,
                    y
                )

                MotionEvent.ACTION_CANCEL -> controller.pointerEvent(
                    PointerEvents.POINTER_UP,
                    x,
                    y
                )

                MotionEvent.ACTION_DOWN -> controller.pointerEvent(
                    PointerEvents.POINTER_DOWN,
                    x,
                    y
                )

                MotionEvent.ACTION_UP -> controller.pointerEvent(
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

/**
 * The [DefaultLifecycleObserver] tied to a [RiveAnimationView].
 * Created within RiveAnimationView() to make sure things are properly cleaned up when the View
 * is destroyed.
 *
 * N.B. since the [RiveAnimationView] can change [LifecycleOwner] during its lifetime, this is
 * updated within [RiveAnimationView.onAttachedToWindow]. If there is a new [LifecycleOwner]
 * [onCreate], [onStart], and [onResume] will be called again when it is registered.
 */
class RiveViewLifecycleObserver(private val dependencies: MutableList<RefCount>) :
    DefaultLifecycleObserver {
    override fun onCreate(owner: LifecycleOwner) {}

    override fun onStart(owner: LifecycleOwner) {}

    override fun onResume(owner: LifecycleOwner) {}

    override fun onPause(owner: LifecycleOwner) {}

    override fun onStop(owner: LifecycleOwner) {}

    /**
     * [DefaultLifecycleObserver] [onDestroy()] is called when the [LifecycleOwner]'s [onDestroy()] method
     * is called.
     * This typically happens when the [Activity] or [Fragment] is in the process of being permanently
     * destroyed.
     */
    @CallSuper
    override fun onDestroy(owner: LifecycleOwner) {
        dependencies.forEach { it.release() }
        owner.lifecycle.removeObserver(this)
    }

    fun remove(dependency: RefCount): Boolean {
        return dependencies.remove(dependency)
    }

    fun insert(dependency: RefCount) {
        dependencies.add(dependency)
    }
}

// Custom Volley request to download and create rive files over http
class RiveFileRequest(
    url: String,
    private val rendererType: RendererType,
    private val listener: Response.Listener<File>,
    errorListener: Response.ErrorListener,
    private val assetLoader: FileAssetLoader? = null
) : Request<File>(Method.GET, url, errorListener) {

    override fun deliverResponse(response: File) = listener.onResponse(response)

    override fun parseNetworkResponse(response: NetworkResponse?): Response<File> {
        return try {
            val bytes = response?.data ?: ByteArray(0)
            val file =
                File(
                    bytes = bytes,
                    rendererType = rendererType,
                    fileAssetLoader = assetLoader,
                )
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

/**
 * Wraps the data necessary for grabbing an input with [name] with [value]
 * [value] is necessary when wrapping [SMINumber] and [SMIBoolean] inputs.
 */
data class ChangedInput(val stateMachineName: String, val name: String, val value: Any? = null, val nestedArtboardPath: String? = null)
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
import android.view.MotionEvent
import android.view.View
import android.view.Window
import androidx.annotation.CallSuper
import androidx.annotation.RawRes
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import app.rive.runtime.kotlin.ResourceType.Companion.makeMaybeResource
import app.rive.runtime.kotlin.controllers.ControllerState
import app.rive.runtime.kotlin.controllers.ControllerStateManagement
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Artboard
import app.rive.runtime.kotlin.core.ContextAssetLoader
import app.rive.runtime.kotlin.core.Direction
import app.rive.runtime.kotlin.core.FallbackAssetLoader
import app.rive.runtime.kotlin.core.File
import app.rive.runtime.kotlin.core.FileAssetLoader
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.LinearAnimationInstance
import app.rive.runtime.kotlin.core.Loop
import app.rive.runtime.kotlin.core.RefCount
import app.rive.runtime.kotlin.core.RendererType
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.core.RiveEvent
import app.rive.runtime.kotlin.core.SMIBoolean
import app.rive.runtime.kotlin.core.SMINumber
import app.rive.runtime.kotlin.core.SMITrigger
import app.rive.runtime.kotlin.core.StateMachineInstance
import app.rive.runtime.kotlin.core.errors.RiveException
import app.rive.runtime.kotlin.core.errors.TextValueRunException
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
import kotlin.math.min


/**
 * This view aims to provide the most straightforward way to get Rive graphics into your
 * application.
 *
 * Simply add the view to your activity and you are good to go!
 *
 * Very simple animations can be configured completely from a layout file. We also expose a thin API
 * layer to allow more control over how animations are played.
 *
 * All of this is built upon the C++ wrappers under the `app.rive.runtime.kotlin.core` namespace
 * which can be used directly for the most flexibility.
 */
open class RiveAnimationView(context: Context, attrs: AttributeSet? = null) :
    RiveTextureView(context, attrs), Observable<RiveFileController.Listener> {
    companion object {
        const val TAG = "RiveAnimationView"

        // Default attribute values.
        val alignmentIndexDefault = Alignment.CENTER.ordinal
        val fitIndexDefault = Fit.CONTAIN.ordinal
        val loopIndexDefault = Loop.AUTO.ordinal
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
     * The scale factor to use for [Fit.LAYOUT]. If `null`, it will use a density determined by Rive
     * (automatic).
     *
     * @see layoutScaleFactorAutomatic
     */
    var layoutScaleFactor: Float?
        get() = controller.layoutScaleFactor
        set(value) {
            // Not replacing with controller b/c it's currently calling `start()`
            controller.layoutScaleFactor = value
        }

    /**
     * The automatic scale factor set by Rive. This value will only be used if [layoutScaleFactor]
     * is `null`.
     */
    var layoutScaleFactorAutomatic: Float
        get() = controller.layoutScaleFactorAutomatic
        internal set(value) {
            // Not replacing with controller b/c it's currently calling `start()`
            controller.layoutScaleFactorAutomatic = value
        }

    /** Getter for the loaded [Rive file][File]. */
    val file: File?
        get() = controller.file

    /** Helper for determining performance metrics. */
    private var frameMetricsListener: Window.OnFrameMetricsAvailableListener? = null
    private val bounds = RectF()

    /**
     * Getter/Setter for the currently loaded artboard name. Setting a new name will load the new
     * artboard and, depending on [autoplay], play them.
     */
    var artboardName: String?
        get() = controller.activeArtboard?.name
        set(name) {
            controller.selectArtboard(name)
        }

    /** Getter/Setter for [autoplay]. */
    var autoplay: Boolean
        get() = controller.autoplay
        set(value) {
            controller.autoplay = value
        }

    /** Get the currently loaded [animation instances][LinearAnimationInstance]. */
    val animations: List<LinearAnimationInstance>
        get() = controller.animations

    /** Get the currently loaded [state machine instances][StateMachineInstance]. */
    val stateMachines: List<StateMachineInstance>
        get() = controller.stateMachines

    /** Get the currently playing [animation instances][LinearAnimationInstance]. */
    val playingAnimations: HashSet<LinearAnimationInstance>
        get() = controller.playingAnimations

    /** Get the currently playing [state machine instances][StateMachineInstance]. */
    val playingStateMachines: HashSet<StateMachineInstance>
        get() = controller.playingStateMachines

    /** The current [LifecycleOwner]. At the time of creation it will be the [Activity]. */
    private var lifecycleOwner: LifecycleOwner? = getContextAsType<LifecycleOwner>()

    /**
     * Tracks the renderer attributes that need to be applied to this [View] within its lifecycle.
     */
    class RendererAttributes(
        alignmentIndex: Int = alignmentIndexDefault,
        fitIndex: Int = fitIndexDefault,
        loopIndex: Int = loopIndexDefault,
        rendererIndex: Int = rendererIndexDefault,
        var autoplay: Boolean,
        var autoBind: Boolean = false,
        var riveTraceAnimations: Boolean = false,
        var artboardName: String?,
        var animationName: String?,
        var stateMachineName: String?,
        var resource: ResourceType?,
        var assetLoader: FileAssetLoader? = null,
    ) {
        companion object {
            /**
             * When a [name] is provided, it tries to build a [FileAssetLoader]. [name] needs to be
             * a full class name, including the package (e.g. `java.lang.Thread`)
             *
             * This function expects that the classname provided is either a [FileAssetLoader], for
             * which the class has a constructor with no parameters, or a [ContextAssetLoader],
             * for which the constructor has a single parameter of type [Context].
             */
            fun assetLoaderFrom(name: String?, context: Context): FileAssetLoader? {
                if (name.isNullOrEmpty()) return null

                return try {
                    val clazz = Class.forName(name)
                    val contextConstructor = clazz.constructors.find {
                        it.parameterTypes.size == 1 && it.parameterTypes[0] == Context::class.java
                    }
                    contextConstructor?.newInstance(context.applicationContext)?.let {
                        if (it is ContextAssetLoader) return it
                    }

                    val noArgConstructor = clazz.constructors.find { it.parameterTypes.isEmpty() }
                    noArgConstructor?.newInstance()?.let {
                        if (it is FileAssetLoader) return it
                    }

                    Log.e(TAG, "Failed to initialize AssetLoader: No suitable constructor in $name")
                    null
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize AssetLoader from name: $name", e)
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
        internal var autoBind: Boolean = false
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
        fun setAutoBind(value: Boolean) = apply { autoBind = value }
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
            attrs, R.styleable.RiveAnimationView, 0, 0
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
                        R.styleable.RiveAnimationView_riveAlignment, alignmentIndexDefault
                    ),
                    fitIndex = getInteger(R.styleable.RiveAnimationView_riveFit, fitIndexDefault),
                    loopIndex = getInteger(
                        R.styleable.RiveAnimationView_riveLoop, loopIndexDefault
                    ),
                    autoplay = getBoolean(
                        R.styleable.RiveAnimationView_riveAutoPlay,
                        defaultAutoplay
                    ),
                    autoBind = getBoolean(
                        R.styleable.RiveAnimationView_riveAutoBind, false
                    ),
                    riveTraceAnimations = getBoolean(
                        R.styleable.RiveAnimationView_riveTraceAnimations, traceAnimationsDefault
                    ),
                    artboardName = getString(R.styleable.RiveAnimationView_riveArtboard),
                    animationName = getString(R.styleable.RiveAnimationView_riveAnimation),
                    stateMachineName = getString(R.styleable.RiveAnimationView_riveStateMachine),
                    resource = resourceFromValue,
                    rendererIndex = getInteger(
                        R.styleable.RiveAnimationView_riveRenderer, rendererIndexDefault
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
                 * Attach the observer to give us lifecycle hooks.
                 *
                 * N.B.: We're attaching in the constructor because the View can be created without
                 * ever getting attached (e.g. in RecyclerViews) - we need to register the Observer
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
            autoBind = builder.autoBind
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
        surfaceTexture: SurfaceTexture, width: Int, height: Int
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

    /** Pauses all playing [animation instances][LinearAnimationInstance]. */
    fun pause() {
        artboardRenderer?.stop()
        controller.pause()
        stopFrameMetrics()
    }

    /**
     * Pauses any [animation instances][LinearAnimationInstance] with any of the provided
     * [names][animationNames].
     *
     * Advanced: Multiple [animation instances][LinearAnimationInstance] can be running the same
     * [animationNames].
     */
    fun pause(animationNames: List<String>, areStateMachines: Boolean = false) {
        controller.pause(animationNames = animationNames, areStateMachines = areStateMachines)
    }


    /**
     * Pauses any [animation instances][LinearAnimationInstance] called [animationName].
     *
     * Advanced: Multiple [animation instances][LinearAnimationInstance] can be running the same
     * [animationName] Animation.
     */
    fun pause(animationName: String, isStateMachine: Boolean = false) {
        controller.pause(animationName = animationName, isStateMachine = isStateMachine)
    }

    /**
     * Stops all [animation instances][LinearAnimationInstance].
     *
     * Animations instances will be disposed of completely. Subsequent plays will create new
     * [animation instances][LinearAnimationInstance] for the animation in the file.
     */
    fun stop() {
        controller.stopAnimations()
        stopFrameMetrics()
    }

    /**
     * Stops any [animation instances][LinearAnimationInstance] with any of the provided
     * [names][animationNames].
     *
     * Animations instances will be disposed of completely. Subsequent plays will create new
     * [animation instances][LinearAnimationInstance] for the animations in the file.
     *
     * Advanced: Multiple [animation instances][LinearAnimationInstance] can run the same animation.
     */
    fun stop(animationNames: List<String>, areStateMachines: Boolean = false) {
        controller.stopAnimations(
            animationNames = animationNames, areStateMachines = areStateMachines
        )
    }

    /**
     * Stops any [animation instances][LinearAnimationInstance] called [animationName].
     *
     * Animations instances will be disposed of completely. Subsequent plays will create new
     * [animation instances][LinearAnimationInstance].
     *
     * Advanced: Multiple [animation instances][LinearAnimationInstance] can run the same animation.
     */
    fun stop(animationName: String, isStateMachine: Boolean = false) {
        controller.stopAnimations(animationName = animationName, isStateMachine = isStateMachine)
    }

    /**
     * Restarts paused animations. If no animations were playing it plays the first in the [File].
     *
     * @experimental Optionally provide a [loop mode][Loop] to overwrite the animations configured
     *    loop mode. Already playing animation instances will be updated to this loop mode if
     *    provided.
     * @experimental Optionally provide a [direction][Direction] to set the direction an animation
     *    is playing in. Already playing animation instances will be updated to this direction
     *    immediately. Backwards animations will start from the end.
     * @experimental Optionally provide a [settleInitialState][Boolean] to inform the state machine
     *    to settle its state on initialization by determining its starting state based of the
     *    initial input values.
     *
     * For any animation without an [animation instance][LinearAnimationInstance] one will be
     * created and played.
     */
    fun play(
        loop: Loop = Loop.AUTO,
        direction: Direction = Direction.AUTO,
        settleInitialState: Boolean = true
    ) {
        rendererAttributes.apply {
            this.loop = loop
        }
        controller.play(loop = loop, direction = direction, settleInitialState = settleInitialState)
    }

    /**
     * Plays any [animation instances][LinearAnimationInstance] with any of the provided
     * [names][animationNames].
     *
     * @see play
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
            animationNames = animationNames,
            loop = loop,
            direction = direction,
            areStateMachines = areStateMachines,
            settleInitialState = settleInitialState
        )
    }

    /**
     * Plays any [animation instances][LinearAnimationInstance] called [animationName].
     *
     * @see play
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
            animationName = animationName,
            loop = loop,
            direction = direction,
            isStateMachine = isStateMachine,
            settleInitialState = settleInitialState
        )
    }

    /**
     * Reset the view by resetting the current artboard before any animations have been applied.
     *
     * Note: This will respect [autoplay].
     */
    fun reset() {
        artboardRenderer?.reset()
    }

    /**
     * Fire an [SMITrigger] input.
     *
     * @param stateMachineName The state machine name.
     * @param inputName The trigger input name.
     */
    fun fireState(stateMachineName: String, inputName: String) {
        controller.fireState(stateMachineName = stateMachineName, inputName = inputName)
    }

    /**
     * Update the state of an [SMIBoolean] input.
     *
     * @param stateMachineName The state machine name.
     * @param inputName The boolean input name.
     * @param value The new value.
     */
    fun setBooleanState(stateMachineName: String, inputName: String, value: Boolean) {
        controller.setBooleanState(
            stateMachineName = stateMachineName, inputName = inputName, value = value
        )
    }

    /**
     * Update the state of an [SMINumber] input.
     *
     * @param stateMachineName The state machine name.
     * @param inputName The number input name.
     * @param value The new value.
     */
    fun setNumberState(stateMachineName: String, inputName: String, value: Float) {
        controller.setNumberState(
            stateMachineName = stateMachineName, inputName = inputName, value = value
        )
    }

    /**
     * Fire an [SMITrigger] input.
     *
     * @param inputName The trigger name.
     * @param path The path to the nested artboard.
     */
    fun fireStateAtPath(inputName: String, path: String) {
        controller.fireStateAtPath(inputName = inputName, path = path)
    }

    /**
     * Update the state of an [SMIBoolean] input.
     *
     * @param inputName The boolean input name.
     * @param value The new value.
     * @param path The path to the nested artboard.
     */
    fun setBooleanStateAtPath(inputName: String, value: Boolean, path: String) {
        controller.setBooleanStateAtPath(inputName = inputName, value = value, path = path)
    }

    /**
     * Update the state of an [SMINumber] input.
     *
     * @param inputName The number input name.
     * @param value The new value.
     * @param path The path to the nested artboard.
     */
    fun setNumberStateAtPath(inputName: String, value: Float, path: String) {
        controller.setNumberStateAtPath(inputName = inputName, value = value, path = path)
    }

    /** Update multiple states at once supplying one or more [inputs]. */
    fun setMultipleStates(vararg inputs: ChangedInput) {
        controller.queueInputs(*inputs)
    }

    /**
     * Get the current value for a text run named [textRunName] on the active artboard if it exists.
     */
    fun getTextRunValue(textRunName: String): String? {
        return controller.getTextRunValue(textRunName = textRunName)
    }

    /**
     * Get the text value for a text run named [textRunName] on the nested artboard represented at
     * [path].
     */
    fun getTextRunValue(textRunName: String, path: String): String? {
        return controller.getTextRunValue(textRunName = textRunName, path = path)
    }


    /**
     * Set the text value for a text run named [textRunName] to [textValue] on the active artboard.
     *
     * @throws TextValueRunException if the text run does not exist.
     */
    fun setTextRunValue(textRunName: String, textValue: String) {
        controller.setTextRunValue(textRunName = textRunName, textValue = textValue)
    }

    /**
     * Set the text value for a text run named [textRunName] to [textValue] on the nested artboard
     * represented at [path].
     *
     * @throws TextValueRunException if the text run does not exist.
     */
    fun setTextRunValue(textRunName: String, textValue: String, path: String) {
        controller.setTextRunValue(textRunName = textRunName, textValue = textValue, path = path)
    }

    /** Get the active [Artboard]'s volume. */
    fun getVolume(): Float? = controller.getVolume()

    /** Set the active [Artboard]'s volume to [value]. */
    fun setVolume(value: Float) = controller.setVolume(value)

    /** Check if the animation is currently playing. */
    val isPlaying: Boolean
        get() = renderer?.isPlaying == true

    /**
     * Load the [resource ID][resId] as a Rive file into the view.
     *
     * @param resId The resource ID to load.
     * @param artboardName Optionally provide an named artboard to use. Defaults to the first
     *    artboard in the file.
     * @param animationName Optionally provide an named animation to load. If not supplied, playing
     *    will default to the first animation.
     * @param stateMachineName Optionally provide a named state machine to load.
     * @param autoplay Enable autoplay to start the animation automatically.
     * @param fit Configure how the animation should be resized to fit its container.
     * @param alignment Configure how the animation should be aligned to its container.
     * @param loop Configure if animations should loop, play once, or ping-pong back and forth.
     *    Defaults to the setup in the Rive file.
     * @throws [RiveException] if [artboardName] or [animationName] are set and do not exist in the
     *    file.
     */
    fun setRiveResource(
        @RawRes resId: Int,
        artboardName: String? = null,
        animationName: String? = null,
        stateMachineName: String? = null,
        autoplay: Boolean = controller.autoplay,
        autoBind: Boolean = false,
        fit: Fit = Fit.fromIndex(fitIndexDefault),
        alignment: Alignment = Alignment.fromIndex(alignmentIndexDefault),
        loop: Loop = Loop.fromIndex(loopIndexDefault),
    ) {
        rendererAttributes.apply {
            this.artboardName = artboardName
            this.animationName = animationName
            this.stateMachineName = stateMachineName
            this.autoplay = autoplay
            this.autoBind = autoBind
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
     * Create a Rive file from a byte array and load it into the view.
     *
     * @param bytes The byte array to load as a Rive file.
     * @throws [RiveException] if [artboardName] or [animationName] are set and do not exist in the
     *    file.
     * @see setRiveResource
     */
    fun setRiveBytes(
        bytes: ByteArray,
        artboardName: String? = null,
        animationName: String? = null,
        stateMachineName: String? = null,
        autoplay: Boolean = controller.autoplay,
        autoBind: Boolean = false,
        fit: Fit = Fit.fromIndex(fitIndexDefault),
        alignment: Alignment = Alignment.fromIndex(alignmentIndexDefault),
        loop: Loop = Loop.fromIndex(loopIndexDefault),
    ) {
        rendererAttributes.apply {
            this.artboardName = artboardName
            this.animationName = animationName
            this.stateMachineName = stateMachineName
            this.autoplay = autoplay
            this.autoBind = autoBind
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
     * Set this view to use the specified Rive [file]. The file must be initialized outside this
     * scope and the caller is responsible for cleaning up its resources.
     *
     * @param file The Rive file to load.
     * @throws [RiveException] if [artboardName] or [animationName] are set and do not exist in the
     *    file.
     * @see setRiveResource
     */
    fun setRiveFile(
        file: File,
        artboardName: String? = null,
        animationName: String? = null,
        stateMachineName: String? = null,
        autoplay: Boolean = controller.autoplay,
        autoBind: Boolean = false,
        fit: Fit = Fit.fromIndex(fitIndexDefault),
        alignment: Alignment = Alignment.fromIndex(alignmentIndexDefault),
        loop: Loop = Loop.fromIndex(loopIndexDefault),
    ) {
        if (file.rendererType != rendererAttributes.rendererType) {
            throw RiveException(
                "Incompatible Renderer types: file initialized with ${file.rendererType.name}" + " but View is set up for ${rendererAttributes.rendererType.name}"
            )
        }
        rendererAttributes.apply {
            this.artboardName = artboardName
            this.animationName = animationName
            this.stateMachineName = stateMachineName
            this.autoplay = autoplay
            this.autoBind = autoBind
            this.fit = fit
            this.alignment = alignment
            this.loop = loop
            this.resource = makeMaybeResource(file)
        }

        controller.file = file
        controller.setupScene(rendererAttributes)
    }

    /**
     * Overrides the current asset loader. It increases the [assetLoader] ref count by one, but its
     * ownership is still in the hands of the caller.
     *
     * A RiveAnimationView creates a [FallbackAssetLoader] by default.
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
     * Ensure that the current [lifecycleOwner] is still valid in this View's tree. Upon creation,
     * the original [lifecycleOwner] is going to be the [Activity][android.app.Activity] containing
     * this [RiveAnimationView]. If the [View] is added within a different [LifecycleOwner]
     * this needs to be validated again. This could arise when adding the [View] to a
     * [Fragment][android.app.Fragment]. If the [lifecycleOwner] has changed, this is swapped out.
     * Calling addObserver again will trigger a onCreate/onStart/onResume again.
     */
    private fun validateLifecycleOwner() {

        val currentLifecycleOwner = this.findViewTreeLifecycleOwner()
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
                    it, Handler(Looper.getMainLooper())
                )
            }
        } else {
            Log.w(TAG, "FrameMetrics is available with Android SDK 24 (Nougat) and higher")
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

        // Rive automatically sets to the current density. If [layoutScaleFactor] is not
        // set by the user, this value will be used.
        controller.layoutScaleFactorAutomatic = resources.displayMetrics.density
        controller.requireArtboardResize.set(true) // artboard requires resizing depending on Fit

        bounds.set(0.0f, 0.0f, providedWidth.toFloat(), providedHeight.toFloat())
        // Lets work out how much space our artboard is going to actually use.
        val usedBounds = Rive.calculateRequiredBounds(
            controller.fit,
            controller.alignment,
            bounds,
            controller.artboardBounds,
            controller.layoutScaleFactorActive
        )

        //Measure Width
        val width: Int = when (widthMode) {
            MeasureSpec.EXACTLY -> providedWidth
            MeasureSpec.AT_MOST -> min(usedBounds.width().toInt(), providedWidth)
            else -> usedBounds.width().toInt()
        }

        val height: Int = when (heightMode) {
            MeasureSpec.EXACTLY -> providedHeight
            MeasureSpec.AT_MOST -> min(usedBounds.height().toInt(), providedHeight)
            else -> usedBounds.height().toInt()
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
     * Adds a [RiveEventListener][RiveFileController.RiveEventListener] to get notified on
     * [RiveEvent]s.
     *
     * Remove with: [removeEventListener].
     */
    fun addEventListener(listener: RiveFileController.RiveEventListener) {
        controller.addEventListener(listener)
    }

    /** Removes the [listener]. */
    fun removeEventListener(listener: RiveFileController.RiveEventListener) {
        controller.removeEventListener(listener)
    }


    // Handoff to Controller which knows about artboards & state machines.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        event.apply {
            when (action) {
                MotionEvent.ACTION_MOVE -> controller.pointerEvent(
                    PointerEvents.POINTER_MOVE, x, y
                )

                MotionEvent.ACTION_CANCEL -> controller.pointerEvent(
                    PointerEvents.POINTER_UP, x, y
                )

                MotionEvent.ACTION_DOWN -> controller.pointerEvent(
                    PointerEvents.POINTER_DOWN, x, y
                )

                MotionEvent.ACTION_UP -> controller.pointerEvent(
                    PointerEvents.POINTER_UP, x, y
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
 * The [DefaultLifecycleObserver] tied to a [RiveAnimationView]. Created within RiveAnimationView()
 * to make sure things are properly cleaned up when the View is destroyed.
 *
 * Note: Since the [RiveAnimationView] can change [LifecycleOwner] during its lifetime, this is
 * updated within [RiveAnimationView.onAttachedToWindow]. If there is a new [LifecycleOwner]
 * [onCreate], [onStart], and [onResume] will be called again when it is registered.
 */
open class RiveViewLifecycleObserver(protected val dependencies: MutableList<RefCount>) :
    DefaultLifecycleObserver {
    override fun onCreate(owner: LifecycleOwner) {}

    override fun onStart(owner: LifecycleOwner) {}

    override fun onResume(owner: LifecycleOwner) {}

    override fun onPause(owner: LifecycleOwner) {}

    override fun onStop(owner: LifecycleOwner) {}

    /**
     * [DefaultLifecycleObserver.onDestroy] is called when the [LifecycleOwner]'s
     * [ON_DESTROY][Lifecycle.Event.ON_DESTROY] event is thrown. This typically happens when the
     * [Activity][android.app.Activity] or [Fragment][android.app.Fragment] is in the process of
     * being permanently destroyed.
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

// Custom Volley request to download and create Rive files over HTTP
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
            val file = File(
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
         * @throws IllegalArgumentException If [value] is an unknown type.
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
 * Wraps the data necessary for grabbing an input with [name] with [value] [value] is necessary when
 * wrapping [SMINumber] and [SMIBoolean] inputs.
 */
data class ChangedInput(
    val stateMachineName: String,
    val name: String,
    val value: Any? = null,
    val nestedArtboardPath: String? = null
)
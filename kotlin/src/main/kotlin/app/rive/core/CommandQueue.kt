package app.rive.core

import android.graphics.Color
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.annotation.ColorInt
import androidx.annotation.Keep
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.rive.Artboard
import app.rive.Fit
import app.rive.RiveDrawToBufferException
import app.rive.RiveFile
import app.rive.RiveFileException
import app.rive.RiveInitializationException
import app.rive.RiveLog
import app.rive.ViewModelInstance
import app.rive.ViewModelInstanceSource
import app.rive.ViewModelSource
import app.rive.core.RenderContextGL.Companion.TAG
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorker
import app.rive.runtime.kotlin.core.File.Enum
import app.rive.runtime.kotlin.core.ViewModel
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.Continuation
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration

/**
 * [CommandQueue] is named to match the underlying core C++ class, which reflects its algorithm: a
 * queue of commands that are dispatched to a command server which receives them and operates on
 * Rive files.
 *
 * But for the purposes of a public API, it makes more sense to think of this less in terms of its
 * algorithm and more in terms of its purpose, which is to dispatch work and manage memory for a
 * worker thread. This type alias preserves the internal name to continue matching the C++ class it
 * represents while also exposing a more semantic name for users.
 */
typealias RiveWorker = CommandQueue

/**
 * Additional alias for the interior type `RiveWorker.PropertyUpdate` due to not being accessible
 * from [RiveWorker].
 */
typealias RivePropertyUpdate<T> = CommandQueue.PropertyUpdate<T>

const val COMMAND_QUEUE_TAG = "Rive/CQ"

/**
 * A [CommandQueue] is the worker that runs Rive in a thread. It holds all of
 * the state, including assets ([images][ImageHandle], [audio][AudioHandle], and
 * [fonts][FontHandle]), [Rive files][RiveFile], [artboards][Artboard], state machines, and
 * [view model instances][ViewModelInstance].
 *
 * Instances of the command queue are reference counted. At initialization, the command queue has a
 * ref count of 1. Call [acquire] to increment the ref count, and [release] to decrement it. When
 * the ref count reaches 0, the command queue is disposed, and its resources are released. Using
 * a disposed command queue will throw an [IllegalStateException]. Any pending operations will be
 * notified with a [CancellationException].
 *
 * The command queue normally is passed into [rememberRiveFile] or, alternatively, created by
 * default if one is not supplied. This is then transitively supplied to other Rive elements, such
 * as artboards, when the Rive file is passed in.
 *
 * It is important to understand the threading model. Each command queue begins a system thread. The
 * "command" part of the name indicates that "commands" are serialized to the CommandServer, all in
 * the Rive C++ runtime. This design minimizes shared state. As a consequence, all operations are
 * asynchronous.
 *
 * If the operation creates a resource, it will be received as a handle, a monotonically increasing
 * opaque identifier that can be translated to a pointer on the command server. A handle can be used
 * immediately, since commands are ordered, ensuring validity at the time of any operation that uses
 * it.
 *
 * If instead the operation is a query, such as getting the names of artboards or view models, it
 * is represented as a suspend function. In most cases, the result can be cached, as Rive files are
 * largely immutable.
 *
 * You may choose to have multiple command queues to run multiple Rive files in parallel. However,
 * be aware that these instances cannot share memory. This means that each file and asset must be
 * loaded into each command queue separately.
 *
 * A command queue needs to be polled to receive messages from the command server. This is handled
 * by the [rememberRiveWorker] composable or by calling [beginPolling].
 *
 * @param renderContext The [RenderContext] to use for rendering. Currently only OpenGL is
 *    supported. The CommandQueue takes ownership of the passed context.
 * @param bridge The [CommandQueueBridge] to use for native implementation. Leave as default - used
 *    for testing.
 * @throws RiveInitializationException If the command queue cannot be created for any reason.
 */
class CommandQueue(
    private val renderContext: RenderContext = RenderContextGL(),
    private val bridge: CommandQueueBridge = CommandQueueJNIBridge(),
) : RefCounted {
    private external fun cppPollMessages(pointer: Long)
    private external fun cppGetArtboardNames(
        pointer: Long,
        requestID: Long,
        fileHandle: Long
    )

    private external fun cppGetStateMachineNames(
        pointer: Long,
        requestID: Long,
        artboardHandle: Long
    )

    private external fun cppGetViewModelNames(
        pointer: Long,
        requestID: Long,
        fileHandle: Long
    )

    private external fun cppGetViewModelInstanceNames(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        viewModelName: String
    )

    private external fun cppGetViewModelProperties(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        viewModelName: String
    )

    private external fun cppGetEnums(
        pointer: Long,
        requestID: Long,
        fileHandle: Long
    )

    private external fun cppCreateDefaultArtboard(
        pointer: Long,
        requestID: Long,
        fileHandle: Long
    ): Long

    private external fun cppCreateArtboardByName(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        name: String
    ): Long

    private external fun cppDeleteArtboard(pointer: Long, requestID: Long, artboardHandle: Long)

    private external fun cppCreateDefaultStateMachine(
        pointer: Long,
        requestID: Long,
        artboardHandle: Long
    ): Long

    private external fun cppCreateStateMachineByName(
        pointer: Long,
        requestID: Long,
        artboardHandle: Long,
        name: String
    ): Long

    private external fun cppDeleteStateMachine(
        pointer: Long,
        requestID: Long,
        stateMachineHandle: Long
    )

    private external fun cppAdvanceStateMachine(
        pointer: Long,
        stateMachineHandle: Long,
        deltaTimeNs: Long
    )

    private external fun cppNamedVMCreateBlankVMI(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        viewModelName: String
    ): Long

    private external fun cppDefaultVMCreateBlankVMI(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        artboardHandle: Long
    ): Long

    private external fun cppNamedVMCreateDefaultVMI(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        viewModelName: String
    ): Long

    private external fun cppDefaultVMCreateDefaultVMI(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        artboardHandle: Long
    ): Long

    private external fun cppNamedVMCreateNamedVMI(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        viewModelName: String,
        instanceName: String
    ): Long

    private external fun cppDefaultVMCreateNamedVMI(
        pointer: Long,
        requestID: Long,
        fileHandle: Long,
        artboardHandle: Long,
        instanceName: String
    ): Long

    private external fun cppReferenceNestedVMI(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long,
        path: String
    ): Long

    private external fun cppReferenceListItemVMI(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long,
        path: String,
        index: Int
    ): Long

    private external fun cppDeleteViewModelInstance(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long
    )

    private external fun cppBindViewModelInstance(
        pointer: Long,
        requestID: Long,
        stateMachineHandle: Long,
        viewModelInstanceHandle: Long,
    )

    private external fun cppSetNumberProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        value: Float
    )

    private external fun cppGetNumberProperty(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String
    )

    private external fun cppSetStringProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        value: String
    )

    private external fun cppGetStringProperty(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String
    )

    private external fun cppSetBooleanProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        value: Boolean
    )

    private external fun cppGetBooleanProperty(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String
    )

    private external fun cppSetEnumProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        value: String
    )

    private external fun cppGetEnumProperty(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String
    )

    private external fun cppSetColorProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        value: Int
    )

    private external fun cppGetColorProperty(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String
    )

    private external fun cppFireTriggerProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String
    )

    private external fun cppSubscribeToProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        propertyType: Int
    )

    private external fun cppSetImageProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        imageHandle: Long
    )

    private external fun cppSetArtboardProperty(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        artboardHandle: Long
    )

    private external fun cppGetListSize(
        pointer: Long,
        requestID: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String
    )

    private external fun cppInsertToListAtIndex(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        index: Int,
        itemHandle: Long
    )

    private external fun cppAppendToList(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        itemHandle: Long
    )

    private external fun cppRemoveFromListAtIndex(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        index: Int
    )

    private external fun cppRemoveFromList(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        itemHandle: Long
    )

    private external fun cppSwapListItems(
        pointer: Long,
        viewModelInstanceHandle: Long,
        propertyPath: String,
        indexA: Int,
        indexB: Int
    )

    private external fun cppDecodeImage(pointer: Long, requestID: Long, bytes: ByteArray)
    private external fun cppDeleteImage(pointer: Long, imageHandle: Long)
    private external fun cppRegisterImage(
        pointer: Long,
        name: String,
        imageHandle: Long
    )

    private external fun cppUnregisterImage(pointer: Long, name: String)
    private external fun cppDecodeAudio(pointer: Long, requestID: Long, bytes: ByteArray)
    private external fun cppDeleteAudio(pointer: Long, audioHandle: Long)
    private external fun cppRegisterAudio(
        pointer: Long,
        name: String,
        audioHandle: Long
    )

    private external fun cppUnregisterAudio(pointer: Long, name: String)
    private external fun cppDecodeFont(pointer: Long, requestID: Long, bytes: ByteArray)
    private external fun cppDeleteFont(pointer: Long, fontHandle: Long)
    private external fun cppRegisterFont(
        pointer: Long,
        name: String,
        fontHandle: Long
    )

    private external fun cppUnregisterFont(pointer: Long, name: String)
    private external fun cppPointerMove(
        pointer: Long,
        stateMachineHandle: Long,
        fit: Byte,
        alignment: Byte,
        layoutScale: Float,
        surfaceWidth: Float,
        surfaceHeight: Float,
        pointerID: Int,
        x: Float,
        y: Float
    )

    private external fun cppPointerDown(
        pointer: Long,
        stateMachineHandle: Long,
        fit: Byte,
        alignment: Byte,
        layoutScale: Float,
        surfaceWidth: Float,
        surfaceHeight: Float,
        pointerID: Int,
        x: Float,
        y: Float
    )

    private external fun cppPointerUp(
        pointer: Long,
        stateMachineHandle: Long,
        fit: Byte,
        alignment: Byte,
        layoutScale: Float,
        surfaceWidth: Float,
        surfaceHeight: Float,
        pointerID: Int,
        x: Float,
        y: Float
    )

    private external fun cppPointerExit(
        pointer: Long,
        stateMachineHandle: Long,
        fit: Byte,
        alignment: Byte,
        layoutScale: Float,
        surfaceWidth: Float,
        surfaceHeight: Float,
        pointerID: Int,
        x: Float,
        y: Float
    )

    private external fun cppResizeArtboard(
        pointer: Long,
        artboardHandle: Long,
        width: Int,
        height: Int,
        scaleFactor: Float
    )

    private external fun cppResetArtboardSize(
        pointer: Long,
        artboardHandle: Long
    )

    private external fun cppCreateRiveRenderTarget(pointer: Long, width: Int, height: Int): Long
    private external fun cppCreateDrawKey(pointer: Long): Long
    private external fun cppDraw(
        pointer: Long,
        renderContextPointer: Long,
        surfaceNativePointer: Long,
        drawKey: Long,
        artboardHandle: Long,
        stateMachineHandle: Long,
        renderTargetPointer: Long,
        width: Int,
        height: Int,
        fit: Byte,
        alignment: Byte,
        scaleFactor: Float,
        clearColor: Int
    )

    private external fun cppDrawToBuffer(
        pointer: Long,
        renderContextPointer: Long,
        surfaceNativePointer: Long,
        drawKey: Long,
        artboardHandle: Long,
        stateMachineHandle: Long,
        renderTargetPointer: Long,
        width: Int,
        height: Int,
        fit: Byte,
        alignment: Byte,
        scaleFactor: Float,
        clearColor: Int,
        buffer: ByteArray
    )

    private external fun cppRunOnCommandServer(pointer: Long, work: () -> Unit)

    companion object {
        /**
         * Maximum number of Rive components that can safely use this CommandQueue instance
         * concurrently.
         */
        const val MAX_CONCURRENT_SUBSCRIBERS = 32
    }

    private val cppPointer = RCPointer(
        bridge.cppConstructor(renderContext.nativeObjectPointer),
        COMMAND_QUEUE_TAG,
        ::dispose
    )
    private var listeners = bridge.cppCreateListeners(cppPointer.pointer, this)

    /**
     * Cleanup to be performed when the ref count reaches 0.
     *
     * Any pending continuations are cancelled to avoid callers hanging indefinitely.
     */
    private fun dispose(cppPointer: Long) {
        bridge.cppDelete(cppPointer)

        listeners.close()
        renderContext.close()

        // Cancel and clear any pending JNI continuations so callers don't hang.
        pendingContinuations.values.toList().forEach { cont ->
            cont.cancel(CancellationException("CommandQueue was released before operation could complete."))
        }
        pendingContinuations.clear()
    }

    // Implement the RefCounted interface by delegating to cppPointer
    override fun acquire(source: String) = cppPointer.acquire(source)
    override fun release(source: String, reason: String) = cppPointer.release(source, reason)
    override val refCount: Int
        get() = cppPointer.refCount
    override val isDisposed: Boolean
        get() = cppPointer.isDisposed

    /**
     * Tie this CommandQueue's lifetime to a LifecycleOwner. This will call [release] when the
     * owner's lifecycle reaches DESTROYED. Returns an [AutoCloseable] that can be used to manually
     * release early; calling it is idempotent.
     *
     * Also manages the audio engine start/stop state by acquiring a reference when the lifecycle is
     * "resumed" and releasing when "paused" or destroyed.
     *
     * Typical usage from an Activity or Fragment:
     *
     * val cq = CommandQueue().also { it.withLifecycle(this, "MyActivity") }
     *
     * ⚠️ Do not use this with a command queue created with [rememberRiveWorker]. A command queue
     * created with that method has its lifecycle managed by the Composable's lifecycle.
     *
     * @param owner The LifecycleOwner to tie this CommandQueue's lifetime to, such as an Activity
     *    or Fragment.
     * @param source A string indicating the source of the acquisition, for logging purposes, e.g.
     *    "MyActivity".
     */
    fun withLifecycle(owner: LifecycleOwner, source: String): AutoCloseable {
        lateinit var onClose: CloseOnce // lateinit to break circular reference
        var audioAcquired = false
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                if (!audioAcquired) {
                    AudioEngine.acquire()
                    audioAcquired = true
                }
            }

            override fun onPause(owner: LifecycleOwner) {
                if (audioAcquired) {
                    AudioEngine.release()
                    audioAcquired = false
                }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                onClose.close()
            }
        }
        onClose = CloseOnce("CommandQueue (withLifecycle)") {
            owner.lifecycle.removeObserver(observer)
            if (audioAcquired) {
                AudioEngine.release()
                audioAcquired = false
            }
            cppPointer.release(source, "Closed by withLifecycle")
        }
        owner.lifecycle.addObserver(observer)
        // If already RESUMED, acquire immediately since the callback won't fire
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            AudioEngine.acquire()
            audioAcquired = true
        }
        return onClose
    }

    private val _settledFlow = MutableSharedFlow<StateMachineHandle>(
        replay = 0,
        extraBufferCapacity = MAX_CONCURRENT_SUBSCRIBERS, // Protects for the worst case of simultaneous settles
        onBufferOverflow = BufferOverflow.DROP_OLDEST // It's fine to miss a settle event - worse to block
    )

    /** Flow that emits when a state machine has settled. */
    val settledFlow: SharedFlow<StateMachineHandle> = _settledFlow

    /**
     * Contains the data associated with a property update event.
     *
     * @param handle The handle of the ViewModelInstance that the property belongs to.
     * @param propertyPath The path to the property that was updated.
     * @param value The new value of the property.
     */
    data class PropertyUpdate<T>(
        val handle: ViewModelInstanceHandle,
        val propertyPath: String,
        val value: T
    )

    private val _numberPropertyFlow = MutableSharedFlow<PropertyUpdate<Float>>(
        replay = 0,
        extraBufferCapacity = MAX_CONCURRENT_SUBSCRIBERS,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Hot flow that emits when any number property is updated. */
    val numberPropertyFlow: SharedFlow<PropertyUpdate<Float>> = _numberPropertyFlow

    private val _stringPropertyFlow = MutableSharedFlow<PropertyUpdate<String>>(
        replay = 0,
        extraBufferCapacity = MAX_CONCURRENT_SUBSCRIBERS,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Hot flow that emits when any string property is updated. */
    val stringPropertyFlow: SharedFlow<PropertyUpdate<String>> = _stringPropertyFlow

    private val _booleanPropertyFlow = MutableSharedFlow<PropertyUpdate<Boolean>>(
        replay = 0,
        extraBufferCapacity = MAX_CONCURRENT_SUBSCRIBERS,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Hot flow that emits when any boolean property is updated. */
    val booleanPropertyFlow: SharedFlow<PropertyUpdate<Boolean>> = _booleanPropertyFlow

    private val _enumPropertyFlow = MutableSharedFlow<PropertyUpdate<String>>(
        replay = 0,
        extraBufferCapacity = MAX_CONCURRENT_SUBSCRIBERS,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Hot flow that emits when any enum property is updated. */
    val enumPropertyFlow: SharedFlow<PropertyUpdate<String>> = _enumPropertyFlow

    private val _colorPropertyFlow = MutableSharedFlow<PropertyUpdate<Int>>(
        replay = 0,
        extraBufferCapacity = MAX_CONCURRENT_SUBSCRIBERS,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Hot flow that emits when any color property is updated. */
    val colorPropertyFlow: SharedFlow<PropertyUpdate<Int>> = _colorPropertyFlow

    private val _triggerPropertyFlow = MutableSharedFlow<PropertyUpdate<Unit>>(
        replay = 0,
        extraBufferCapacity = MAX_CONCURRENT_SUBSCRIBERS,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Hot flow that emits when any trigger property is fired. */
    val triggerPropertyFlow: SharedFlow<PropertyUpdate<Unit>> = _triggerPropertyFlow

    init {
        RiveLog.d(COMMAND_QUEUE_TAG) { "Creating command queue" }
    }

    /**
     * Begin polling the CommandQueue for messages from the CommandServer. Without this, callbacks
     * and errors will not be delivered. This should typically be called by tying to the lifecycle
     * scope of the containing activity, fragment, or composable, e.g. with `lifecycleScope.launch`.
     *
     * The polling will automatically start and stop based on the lifecycle state, only polling when
     * the lifecycle is in the RESUMED state.
     *
     * @param lifecycle The lifecycle bounding the polling.
     * @param ticker The frame ticker to use for polling. Defaults to [ChoreographerFrameTicker],
     *    which uses the Choreographer to sync to the display refresh rate.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    suspend fun beginPolling(
        lifecycle: Lifecycle,
        ticker: FrameTicker = ChoreographerFrameTicker
    ) = lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        RiveLog.d(COMMAND_QUEUE_TAG) { "Starting command queue polling" }
        while (isActive) {
            ticker.withFrame {
                pollMessages()
            }
        }
        RiveLog.d(COMMAND_QUEUE_TAG) { "Stopping command queue polling" }
    }

    /**
     * Poll messages from the CommandServer to the CommandQueue. This is the channel that all
     * callbacks and errors arrive on. Should be called every frame, regardless of whether there is
     * any advancing or drawing.
     *
     * This method represents a single poll operation. To continuously poll, use [beginPolling],
     * which will handle starting and stopping the polling based on a lifecycle.
     *
     * @throws IllegalStateException If the CommandQueue has been released.
     * @see [beginPolling]
     */
    @Throws(IllegalStateException::class)
    fun pollMessages() = cppPollMessages(cppPointer.pointer)

    /**
     * Create a Rive rendering surface for Rive to draw into.
     *
     * @param surfaceTexture The Android [SurfaceTexture], likely coming from a [TextureView].
     * @return A [RiveSurface] that can be used for rendering.
     * @throws RuntimeException If the surface could not be created.
     */
    @Throws(RuntimeException::class)
    fun createRiveSurface(surfaceTexture: SurfaceTexture): RiveSurface =
        renderContext.createSurface(surfaceTexture, nextDrawKey(), this)

    /**
     * Create an off-screen image surface for rendering to a buffer.
     *
     * @param width The width of the image surface in pixels.
     * @param height The height of the image surface in pixels.
     * @return A [RiveSurface] that can be used for off-screen rendering.
     * @throws RuntimeException If the surface could not be created.
     */
    @Throws(RuntimeException::class)
    fun createImageSurface(width: Int, height: Int): RiveSurface =
        renderContext.createImageSurface(width, height, nextDrawKey(), this)

    /**
     * Destroy a Rive rendering surface. The surface will not be valid for use after this call.
     *
     * This runs on the command server thread to ensure that any in-flight draw commands finish with
     * a valid [SurfaceTexture].
     *
     * @param surface The [RiveSurface] to destroy.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun destroyRiveSurface(surface: RiveSurface) = runOnCommandServer { surface.close() }

    /**
     * Creates a Rive render target on the command server thread. This is a synchronous call that
     * blocks until the render target is created.
     *
     * @param width The width of the render target in pixels.
     * @param height The height of the render target in pixels.
     * @return The native pointer to the created render target.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun createRiveRenderTarget(width: Int, height: Int): Long {
        RiveLog.d(TAG) { "Creating Rive render target on command server thread" }
        return cppCreateRiveRenderTarget(cppPointer.pointer, width, height)
    }

    private fun nextDrawKey() = DrawKey(cppCreateDrawKey(cppPointer.pointer))

    /**
     * Callback when there is any error on a file operation. The continuation will be resumed with
     * an exception, including a message with the specific error.
     *
     * @param requestID The request ID for the original operation, used to complete the
     *    continuation.
     * @param error The error message.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onFileError")
    internal fun onFileError(requestID: Long, error: String) {
        RiveLog.e(COMMAND_QUEUE_TAG) { "File error for request $requestID: $error" }
        (pendingContinuations.remove(requestID) as? Continuation<FileHandle>)?.resumeWithException(
            RiveFileException("File error: $error")
        )
    }

    /**
     * Callback when there is any error on an artboard operation. The continuation will be resumed
     * with an exception, including a message with the specific error.
     *
     * @param requestID The request ID for the original operation, used to complete the
     *    continuation.
     * @param error The error message.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onArtboardError")
    internal fun onArtboardError(requestID: Long, error: String) {
        RiveLog.e(COMMAND_QUEUE_TAG) { "Artboard error for request $requestID: $error" }
        (pendingContinuations.remove(requestID) as? Continuation<ArtboardHandle>)?.resumeWithException(
            RuntimeException("Artboard error: $error")
        )
    }

    /**
     * Callback when there is any error on a state machine operation. The continuation will be
     * resumed with an exception, including a message with the specific error.
     *
     * @param requestID The request ID for the original operation, used to complete the
     *    continuation.
     * @param error The error message.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onStateMachineError")
    internal fun onStateMachineError(requestID: Long, error: String) {
        RiveLog.e(COMMAND_QUEUE_TAG) { "State machine error for request $requestID: $error" }
        (pendingContinuations.remove(requestID) as? Continuation<StateMachineHandle>)?.resumeWithException(
            RuntimeException("State machine error: $error")
        )
    }

    /**
     * Callback when there is any error on a view model instance operation. The continuation will be
     * resumed with an exception, including a message with the specific error.
     *
     * @param requestID The request ID for the original operation, used to complete the
     *    continuation.
     * @param error The error message.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onViewModelInstanceError")
    internal fun onViewModelInstanceError(requestID: Long, error: String) {
        RiveLog.e(COMMAND_QUEUE_TAG) { "View model instance error for request $requestID: $error" }
        (pendingContinuations.remove(requestID) as? Continuation<ViewModelInstanceHandle>)?.resumeWithException(
            RuntimeException("View model instance error: $error")
        )
    }

    /**
     * Load a Rive into the command queue. Returns the handle in either [onFileLoaded] or an error
     * in [onFileError].
     *
     * @param bytes The bytes of the Rive file to load.
     * @return A [FileHandle] that represents the loaded Rive file.
     * @throws RiveFileException If the file could not be loaded.
     * @throws IllegalStateException If the CommandQueue has been released.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     */
    @Throws(RuntimeException::class, IllegalStateException::class, CancellationException::class)
    suspend fun loadFile(bytes: ByteArray): FileHandle = suspendNativeRequest { requestID ->
        bridge.cppLoadFile(cppPointer.pointer, requestID, bytes)
    }

    /**
     * Callback when a Rive file is loaded successfully, from [loadFile].
     *
     * @param requestID The request ID used when loading the file, used to complete the
     *    continuation.
     * @param fileHandle The handle of the loaded file.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onFileLoaded")
    internal fun onFileLoaded(requestID: Long, fileHandle: FileHandle) {
        (pendingContinuations.remove(requestID) as? Continuation<FileHandle>)?.resume(fileHandle)
    }

    /**
     * Counterpart to [loadFile] to free the resources associated to the file handle.
     *
     * This method is idempotent; calling it multiple times with the same file handle has no
     * additional effect.
     *
     * @param fileHandle The handle of the file to delete.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun deleteFile(fileHandle: FileHandle) =
        bridge.cppDeleteFile(cppPointer.pointer, nextRequestID.getAndIncrement(), fileHandle.handle)

    /**
     * Query the file for available artboard names. Returns on [onArtboardsListed].
     *
     * @param fileHandle The handle of the file to query.
     * @return A list of artboard names in the file.
     * @throws RuntimeException If the file handle is invalid.
     * @throws IllegalStateException If the CommandQueue has been released.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     */
    @Throws(RuntimeException::class, IllegalStateException::class, CancellationException::class)
    suspend fun getArtboardNames(fileHandle: FileHandle): List<String> =
        suspendNativeRequest { requestID ->
            cppGetArtboardNames(
                cppPointer.pointer,
                requestID,
                fileHandle.handle
            )
        }

    /**
     * Callback when the artboard names are listed, from [getArtboardNames].
     *
     * @param requestID The request ID used when querying the artboard names, used to complete the
     *    continuation.
     * @param names The list of artboard names in the file.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onArtboardsListed")
    internal fun onArtboardsListed(requestID: Long, names: List<String>) {
        (pendingContinuations.remove(requestID) as? Continuation<List<String>>)?.resume(names)
    }

    /**
     * Query the artboard for available state machine names. Returns on [onStateMachinesListed].
     *
     * @param artboardHandle The handle of the artboard to query.
     * @return A list of state machine names in the artboard.
     * @throws RuntimeException If the artboard handle is invalid.
     * @throws IllegalStateException If the CommandQueue has been released.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     */
    @Throws(RuntimeException::class, IllegalStateException::class, CancellationException::class)
    suspend fun getStateMachineNames(artboardHandle: ArtboardHandle): List<String> =
        suspendNativeRequest { requestID ->
            cppGetStateMachineNames(
                cppPointer.pointer,
                requestID,
                artboardHandle.handle
            )
        }

    /**
     * Callback when the state machine names are listed, from [getStateMachineNames].
     *
     * @param requestID The request ID used when querying the state machine names, used to complete
     *    the continuation.
     * @param names The list of state machine names in the artboard.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onStateMachinesListed")
    internal fun onStateMachinesListed(requestID: Long, names: List<String>) {
        (pendingContinuations.remove(requestID) as? Continuation<List<String>>)?.resume(names)
    }

    /**
     * Query the file for available view model names. Returns on [onViewModelsListed].
     *
     * @param fileHandle The handle of the file to query.
     * @return A list of view model names in the file.
     * @throws RuntimeException If the file handle is invalid.
     * @throws IllegalStateException If the CommandQueue has been released.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     */
    @Throws(RuntimeException::class, IllegalStateException::class, CancellationException::class)
    suspend fun getViewModelNames(fileHandle: FileHandle): List<String> =
        suspendNativeRequest { requestID ->
            cppGetViewModelNames(
                cppPointer.pointer,
                requestID,
                fileHandle.handle
            )
        }

    /**
     * Callback when the view model names are listed, from [getViewModelNames].
     *
     * @param requestID The request ID used when querying the view model names, used to complete the
     *    continuation.
     * @param names The list of view model names in the file.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onViewModelsListed")
    internal fun onViewModelsListed(requestID: Long, names: List<String>) {
        (pendingContinuations.remove(requestID) as? Continuation<List<String>>)?.resume(names)
    }

    /**
     * Query the file for available view model instance names for the given view model. Returns on
     * [onViewModelInstancesListed].
     *
     * @param fileHandle The handle of the file that owns the view model.
     * @param viewModelName The name of the view model to query.
     * @return A list of view model instance names on the view model.
     * @throws RuntimeException If the file handle is invalid or the named view model does not
     *    exist.
     * @throws IllegalStateException If the CommandQueue has been released.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     */
    @Throws(IllegalStateException::class, CancellationException::class)
    suspend fun getViewModelInstanceNames(
        fileHandle: FileHandle,
        viewModelName: String
    ): List<String> = suspendNativeRequest { requestID ->
        cppGetViewModelInstanceNames(
            cppPointer.pointer,
            requestID,
            fileHandle.handle,
            viewModelName
        )
    }

    /**
     * Callback when the view model instance names are listed, from [getViewModelInstanceNames].
     *
     * @param requestID The request ID used when querying the view model instance names, used to
     *    complete the continuation.
     * @param names The list of view model instance names on the view model.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onViewModelInstancesListed")
    internal fun onViewModelInstancesListed(requestID: Long, names: List<String>) {
        (pendingContinuations.remove(requestID) as? Continuation<List<String>>)?.resume(names)
    }

    /**
     * Query the file for available view model properties for the given view model. Returns on
     * [onViewModelPropertiesListed].
     *
     * @param fileHandle The handle of the file that owns the view model.
     * @param viewModelName The name of the view model to query.
     * @return A list of properties on the view model.
     * @throws RuntimeException If the file handle is invalid or the named view model does not
     *    exist.
     * @throws IllegalStateException If the CommandQueue has been released.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     */
    @Throws(RuntimeException::class, IllegalStateException::class, CancellationException::class)
    suspend fun getViewModelProperties(
        fileHandle: FileHandle,
        viewModelName: String
    ): List<ViewModel.Property> = suspendNativeRequest { requestID ->
        cppGetViewModelProperties(
            cppPointer.pointer,
            requestID,
            fileHandle.handle,
            viewModelName
        )
    }

    /**
     * Callback when the view model properties are listed, from [getViewModelProperties].
     *
     * @param requestID The request ID used when querying the view model properties, used to
     *    complete the continuation.
     * @param properties The list of properties on the view model.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onViewModelPropertiesListed")
    internal fun onViewModelPropertiesListed(
        requestID: Long,
        properties: List<ViewModel.Property>
    ) {
        (pendingContinuations.remove(requestID) as? Continuation<List<ViewModel.Property>>)?.resume(
            properties
        )
    }

    /**
     * Query the file for available enums. Returns on [onEnumsListed].
     *
     * @param fileHandle The handle of the file to query.
     * @return A list of enums in the file.
     * @throws RuntimeException If the file handle is invalid.
     * @throws IllegalStateException If the CommandQueue has been released.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     */
    @Throws(RuntimeException::class, IllegalStateException::class, CancellationException::class)
    suspend fun getEnums(fileHandle: FileHandle): List<Enum> = suspendNativeRequest { requestID ->
        cppGetEnums(
            cppPointer.pointer,
            requestID,
            fileHandle.handle
        )
    }

    /**
     * Callback when the enums are listed, from [getEnums].
     *
     * @param requestID The request ID used when querying the enums, used to complete the
     *    continuation.
     * @param enums The list of enums in the file.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onEnumsListed")
    internal fun onEnumsListed(requestID: Long, enums: List<Enum>) {
        (pendingContinuations.remove(requestID) as? Continuation<List<Enum>>)?.resume(enums)
    }

    /**
     * Create the default artboard for the given file. This is the artboard marked "Default" in the
     * Rive editor.
     *
     * @param fileHandle The handle of the file that owns the artboard.
     * @return The handle of the created artboard.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun createDefaultArtboard(fileHandle: FileHandle): ArtboardHandle =
        ArtboardHandle(
            cppCreateDefaultArtboard(
                cppPointer.pointer,
                nextRequestID.getAndIncrement(),
                fileHandle.handle
            )
        )

    /**
     * Create an artboard by name for the given file. This is useful when the file has multiple
     * artboards and you want to create a specific one.
     *
     * @param fileHandle The handle of the file that owns the artboard.
     * @param name The name of the artboard to create.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun createArtboardByName(fileHandle: FileHandle, name: String): ArtboardHandle =
        ArtboardHandle(
            cppCreateArtboardByName(
                cppPointer.pointer,
                nextRequestID.getAndIncrement(),
                fileHandle.handle,
                name
            )
        )

    /**
     * Delete an artboard and free its resources. This is useful when you no longer need
     * the artboard and want to free up memory. Counterpart to [createDefaultArtboard] or
     * [createArtboardByName].
     *
     * @param artboardHandle The handle of the artboard to delete.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun deleteArtboard(artboardHandle: ArtboardHandle) =
        cppDeleteArtboard(
            cppPointer.pointer,
            nextRequestID.getAndIncrement(),
            artboardHandle.handle
        )

    /**
     * Create the default state machine for the given artboard. This is the state machine marked
     * "Default" in the Rive editor.
     *
     * ℹ️ State machines are initialized to the "design" state upon creation, which is almost never
     * the desired starting point for runtime use. Additionally, the Entry state absorbs the first
     * advance call due to not tracking the remaining advance time after exiting.
     *
     * For these reasons, you will almost always want to advance the state machine by 0ns after
     * binding view models and before your first real advance call to allow it to settle into its
     * initial "animated" state.
     *
     * @param artboardHandle The handle of the artboard that owns the state machine.
     * @return The handle of the created state machine.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun createDefaultStateMachine(artboardHandle: ArtboardHandle): StateMachineHandle =
        StateMachineHandle(
            cppCreateDefaultStateMachine(
                cppPointer.pointer,
                nextRequestID.getAndIncrement(),
                artboardHandle.handle
            )
        )

    /**
     * Create a state machine by name for the given artboard. This is useful when the artboard has
     * multiple state machines and you want to create a specific one.
     *
     * ℹ️ State machines are initialized to the "design" state upon creation, which is almost never
     * the desired starting point for runtime use. Additionally, the Entry state absorbs the first
     * advance call due to not tracking the remaining advance time after exiting.
     *
     * For these reasons, you will almost always want to advance the state machine by 0ns after
     * binding view models and before your first real advance call to allow it to settle into its
     * initial "animated" state.
     *
     * @param artboardHandle The handle of the artboard that owns the state machine.
     * @param name The name of the state machine to create.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun createStateMachineByName(artboardHandle: ArtboardHandle, name: String): StateMachineHandle =
        StateMachineHandle(
            cppCreateStateMachineByName(
                cppPointer.pointer,
                nextRequestID.getAndIncrement(),
                artboardHandle.handle,
                name
            )
        )

    /**
     * Delete a state machine and free its resources. This is useful when you no longer need the
     * state machine and want to free up memory. Counterpart to [createDefaultStateMachine] or
     * [createStateMachineByName].
     *
     * @param stateMachineHandle The handle of the state machine to delete.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun deleteStateMachine(stateMachineHandle: StateMachineHandle) =
        cppDeleteStateMachine(
            cppPointer.pointer,
            nextRequestID.getAndIncrement(),
            stateMachineHandle.handle
        )

    /**
     * Advance the state machine by the given delta time in nanoseconds.
     *
     * @param stateMachineHandle The handle of the state machine to advance.
     * @param deltaTime The delta time to advance the state machine by.
     * @throws RuntimeException If the state machine handle is invalid.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(RuntimeException::class, IllegalStateException::class)
    fun advanceStateMachine(
        stateMachineHandle: StateMachineHandle,
        deltaTime: Duration
    ) = cppAdvanceStateMachine(
        cppPointer.pointer,
        stateMachineHandle.handle,
        deltaTime.inWholeNanoseconds
    )

    /**
     * Callback when the state machine settles. This is called when the state machine has determined
     * that it has no meaningful changes left to apply, and can optimize by not calling advance or
     * draw. Called when this is true from [advanceStateMachine].
     *
     * Unsettling happens when the user "perturbs" the state machine, such as by pointer events or
     * setting a value on a property.
     *
     * This sends the handle of the settled state machine to the [settledFlow] flow, which can be
     * collected to react to the state machine settling.
     *
     * @param stateMachineHandle The handle of the state machine that has settled.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onStateMachineSettled")
    internal fun onStateMachineSettled(stateMachineHandle: StateMachineHandle) {
        _settledFlow.tryEmit(stateMachineHandle)
    }

    /**
     * Create a new view model instance based on the given source. The source is a combination of a
     * view model source and an instance source, which multiply to capture all the possible ways to
     * create an instance (+1 for referencing).
     *
     * @param fileHandle The handle of the file that owns the view model instance.
     * @param source The source of the view model instance to create, which internally also has a
     *    [view model source][ViewModelSource].
     * @return The handle of the created view model instance.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun createViewModelInstance(
        fileHandle: FileHandle,
        source: ViewModelInstanceSource
    ): ViewModelInstanceHandle =
        when (source) {
            is ViewModelInstanceSource.Blank -> when (val vm = source.vmSource) {
                is ViewModelSource.Named ->
                    ViewModelInstanceHandle(
                        cppNamedVMCreateBlankVMI(
                            cppPointer.pointer,
                            nextRequestID.getAndIncrement(),
                            fileHandle.handle,
                            vm.viewModelName
                        )
                    )

                is ViewModelSource.DefaultForArtboard ->
                    ViewModelInstanceHandle(
                        cppDefaultVMCreateBlankVMI(
                            cppPointer.pointer,
                            nextRequestID.getAndIncrement(),
                            fileHandle.handle,
                            vm.artboard.artboardHandle.handle
                        )
                    )
            }

            is ViewModelInstanceSource.Default -> when (val vm = source.vmSource) {
                is ViewModelSource.Named ->
                    ViewModelInstanceHandle(
                        cppNamedVMCreateDefaultVMI(
                            cppPointer.pointer,
                            nextRequestID.getAndIncrement(),
                            fileHandle.handle,
                            vm.viewModelName
                        )
                    )

                is ViewModelSource.DefaultForArtboard ->
                    ViewModelInstanceHandle(
                        cppDefaultVMCreateDefaultVMI(
                            cppPointer.pointer,
                            nextRequestID.getAndIncrement(),
                            fileHandle.handle,
                            vm.artboard.artboardHandle.handle
                        )
                    )
            }

            is ViewModelInstanceSource.Named -> when (val vm = source.vmSource) {
                is ViewModelSource.Named ->
                    ViewModelInstanceHandle(
                        cppNamedVMCreateNamedVMI(
                            cppPointer.pointer,
                            nextRequestID.getAndIncrement(),
                            fileHandle.handle,
                            vm.viewModelName,
                            source.instanceName
                        )
                    )

                is ViewModelSource.DefaultForArtboard ->
                    ViewModelInstanceHandle(
                        cppDefaultVMCreateNamedVMI(
                            cppPointer.pointer,
                            nextRequestID.getAndIncrement(),
                            fileHandle.handle,
                            vm.artboard.artboardHandle.handle,
                            source.instanceName
                        )
                    )
            }

            is ViewModelInstanceSource.Reference -> ViewModelInstanceHandle(
                cppReferenceNestedVMI(
                    cppPointer.pointer,
                    nextRequestID.getAndIncrement(),
                    source.parentInstance.instanceHandle.handle,
                    source.path
                )
            )

            is ViewModelInstanceSource.ReferenceListItem -> ViewModelInstanceHandle(
                cppReferenceListItemVMI(
                    cppPointer.pointer,
                    nextRequestID.getAndIncrement(),
                    source.parentInstance.instanceHandle.handle,
                    source.pathToList,
                    source.index
                )
            )
        }

    /**
     * Delete a view model instance and free its resources. This is useful when you no longer need
     * the view model instance and want to free up memory. Counterpart to [createViewModelInstance].
     *
     * @param viewModelInstanceHandle The handle of the view model instance to delete.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun deleteViewModelInstance(
        viewModelInstanceHandle: ViewModelInstanceHandle
    ) = cppDeleteViewModelInstance(
        cppPointer.pointer,
        nextRequestID.getAndIncrement(),
        viewModelInstanceHandle.handle
    )

    /**
     * Bind a view model instance to a state machine. This establishes the data binding for the
     * instance's properties.
     *
     * @param stateMachineHandle The handle of the state machine to bind to.
     * @param viewModelInstanceHandle The handle of the view model instance to bind.
     * @throws RuntimeException If the state machine handle or view model instance handle is
     *    invalid.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(RuntimeException::class, IllegalStateException::class)
    fun bindViewModelInstance(
        stateMachineHandle: StateMachineHandle,
        viewModelInstanceHandle: ViewModelInstanceHandle
    ) = cppBindViewModelInstance(
        cppPointer.pointer,
        nextRequestID.getAndIncrement(),
        stateMachineHandle.handle,
        viewModelInstanceHandle.handle
    )

    /**
     * Helper function to template property updates for all types.
     *
     * @param requestID The request ID used to identify the property update.
     * @param viewModelInstanceHandle The handle of the view model instance that the property
     *    belongs to.
     * @param propertyName The name of the property that was updated.
     * @param value The new value of the property.
     * @param flow The flow to emit the property update to.
     */
    private fun <T> onPropertyUpdated(
        requestID: Long,
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyName: String,
        value: T,
        flow: MutableSharedFlow<PropertyUpdate<T>>
    ) {
        flow.tryEmit(PropertyUpdate(viewModelInstanceHandle, propertyName, value))
        pendingContinuations.remove(requestID)?.resume(value as Any)
    }

    /**
     * Update a number property's value on the view model instance.
     *
     * @param viewModelInstanceHandle The handle of the view model instance that the property
     *    belongs to.
     * @param propertyPath The path to the property that should be updated. Slash delimited.
     * @param value The new value of the property.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun setNumberProperty(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String,
        value: Float
    ) = cppSetNumberProperty(
        cppPointer.pointer,
        viewModelInstanceHandle.handle,
        propertyPath,
        value
    )

    /**
     * Get a number property's value on the view model instance.
     *
     * @param viewModelInstanceHandle The handle of the view model instance that the property
     *    belongs to.
     * @param propertyPath The path to the property that should be retrieved. Slash delimited.
     * @return The value of the property.
     * @throws RuntimeException If the view model instance handle is invalid, or the named property
     *    does not exist or is of the wrong type.
     * @throws IllegalStateException If the CommandQueue has been released.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     */
    @Throws(RuntimeException::class, IllegalStateException::class, CancellationException::class)
    suspend fun getNumberProperty(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String
    ): Float = suspendNativeRequest { requestID ->
        cppGetNumberProperty(
            cppPointer.pointer,
            requestID,
            viewModelInstanceHandle.handle,
            propertyPath
        )
    }

    /**
     * Callback when a number property is updated, from [getNumberProperty] or [subscribeToProperty]
     * updates.
     *
     * @param requestID The request ID used when updating the property, used to complete the
     *    continuation.
     * @param viewModelInstanceHandle The handle of the view model instance that the property
     *    belongs to.
     * @param propertyName The name of the property that was updated.
     * @param value The value of the property.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onNumberPropertyUpdated")
    internal fun onNumberPropertyUpdated(
        requestID: Long,
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyName: String,
        value: Float
    ) = onPropertyUpdated(
        requestID,
        viewModelInstanceHandle,
        propertyName,
        value,
        _numberPropertyFlow
    )

    /**
     * Set a string property's value on the view model instance.
     *
     * @param viewModelInstanceHandle The handle of the view model instance that the property
     *    belongs to.
     * @param propertyPath The path to the property that should be updated. Slash delimited.
     * @param value The new value of the property.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun setStringProperty(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String,
        value: String
    ) = cppSetStringProperty(
        cppPointer.pointer,
        viewModelInstanceHandle.handle,
        propertyPath,
        value
    )

    /**
     * Get a string property's value on the view model instance.
     *
     * @param viewModelInstanceHandle The handle of the view model instance that the property
     *    belongs to.
     * @param propertyPath The path to the property that should be retrieved. Slash delimited.
     * @return The value of the property.
     * @throws RuntimeException If the view model instance handle is invalid, or the named property
     *    does not exist or is of the wrong type.
     * @throws IllegalStateException If the CommandQueue has been released.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     */
    @Throws(RuntimeException::class, IllegalStateException::class, CancellationException::class)
    suspend fun getStringProperty(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String
    ): String = suspendNativeRequest { requestID ->
        cppGetStringProperty(
            cppPointer.pointer,
            requestID,
            viewModelInstanceHandle.handle,
            propertyPath
        )
    }

    /**
     * Callback when a string property is updated, from [getStringProperty] or [subscribeToProperty]
     * updates.
     *
     * @param requestID The request ID used when updating the property, used to complete the
     *    continuation.
     * @param viewModelInstanceHandle The handle of the view model instance that the property
     *    belongs to.
     * @param propertyName The name of the property that was updated.
     * @param value The value of the property.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onStringPropertyUpdated")
    internal fun onStringPropertyUpdated(
        requestID: Long,
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyName: String,
        value: String
    ) = onPropertyUpdated(
        requestID,
        viewModelInstanceHandle,
        propertyName,
        value,
        _stringPropertyFlow
    )

    /**
     * Set a boolean property's value on the view model instance.
     *
     * @param viewModelInstanceHandle The handle of the view model instance that the property
     *    belongs to.
     * @param propertyPath The path to the property that should be updated. Slash delimited.
     * @param value The new value of the property.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun setBooleanProperty(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String,
        value: Boolean
    ) = cppSetBooleanProperty(
        cppPointer.pointer,
        viewModelInstanceHandle.handle,
        propertyPath,
        value
    )

    /**
     * Get a boolean property's value on the view model instance.
     *
     * @param viewModelInstanceHandle The handle of the view model instance that the property
     *    belongs to.
     * @param propertyPath The path to the property that should be retrieved. Slash delimited.
     * @return The value of the property.
     * @throws RuntimeException If the view model instance handle is invalid, or the named property
     *    does not exist or is of the wrong type.
     * @throws IllegalStateException If the CommandQueue has been released.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     */
    @Throws(RuntimeException::class, IllegalStateException::class, CancellationException::class)
    suspend fun getBooleanProperty(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String
    ): Boolean = suspendNativeRequest { requestID ->
        cppGetBooleanProperty(
            cppPointer.pointer,
            requestID,
            viewModelInstanceHandle.handle,
            propertyPath
        )
    }

    /**
     * Callback when a boolean property is updated, from [getBooleanProperty] or
     * [subscribeToProperty] updates.
     *
     * @param requestID The request ID used when updating the property, used to complete the
     *    continuation.
     * @param viewModelInstanceHandle The handle of the view model instance that the property
     *    belongs to.
     * @param propertyName The name of the property that was updated.
     * @param value The value of the property.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onBooleanPropertyUpdated")
    internal fun onBooleanPropertyUpdated(
        requestID: Long,
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyName: String,
        value: Boolean
    ) = onPropertyUpdated(
        requestID,
        viewModelInstanceHandle,
        propertyName,
        value,
        _booleanPropertyFlow
    )

    /**
     * Set an enum property's value on the view model instance.
     *
     * @param viewModelInstanceHandle The handle of the view model instance that the property
     *    belongs to.
     * @param propertyPath The path to the property that should be updated. Slash delimited.
     * @param value The new value of the property, as a string.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun setEnumProperty(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String,
        value: String
    ) = cppSetEnumProperty(
        cppPointer.pointer,
        viewModelInstanceHandle.handle,
        propertyPath,
        value
    )

    /**
     * Get an enum property's value on the view model instance.
     *
     * @param viewModelInstanceHandle The handle of the view model instance that the property
     *    belongs to.
     * @param propertyPath The path to the property that should be retrieved. Slash delimited.
     * @return The value of the property, as a string.
     * @throws RuntimeException If the view model instance handle is invalid, or the named property
     *    does not exist or is of the wrong type.
     * @throws IllegalStateException If the CommandQueue has been released.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     */
    @Throws(RuntimeException::class, IllegalStateException::class, CancellationException::class)
    suspend fun getEnumProperty(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String
    ): String = suspendNativeRequest { requestID ->
        cppGetEnumProperty(
            cppPointer.pointer,
            requestID,
            viewModelInstanceHandle.handle,
            propertyPath
        )
    }

    /**
     * Callback when an enum property is updated, from [getEnumProperty] or [subscribeToProperty]
     * updates.
     *
     * @param requestID The request ID used when updating the property, used to complete the
     *    continuation.
     * @param viewModelInstanceHandle The handle of the view model instance that the property
     *    belongs to.
     * @param propertyName The name of the property that was updated.
     * @param value The value of the property, as a string.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onEnumPropertyUpdated")
    internal fun onEnumPropertyUpdated(
        requestID: Long,
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyName: String,
        value: String
    ) = onPropertyUpdated(
        requestID,
        viewModelInstanceHandle,
        propertyName,
        value,
        _enumPropertyFlow
    )

    /**
     * Set a color property's value on the view model instance.
     *
     * @param viewModelInstanceHandle The handle of the view model instance that the property
     *    belongs to.
     * @param propertyPath The path to the property that should be updated. Slash delimited.
     * @param value The new value of the property, as an AARRGGBB [ColorInt].
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun setColorProperty(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String,
        @ColorInt value: Int
    ) = cppSetColorProperty(
        cppPointer.pointer,
        viewModelInstanceHandle.handle,
        propertyPath,
        value
    )

    /**
     * Get a color property's value on the view model instance.
     *
     * @param viewModelInstanceHandle The handle of the view model instance that the property
     *    belongs to.
     * @param propertyPath The path to the property that should be retrieved. Slash delimited.
     * @return The value of the property, as an AARRGGBB [ColorInt].
     * @throws RuntimeException If the view model instance handle is invalid, or the named property
     *    does not exist or is of the wrong type.
     * @throws IllegalStateException If the CommandQueue has been released.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     */
    @Throws(RuntimeException::class, IllegalStateException::class, CancellationException::class)
    suspend fun getColorProperty(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String
    ): Int = suspendNativeRequest { requestID ->
        cppGetColorProperty(
            cppPointer.pointer,
            requestID,
            viewModelInstanceHandle.handle,
            propertyPath
        )
    }

    /**
     * Callback when a color property is updated, from [getColorProperty] or [subscribeToProperty]
     * updates.
     *
     * @param requestID The request ID used when updating the property, used to complete the
     *    continuation.
     * @param viewModelInstanceHandle The handle of the view model instance that the property
     *    belongs to.
     * @param propertyName The name of the property that was updated.
     * @param value The value of the property, as an AARRGGBB [ColorInt].
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onColorPropertyUpdated")
    internal fun onColorPropertyUpdated(
        requestID: Long,
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyName: String,
        @ColorInt value: Int
    ) = onPropertyUpdated(
        requestID,
        viewModelInstanceHandle,
        propertyName,
        value,
        _colorPropertyFlow
    )

    /**
     * Fire a trigger property on the view model instance.
     *
     * @param viewModelInstanceHandle The handle of the view model instance that the property
     *    belongs to.
     * @param propertyPath The path to the property that should be fired. Slash delimited.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun fireTriggerProperty(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String
    ) = cppFireTriggerProperty(
        cppPointer.pointer,
        viewModelInstanceHandle.handle,
        propertyPath
    )

    /**
     * Callback when a trigger property is updated, from [subscribeToProperty] updates.
     *
     * @param requestID The request ID used when updating the property, used to complete the
     *    continuation.
     * @param viewModelInstanceHandle The handle of the view model instance that the property
     *    belongs to.
     * @param propertyName The name of the property that was updated.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onTriggerPropertyUpdated")
    internal fun onTriggerPropertyUpdated(
        requestID: Long,
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyName: String
    ) = onPropertyUpdated(
        requestID,
        viewModelInstanceHandle,
        propertyName,
        Unit,
        _triggerPropertyFlow
    )

    /**
     * Subscribe to changes to a property on the view model instance. Updates will be emitted on the
     * flow of the corresponding type, e.g. [numberPropertyFlow] for number properties.
     *
     * @param viewModelInstanceHandle The handle of the view model instance that the property
     *    belongs to.
     * @param propertyPath The path to the property that should be subscribed to. Slash delimited.
     * @param propertyType The type of the property to subscribe to.
     * @throws RuntimeException If the property type is invalid.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun subscribeToProperty(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String,
        propertyType: ViewModel.PropertyDataType
    ) = cppSubscribeToProperty(
        cppPointer.pointer,
        viewModelInstanceHandle.handle,
        propertyPath,
        propertyType.value
    )

    /**
     * Assign an image to an image property on the view model instance.
     *
     * @param viewModelInstanceHandle The handle of the view model instance that the property
     *    belongs to.
     * @param propertyPath The path to the property that should be assigned to. Slash delimited.
     * @param imageHandle The handle of the image to assign.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun setImageProperty(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String,
        imageHandle: ImageHandle
    ) = cppSetImageProperty(
        cppPointer.pointer,
        viewModelInstanceHandle.handle,
        propertyPath,
        imageHandle.handle
    )

    /**
     * Assign an artboard to a bindable artboard property on the view model instance.
     *
     * @param viewModelInstanceHandle The handle of the view model instance that the property
     *    belongs to.
     * @param propertyPath The path to the property that should be assigned to. Slash delimited.
     * @param artboardHandle The handle of the artboard to assign.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun setArtboardProperty(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String,
        artboardHandle: ArtboardHandle
    ) = cppSetArtboardProperty(
        cppPointer.pointer,
        viewModelInstanceHandle.handle,
        propertyPath,
        artboardHandle.handle
    )

    /**
     * Gets the size of a list property on the view model instance.
     *
     * @param viewModelInstanceHandle The handle of the view model instance that the property
     *    belongs to.
     * @param propertyPath The path to the property that should be retrieved. Slash delimited.
     * @return The size of the list.
     * @throws RuntimeException If the view model instance handle is invalid, or the named property
     *    does not exist or is of the wrong type.
     * @throws IllegalStateException If the CommandQueue has been released.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     */
    @Throws(RuntimeException::class, IllegalStateException::class, CancellationException::class)
    suspend fun getListSize(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String
    ): Int = suspendNativeRequest { requestID ->
        cppGetListSize(
            cppPointer.pointer,
            requestID,
            viewModelInstanceHandle.handle,
            propertyPath
        )
    }

    /**
     * Callback when the list size is retrieved, from [getListSize].
     *
     * @param requestID The request ID used when querying the list size, used to complete the
     *    continuation.
     * @param size The size of the list.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onViewModelListSizeReceived")
    internal fun onViewModelListSizeReceived(requestID: Long, size: Int) {
        (pendingContinuations.remove(requestID) as? Continuation<Int>)?.resume(size)
    }

    /**
     * Inserts a view model instance into a list property at the specified index.
     *
     * @param viewModelInstanceHandle The handle of the view model instance that owns the list
     *    property.
     * @param propertyPath The path to the list property. Slash delimited.
     * @param index The index at which to insert the item.
     * @param itemHandle The handle of the view model instance to insert into the list.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun insertToListAtIndex(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String,
        index: Int,
        itemHandle: ViewModelInstanceHandle
    ) = cppInsertToListAtIndex(
        cppPointer.pointer,
        viewModelInstanceHandle.handle,
        propertyPath,
        index,
        itemHandle.handle
    )

    /**
     * Appends a view model instance to the end of a list property.
     *
     * @param viewModelInstanceHandle The handle of the view model instance that owns the list
     *    property.
     * @param propertyPath The path to the list property. Slash delimited.
     * @param itemHandle The handle of the view model instance to append to the list.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun appendToList(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String,
        itemHandle: ViewModelInstanceHandle
    ) = cppAppendToList(
        cppPointer.pointer,
        viewModelInstanceHandle.handle,
        propertyPath,
        itemHandle.handle
    )

    /**
     * Removes an item from a list property at the specified index.
     *
     * @param viewModelInstanceHandle The handle of the view model instance that owns the list
     *    property.
     * @param propertyPath The path to the list property. Slash delimited.
     * @param index The index of the item to remove.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun removeFromListAtIndex(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String,
        index: Int
    ) = cppRemoveFromListAtIndex(
        cppPointer.pointer,
        viewModelInstanceHandle.handle,
        propertyPath,
        index
    )

    /**
     * Removes a view model instance from a list property.
     *
     * @param viewModelInstanceHandle The handle of the view model instance that owns the list
     *    property.
     * @param propertyPath The path to the list property. Slash delimited.
     * @param itemHandle The handle of the view model instance to remove from the list.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun removeFromList(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String,
        itemHandle: ViewModelInstanceHandle
    ) = cppRemoveFromList(
        cppPointer.pointer,
        viewModelInstanceHandle.handle,
        propertyPath,
        itemHandle.handle
    )

    /**
     * Swaps two items in a list property by their indices.
     *
     * @param viewModelInstanceHandle The handle of the view model instance that owns the list
     *    property.
     * @param propertyPath The path to the list property. Slash delimited.
     * @param indexA The index of the first item to swap.
     * @param indexB The index of the second item to swap.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun swapListItems(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String,
        indexA: Int,
        indexB: Int
    ) = cppSwapListItems(
        cppPointer.pointer,
        viewModelInstanceHandle.handle,
        propertyPath,
        indexA,
        indexB
    )

    /**
     * Decode an image file from the given bytes. The bytes are for a compressed image format such
     * as PNG or JPEG. The decoded image is stored on the CommandServer.
     *
     * Images may be used to supply a referenced asset in a Rive file with [registerImage] or used
     * for data binding (forthcoming).
     *
     * @param bytes The bytes of the image file to decode.
     * @return A handle to the decoded image.
     * @throws RuntimeException If the image could not be decoded, e.g. if the bytes are not a valid
     *    image.
     * @throws IllegalStateException If the CommandQueue has been released.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     */
    @Throws(RuntimeException::class, IllegalStateException::class, CancellationException::class)
    suspend fun decodeImage(bytes: ByteArray): ImageHandle = suspendNativeRequest { requestID ->
        cppDecodeImage(cppPointer.pointer, requestID, bytes)
    }

    /**
     * Callback when an image is decoded, from [decodeImage].
     *
     * @param requestID The request ID used when decoding the image, used to complete the
     *    continuation.
     * @param imageHandle The handle of the decoded image.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onImageDecoded")
    internal fun onImageDecoded(requestID: Long, imageHandle: ImageHandle) {
        (pendingContinuations.remove(requestID) as? Continuation<ImageHandle>)?.resume(imageHandle)
    }

    /**
     * Callback when an image fails to decode, from [decodeImage]. This will resume the continuation
     * with an exception.
     *
     * @param requestID The request ID used when decoding the image, used to complete the
     *    continuation.
     * @param error The error message describing the failure.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onImageError")
    internal fun onImageError(requestID: Long, error: String) {
        (pendingContinuations.remove(requestID) as? Continuation<ImageHandle>)?.resumeWithException(
            RuntimeException("Failed to decode image: $error")
        )
    }

    /**
     * Delete an image and free its resources. This is useful when you no longer need the image and
     * want to free up memory. Counterpart to [decodeImage].
     *
     * @param imageHandle The handle of the image to delete.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun deleteImage(imageHandle: ImageHandle) =
        cppDeleteImage(cppPointer.pointer, imageHandle.handle)

    /**
     * Register an image as an asset with the given name. This allows the image to be used to
     * fulfill a referenced asset when loading a Rive file.
     *
     * The CommandServer will keep a reference to the image. For it to be fully released, you must
     * unregister it with [unregisterImage].
     *
     * Registrations are global to the CommandQueue, meaning that the [name] will be used to fulfill
     * any file loaded by this CommandQueue that references the asset with the same name.
     *
     * The same image can be registered multiple times with different names, allowing it to fulfill
     * multiple referenced assets in all Rive files on this CommandQueue.
     *
     * @param name The name of the referenced asset to fulfill. Must match the name in the zip file
     *    when exporting from Rive.
     * @param imageHandle The handle of the image to register.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun registerImage(name: String, imageHandle: ImageHandle) =
        cppRegisterImage(cppPointer.pointer, name, imageHandle.handle)

    /**
     * Unregister an image that was previously registered with [registerImage]. This will remove the
     * reference to the image from the CommandServer, allowing the memory to be freed if the image
     * handle was also deleted with [deleteImage].
     *
     * @param name The name of the referenced asset to unregister, the same used in [registerImage].
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun unregisterImage(name: String) =
        cppUnregisterImage(cppPointer.pointer, name)

    /**
     * Decode an audio file from the given bytes. The decoded audio is stored on the CommandServer.
     *
     * Audio may be used to supply a referenced asset in a Rive file with [registerAudio].
     *
     * @param bytes The bytes of the audio file to decode.
     * @return A handle to the decoded audio.
     * @throws RuntimeException If the audio could not be decoded, e.g. if the bytes are not a valid
     *    audio file.
     * @throws IllegalStateException If the CommandQueue has been released.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     */
    @Throws(RuntimeException::class, IllegalStateException::class, CancellationException::class)
    suspend fun decodeAudio(bytes: ByteArray): AudioHandle = suspendNativeRequest { requestID ->
        cppDecodeAudio(cppPointer.pointer, requestID, bytes)
    }

    /**
     * Callback when audio is decoded, from [decodeAudio].
     *
     * @param requestID The request ID used when decoding the audio, used to complete the
     *    continuation.
     * @param audioHandle The handle of the decoded audio.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onAudioDecoded")
    internal fun onAudioDecoded(requestID: Long, audioHandle: AudioHandle) {
        (pendingContinuations.remove(requestID) as? Continuation<AudioHandle>)?.resume(audioHandle)
    }

    /**
     * Callback when audio fails to decode, from [decodeAudio]. This will resume the continuation
     * with an exception.
     *
     * @param requestID The request ID used when decoding the audio, used to complete the
     *    continuation.
     * @param error The error message describing the failure.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onAudioError")
    internal fun onAudioError(requestID: Long, error: String) {
        (pendingContinuations.remove(requestID) as? Continuation<AudioHandle>)?.resumeWithException(
            RuntimeException("Failed to decode audio: $error")
        )
    }

    /**
     * Delete audio and free its resources. This is useful when you no longer need the audio and
     * want to free up memory. Counterpart to [decodeAudio].
     *
     * @param audioHandle The handle of the audio to delete.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun deleteAudio(audioHandle: AudioHandle) =
        cppDeleteAudio(cppPointer.pointer, audioHandle.handle)

    /**
     * Register audio as an asset with the given name. This allows the audio to be used to fulfill a
     * referenced asset when loading a Rive file.
     *
     * The CommandServer will keep a reference to the audio. For it to be fully released, you must
     * unregister it with [unregisterAudio].
     *
     * Registrations are global to the CommandQueue, meaning that the [name] will be used to fulfill
     * any file loaded by this CommandQueue that references the asset with the same name.
     *
     * The same audio can be registered multiple times with different names, allowing it to fulfill
     * multiple referenced assets in all Rive files on this CommandQueue.
     *
     * @param name The name of the referenced asset to fulfill. Must match the name in the zip file
     *    when exporting from Rive.
     * @param audioHandle The handle of the audio to register.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun registerAudio(name: String, audioHandle: AudioHandle) =
        cppRegisterAudio(cppPointer.pointer, name, audioHandle.handle)

    /**
     * Unregister audio that was previously registered with [registerAudio]. This will remove the
     * reference to the audio from the CommandServer, allowing the memory to be freed if the audio
     * handle was also deleted with [deleteAudio].
     *
     * @param name The name of the referenced asset to unregister, the same used in [registerAudio].
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun unregisterAudio(name: String) =
        cppUnregisterAudio(cppPointer.pointer, name)

    /**
     * Decode a font file from the given bytes. The bytes are for a font file, such as TTF. The
     * decoded font is stored on the CommandServer.
     *
     * Fonts may be used to supply a referenced asset in a Rive file with [registerFont].
     *
     * @param bytes The bytes of the font file to decode.
     * @return A handle to the decoded font.
     * @throws RuntimeException If the font could not be decoded, e.g. if the bytes are not a valid
     *    font.
     * @throws IllegalStateException If the CommandQueue has been released.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     */
    @Throws(RuntimeException::class, IllegalStateException::class, CancellationException::class)
    suspend fun decodeFont(bytes: ByteArray): FontHandle = suspendNativeRequest { requestID ->
        cppDecodeFont(cppPointer.pointer, requestID, bytes)
    }

    /**
     * Callback when a font is decoded, from [decodeFont].
     *
     * @param requestID The request ID used when decoding the font, used to complete the
     *    continuation.
     * @param fontHandle The handle of the decoded font.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onFontDecoded")
    internal fun onFontDecoded(requestID: Long, fontHandle: FontHandle) {
        (pendingContinuations.remove(requestID) as? Continuation<FontHandle>)?.resume(fontHandle)
    }

    /**
     * Callback when a font fails to decode, from [decodeFont]. This will resume the continuation
     * with an exception.
     *
     * @param requestID The request ID used when decoding the font, used to complete the
     *    continuation.
     * @param error The error message describing the failure.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onFontError")
    internal fun onFontError(requestID: Long, error: String) {
        (pendingContinuations.remove(requestID) as? Continuation<FontHandle>)?.resumeWithException(
            RuntimeException("Failed to decode font: $error")
        )
    }

    /**
     * Delete a font and free its resources. This is useful when you no longer need the font and
     * want to free up memory. Counterpart to [decodeFont].
     *
     * @param fontHandle The handle of the font to delete.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun deleteFont(fontHandle: FontHandle) =
        cppDeleteFont(cppPointer.pointer, fontHandle.handle)

    /**
     * Register a font as an asset with the given name. This allows the font to be used to fulfill a
     * referenced asset when loading a Rive file.
     *
     * The CommandServer will keep a reference to the font. For it to be fully released, you must
     * unregister it with [unregisterFont].
     *
     * Registrations are global to the CommandQueue, meaning that the [name] will be used to fulfill
     * any file loaded by this CommandQueue that references the asset with the same name.
     *
     * The same font can be registered multiple times with different names, allowing it to fulfill
     * multiple referenced assets in all Rive files on this CommandQueue.
     *
     * @param name The name of the referenced asset to fulfill. Must match the name in the zip file
     *    when exporting from Rive.
     * @param fontHandle The handle of the font to register.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun registerFont(name: String, fontHandle: FontHandle) =
        cppRegisterFont(cppPointer.pointer, name, fontHandle.handle)

    /**
     * Unregister a font that was previously registered with [registerFont]. This will remove the
     * reference to the font from the CommandServer, allowing the memory to be freed if the font
     * handle was also deleted with [deleteFont].
     *
     * @param name The name of the referenced asset to unregister, the same used in [registerFont].
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun unregisterFont(name: String) =
        cppUnregisterFont(cppPointer.pointer, name)

    /**
     * Notify the state machine that the pointer (typically a user's touch) has moved. This is used
     * to interact with the state machine, triggering pointer events.
     *
     * The additional parameters are required for calculating the pointer position in artboard
     * space.
     *
     * @param stateMachineHandle The handle of the state machine to notify.
     * @param fit The fit mode of the artboard.
     * @param surfaceWidth The width of the surface the artboard is drawn to.
     * @param surfaceHeight The height of the surface the artboard is drawn to.
     * @param pointerX The X coordinate of the pointer in surface space.
     * @param pointerY The Y coordinate of the pointer in surface space.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun pointerMove(
        stateMachineHandle: StateMachineHandle,
        fit: Fit,
        surfaceWidth: Float,
        surfaceHeight: Float,
        pointerID: Int,
        pointerX: Float,
        pointerY: Float
    ) = cppPointerMove(
        cppPointer.pointer,
        stateMachineHandle.handle,
        fit.nativeMapping,
        fit.alignment.nativeMapping,
        fit.scaleFactor,
        surfaceWidth,
        surfaceHeight,
        pointerID,
        pointerX,
        pointerY
    )

    /**
     * Notify the state machine that the pointer (typically a user's touch) has touched down. This
     * is used to interact with the state machine, triggering pointer events.
     *
     * The additional parameters are required for calculating the pointer position in artboard
     * space.
     *
     * @param stateMachineHandle The handle of the state machine to notify.
     * @param fit The fit mode of the artboard.
     * @param surfaceWidth The width of the surface the artboard is drawn to.
     * @param surfaceHeight The height of the surface the artboard is drawn to.
     * @param pointerX The X coordinate of the pointer in surface space.
     * @param pointerY The Y coordinate of the pointer in surface space.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun pointerDown(
        stateMachineHandle: StateMachineHandle,
        fit: Fit,
        surfaceWidth: Float,
        surfaceHeight: Float,
        pointerID: Int,
        pointerX: Float,
        pointerY: Float
    ) = cppPointerDown(
        cppPointer.pointer,
        stateMachineHandle.handle,
        fit.nativeMapping,
        fit.alignment.nativeMapping,
        fit.scaleFactor,
        surfaceWidth,
        surfaceHeight,
        pointerID,
        pointerX,
        pointerY
    )

    /**
     * Notify the state machine that the pointer (typically a user's touch) has lifted up. This is
     * used to interact with the state machine, triggering pointer events.
     *
     * The additional parameters are required for calculating the pointer position in artboard
     * space.
     *
     * @param stateMachineHandle The handle of the state machine to notify.
     * @param fit The fit mode of the artboard.
     * @param surfaceWidth The width of the surface the artboard is drawn to.
     * @param surfaceHeight The height of the surface the artboard is drawn to.
     * @param pointerX The X coordinate of the pointer in surface space.
     * @param pointerY The Y coordinate of the pointer in surface space.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun pointerUp(
        stateMachineHandle: StateMachineHandle,
        fit: Fit,
        surfaceWidth: Float,
        surfaceHeight: Float,
        pointerID: Int,
        pointerX: Float,
        pointerY: Float
    ) = cppPointerUp(
        cppPointer.pointer,
        stateMachineHandle.handle,
        fit.nativeMapping,
        fit.alignment.nativeMapping,
        fit.scaleFactor,
        surfaceWidth,
        surfaceHeight,
        pointerID,
        pointerX,
        pointerY
    )

    /**
     * Notify the state machine that the pointer (typically a user's touch) has exited the surface.
     * This is used to interact with the state machine, triggering pointer events.
     *
     * The additional parameters are required for calculating the pointer position in artboard
     * space.
     *
     * @param stateMachineHandle The handle of the state machine to notify.
     * @param fit The fit mode of the artboard.
     * @param surfaceWidth The width of the surface the artboard is drawn to.
     * @param surfaceHeight The height of the surface the artboard is drawn to.
     * @param pointerX The X coordinate of the pointer in surface space.
     * @param pointerY The Y coordinate of the pointer in surface space.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun pointerExit(
        stateMachineHandle: StateMachineHandle,
        fit: Fit,
        surfaceWidth: Float,
        surfaceHeight: Float,
        pointerID: Int,
        pointerX: Float,
        pointerY: Float
    ) = cppPointerExit(
        cppPointer.pointer,
        stateMachineHandle.handle,
        fit.nativeMapping,
        fit.alignment.nativeMapping,
        fit.scaleFactor,
        surfaceWidth,
        surfaceHeight,
        pointerID,
        pointerX,
        pointerY
    )

    /**
     * Resizes an artboard to match the dimensions of the given surface.
     *
     * ℹ️ This is required when setting the fit type to [Fit.Layout], where the artboard is expected
     * to match the dimensions of the surface it is drawn to and layout its children within those
     * bounds.
     *
     * @param artboardHandle The handle of the artboard to resize.
     * @param surface The surface whose width and height will be used to resize the artboard.
     * @param scaleFactor The scale factor to apply when resizing. The artboard will be resized to
     *    surface dimensions divided by this factor.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun resizeArtboard(
        artboardHandle: ArtboardHandle,
        surface: RiveSurface,
        scaleFactor: Float = 1f
    ) = cppResizeArtboard(
        cppPointer.pointer,
        artboardHandle.handle,
        surface.width,
        surface.height,
        scaleFactor
    )

    /**
     * Resets an artboard to its original dimensions.
     *
     * ℹ️ This should be called if the artboard was previously resized with [resizeArtboard] and you
     * now have a fit type other than [Fit.Layout], to restore the artboard to its original size.
     *
     * @param artboardHandle The handle of the artboard to reset.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun resetArtboardSize(
        artboardHandle: ArtboardHandle
    ) = cppResetArtboardSize(
        cppPointer.pointer,
        artboardHandle.handle
    )

    /**
     * Draw the artboard with the given state machine.
     *
     * @param artboardHandle The handle of the artboard to draw.
     * @param stateMachineHandle The handle of the state machine to use for drawing.
     * @param surface The surface to draw to.
     * @param fit The fit mode of the artboard.
     * @param clearColor The color to clear the surface with before drawing, in AARRGGBB format.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    fun draw(
        artboardHandle: ArtboardHandle,
        stateMachineHandle: StateMachineHandle,
        surface: RiveSurface,
        fit: Fit,
        clearColor: Int = Color.TRANSPARENT
    ) = cppDraw(
        cppPointer.pointer,
        renderContext.nativeObjectPointer,
        surface.surfaceNativePointer,
        surface.drawKey.handle,
        artboardHandle.handle,
        stateMachineHandle.handle,
        surface.renderTargetPointer.pointer,
        surface.width,
        surface.height,
        fit.nativeMapping,
        fit.alignment.nativeMapping,
        fit.scaleFactor,
        clearColor
    )

    /**
     * Renders the current state of an artboard/state machine pair into an off-screen buffer that
     * can be used for snapshot comparisons.
     *
     * ⚠️ Unlike other CommandQueue methods, this method is synchronous and blocks the calling
     * thread.
     *
     * @param artboardHandle The handle of the artboard to draw.
     * @param stateMachineHandle The handle of the state machine to advance/draw.
     * @param surface The surface to draw to, likely created from [createImageSurface].
     * @param buffer The byte array buffer to render into. Must be at least width * height * 4 bytes
     *    in size.
     * @param width The width of the buffer to render.
     * @param height The height of the buffer to render.
     * @param fit Fit to use when drawing.
     * @param clearColor Clear color used prior to drawing, defaults to transparent.
     * @throws RiveDrawToBufferException If the buffer could not be drawn to for any reason. Further
     *    details can be found in the exception message.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(RiveDrawToBufferException::class, IllegalStateException::class)
    fun drawToBuffer(
        artboardHandle: ArtboardHandle,
        stateMachineHandle: StateMachineHandle,
        surface: RiveSurface,
        buffer: ByteArray,
        width: Int,
        height: Int,
        fit: Fit = Fit.Contain(),
        clearColor: Int = Color.TRANSPARENT
    ) = cppDrawToBuffer(
        cppPointer.pointer,
        renderContext.nativeObjectPointer,
        surface.surfaceNativePointer,
        surface.drawKey.handle,
        artboardHandle.handle,
        stateMachineHandle.handle,
        surface.renderTargetPointer.pointer,
        width,
        height,
        fit.nativeMapping,
        fit.alignment.nativeMapping,
        fit.scaleFactor,
        clearColor,
        buffer
    )

    /**
     * Enqueue arbitrary Kotlin code to be run on the command server thread.
     *
     * This allows executing Kotlin-side logic in the context of the command server thread. This can
     * be useful for operations that need to be queued orderly with other commands, such as freeing
     * resources that should only be freed after prior commands using them have completed.
     *
     * The operation is fire-and-forget, so no response is expected.
     *
     * @param work The lambda to execute on the command server thread.
     * @throws IllegalStateException If the CommandQueue has been released.
     */
    @Throws(IllegalStateException::class)
    private fun runOnCommandServer(work: () -> Unit) =
        cppRunOnCommandServer(cppPointer.pointer, work)

    /**
     * The map of all pending continuations, keyed by request ID. Entries are added when a suspend
     * request is made, and removed when the request is completed.
     *
     * @see [suspendNativeRequest]
     */
    private val pendingContinuations = ConcurrentHashMap<Long, CancellableContinuation<Any>>()

    /**
     * A monotonically increasing request ID used to identify JNI requests. This allows pairing
     * outgoing requests with incoming callbacks.
     *
     * @see [suspendNativeRequest]
     */
    private val nextRequestID = AtomicLong()

    /**
     * Make a JNI request that returns a value of type [T], split across a callback.
     * The native function is called with a new, monotonically increasing request ID.
     * [suspendCancellableCoroutine] is used to suspend the calling coroutine until the native
     * callback returns. This generates a "continuation" that is stored in a map based on the
     * request ID. When the native callback is invoked, it retrieves the continuation from the map
     * and resumes it with the result.
     *
     * We switch to the Main dispatcher before running. This is because the C++ CommandQueue
     * implementation is currently not thread-safe, and so must always be called from the main
     * thread. If this changes in the future, this can be removed.
     *
     * @param nativeFn A lambda function invoked by the caller, using trailing lambda syntax, which
     *    takes the request ID as a parameter and performs the JNI function call.
     * @return The result of the JNI function call.
     * @throws CancellationException If the coroutine is cancelled before the native callback
     *    returns, the continuation is removed from the map and a [CancellationException] is thrown.
     */
    @Throws(CancellationException::class)
    private suspend inline fun <reified T> suspendNativeRequest(
        crossinline nativeFn: (Long) -> Unit
    ): T = withContext(Dispatchers.Main.immediate) { // Ensure we're on the main thread
        suspendCancellableCoroutine { cont ->
            val requestID = nextRequestID.getAndIncrement()

            // Store the continuation
            @Suppress("UNCHECKED_CAST")
            pendingContinuations[requestID] = cont as CancellableContinuation<Any>

            cont.invokeOnCancellation {
                pendingContinuations.remove(requestID)
            }

            nativeFn(requestID)
        }
    }
}

/**
 * A handle to a Rive file on the CommandServer. Created with [CommandQueue.loadFile] and deleted
 * with [CommandQueue.deleteFile].
 *
 * @param handle The handle issued by the native CommandQueue.
 */
@JvmInline
value class FileHandle(val handle: Long) {
    override fun toString(): String = "FileHandle($handle)"
}

/**
 * A handle to an artboard instance on the CommandServer. Created with
 * [CommandQueue.createDefaultArtboard] or [CommandQueue.createArtboardByName]
 * and deleted with [CommandQueue.deleteArtboard].
 *
 * @param handle The handle issued by the native CommandQueue.
 */
@JvmInline
value class ArtboardHandle(val handle: Long) {
    override fun toString(): String = "ArtboardHandle($handle)"
}

/**
 * A handle to a state machine instance on the CommandServer. Created with
 * [CommandQueue.createDefaultStateMachine] or [CommandQueue.createStateMachineByName]
 * and deleted with [CommandQueue.deleteStateMachine].
 *
 * @param handle The handle issued by the native CommandQueue.
 */
@JvmInline
value class StateMachineHandle(val handle: Long) {
    override fun toString(): String = "StateMachineHandle($handle)"
}

/**
 * A handle to a view model instance on the CommandServer. Created with
 * [CommandQueue.createViewModelInstance] and deleted with [CommandQueue.deleteViewModelInstance].
 *
 * @param handle The handle issued by the native CommandQueue.
 */
@JvmInline
value class ViewModelInstanceHandle(val handle: Long) {
    override fun toString(): String = "ViewModelInstanceHandle($handle)"
}

/**
 * A handle to a RenderImage on the CommandServer. Created with [CommandQueue.decodeImage] and
 * deleted with [CommandQueue.deleteImage].
 *
 * @param handle The handle issued by the native CommandQueue.
 */
@JvmInline
value class ImageHandle(val handle: Long) {
    override fun toString(): String = "ImageHandle($handle)"
}

/**
 * A handle to a AudioSource on the CommandServer. Created with [CommandQueue.decodeAudio] and
 * deleted with [CommandQueue.deleteAudio].
 *
 * @param handle The handle issued by the native CommandQueue.
 */
@JvmInline
value class AudioHandle(val handle: Long) {
    override fun toString(): String = "AudioHandle($handle)"
}

/**
 * A handle to a Font on the CommandServer. Created with [CommandQueue.decodeFont] and deleted with
 * [CommandQueue.deleteFont].
 *
 * @param handle The handle issued by the native CommandQueue.
 */
@JvmInline
value class FontHandle(val handle: Long) {
    override fun toString(): String = "FontHandle($handle)"
}

/**
 * A key used to uniquely identify a draw operation in the CommandQueue. This is useful when the
 * same CommandQueue issues multiple draw calls. If the same key is used before the render loop
 * flushes the queue, the previous draw call will be replaced with the new one.
 *
 * @param handle The handle issued by the native CommandQueue for this draw operation.
 */
@JvmInline
value class DrawKey(val handle: Long)

/**
 * Holds the native pointers to the listeners used by the CommandQueue. Android uses only one
 * listener of each type to simplify lifetime management. This is as opposed to a listener for each
 * handle.
 *
 * This class is constructed from C++.
 */
@Keep // Called from JNI
data class Listeners(
    val fileListener: Long,
    val artboardListener: Long,
    val stateMachineListener: Long,
    val viewModelInstanceListener: Long,
    val imageListener: Long,
    val audioListener: Long,
    val fontListener: Long,
) : AutoCloseable {
    private external fun cppDelete(
        fileListener: Long,
        artboardListener: Long,
        stateMachineListener: Long,
        viewModelInstanceListener: Long,
        imageListener: Long,
        audioListener: Long,
        fontListener: Long,
    )

    /**
     * Dispose of the listeners and free their resources. This should be called when the
     * CommandQueue is disposed.
     */
    override fun close() = cppDelete(
        fileListener,
        artboardListener,
        stateMachineListener,
        viewModelInstanceListener,
        imageListener,
        audioListener,
        fontListener,
    )
}

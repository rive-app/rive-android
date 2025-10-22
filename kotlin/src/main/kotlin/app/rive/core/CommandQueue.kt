package app.rive.core

import android.graphics.Color
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.view.Surface
import androidx.annotation.ColorInt
import androidx.annotation.Keep
import app.rive.Artboard
import app.rive.RiveFile
import app.rive.ViewModelInstance
import app.rive.ViewModelInstanceSource
import app.rive.ViewModelSource
import app.rive.rememberRiveFile
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.File.Enum
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * A [CommandQueue] is the worker that runs Rive in a thread. It holds all of
 * the state, including assets ([images][ImageHandle], [audio][AudioHandle], and
 * [fonts][FontHandle]), [Rive files][RiveFile], [artboards][Artboard], state machines, and
 * [view model instances][ViewModelInstance].
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
 * by the [rememberCommandQueue][app.rive.rememberCommandQueue] composable.
 *
 * @param coroutineScope The coroutine scope to use for launching coroutines.
 * @throws RuntimeException If the command queue cannot be created for any reason.
 */
class CommandQueue(
    private val scope: CoroutineScope,
//    private val bridge: CommandQueueBridge
) {
    @Throws(RuntimeException::class)
    private external fun cppConstructor(display: Long, context: Long): Long
    private external fun cppDelete(pointer: Long)
    private external fun cppCreateListeners(pointer: Long): Listeners
    private external fun cppCreateRenderTarget(width: Int, height: Int): Long

    private external fun cppLoadFile(
        pointer: Long,
        requestID: Long,
        bytes: ByteArray
    )

    private external fun cppDeleteFile(pointer: Long, requestID: Long, fileHandle: Long)
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
        fit: Fit,
        alignment: Alignment,
        surfaceWidth: Float,
        surfaceHeight: Float,
        pointerID: Int,
        x: Float,
        y: Float
    )

    private external fun cppPointerDown(
        pointer: Long,
        stateMachineHandle: Long,
        fit: Fit,
        alignment: Alignment,
        surfaceWidth: Float,
        surfaceHeight: Float,
        pointerID: Int,
        x: Float,
        y: Float
    )

    private external fun cppPointerUp(
        pointer: Long,
        stateMachineHandle: Long,
        fit: Fit,
        alignment: Alignment,
        surfaceWidth: Float,
        surfaceHeight: Float,
        pointerID: Int,
        x: Float,
        y: Float
    )

    private external fun cppPointerExit(
        pointer: Long,
        stateMachineHandle: Long,
        fit: Fit,
        alignment: Alignment,
        surfaceWidth: Float,
        surfaceHeight: Float,
        pointerID: Int,
        x: Float,
        y: Float
    )

    private external fun cppCreateDrawKey(pointer: Long): Long
    private external fun cppPollMessages(pointer: Long)
    private external fun cppDraw(
        pointer: Long,
        eglDisplay: Long,
        eglSurface: Long,
        eglContext: Long,
        drawKey: Long,
        artboardHandle: Long,
        stateMachineHandle: Long,
        renderTargetPointer: Long,
        width: Int,
        height: Int,
        fit: Fit,
        alignment: Alignment,
        clearColor: Int
    )

    companion object {
        const val NULL_POINTER = 0L

        /**
         * Maximum number of RiveUI components that can safely use this CommandQueue instance
         * concurrently.
         */
        const val MAX_CONCURRENT_SUBSCRIBERS = 8
    }

    private var cppPointer: Long = NULL_POINTER
    private var listeners: Listeners

    private var referenceCount: AtomicInteger = AtomicInteger(1)
    internal val refCount get() = referenceCount.get()

    fun acquire() {
        referenceCount.incrementAndGet()
    }

    fun release() {
        val count = referenceCount.decrementAndGet()
        check(count >= 0) { "CommandQueue released too many times." }
        // Dispose
        if (count == 0) {
            cppDelete(cppPointer)
            cppPointer = NULL_POINTER
            listeners.dispose()
        }
    }

    private val queueDispatcher = Dispatchers.Main.immediate

    val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY).also {
        if (it == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("Unable to get EGL display")
        }

        if (!EGL14.eglInitialize(it, null, 0, null, 0)) {
            throw RuntimeException("Unable to initialize EGL")
        }
    }
    val config: EGLConfig = makeConfig()
    var context: EGLContext = EGL14.EGL_NO_CONTEXT

    private val _settledFlow = MutableSharedFlow<StateMachineHandle>(
        replay = 0,
        extraBufferCapacity = MAX_CONCURRENT_SUBSCRIBERS, // Protects for the worst case of simultaneous settles
        onBufferOverflow = BufferOverflow.DROP_OLDEST // It's fine to miss a settle event - worse to block
    )
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
    val numberPropertyFlow: SharedFlow<PropertyUpdate<Float>> = _numberPropertyFlow

    private val _stringPropertyFlow = MutableSharedFlow<PropertyUpdate<String>>(
        replay = 0,
        extraBufferCapacity = MAX_CONCURRENT_SUBSCRIBERS,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val stringPropertyFlow: SharedFlow<PropertyUpdate<String>> = _stringPropertyFlow

    private val _booleanPropertyFlow = MutableSharedFlow<PropertyUpdate<Boolean>>(
        replay = 0,
        extraBufferCapacity = MAX_CONCURRENT_SUBSCRIBERS,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val booleanPropertyFlow: SharedFlow<PropertyUpdate<Boolean>> = _booleanPropertyFlow

    private val _enumPropertyFlow = MutableSharedFlow<PropertyUpdate<String>>(
        replay = 0,
        extraBufferCapacity = MAX_CONCURRENT_SUBSCRIBERS,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val enumPropertyFlow: SharedFlow<PropertyUpdate<String>> = _enumPropertyFlow

    private val _colorPropertyFlow = MutableSharedFlow<PropertyUpdate<Int>>(
        replay = 0,
        extraBufferCapacity = MAX_CONCURRENT_SUBSCRIBERS,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val colorPropertyFlow: SharedFlow<PropertyUpdate<Int>> = _colorPropertyFlow

    private val _triggerPropertyFlow = MutableSharedFlow<PropertyUpdate<Unit>>(
        replay = 0,
        extraBufferCapacity = MAX_CONCURRENT_SUBSCRIBERS,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val triggerPropertyFlow: SharedFlow<PropertyUpdate<Unit>> = _triggerPropertyFlow

    private fun makeConfig(): EGLConfig {
        val configAttributes = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
//            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 0,
            EGL14.EGL_STENCIL_SIZE, 8,
            EGL14.EGL_NONE
        )

        val numConfigs = IntArray(1)
        val configs = arrayOfNulls<EGLConfig>(1)
        EGL14.eglChooseConfig(
            display,
            configAttributes,
            0,
            configs,
            0,
            configs.size,
            numConfigs,
            0
        )
        return configs[0] ?: throw RuntimeException("Unable to find a suitable EGLConfig")
    }

    init {
        val err = EGL14.eglGetError()
        if (err != EGL14.EGL_SUCCESS) {
            throw RuntimeException("EGL error: $err")
        }

        var version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw RuntimeException("Unable to initialize EGL")
        }

        context = EGL14.eglCreateContext(
            display,
            config,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
            0
        )

        if (context == EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("Unable to create EGL context")
        }

        cppPointer = cppConstructor(display.nativeHandle, context.nativeHandle)
        listeners = cppCreateListeners(cppPointer)
    }

    fun createRiveSurface(surface: Surface): RiveSurface {
        val eglSurface = EGL14.eglCreateWindowSurface(
            display,
            config,
            surface,
            intArrayOf(EGL14.EGL_NONE),
            0
        )

        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("Unable to create EGL surface")
        }

        var dimensions = IntArray(2)
        EGL14.eglQuerySurface(display, eglSurface, EGL14.EGL_WIDTH, dimensions, 0)
        EGL14.eglQuerySurface(display, eglSurface, EGL14.EGL_HEIGHT, dimensions, 1)
        val width = dimensions[0]
        val height = dimensions[1]

        val renderTarget = cppCreateRenderTarget(width, height)
        val drawKey = DrawKey(cppCreateDrawKey(cppPointer))

        return RiveSurface(surface, eglSurface, display, renderTarget, drawKey, width, height)
    }

    /**
     * Load a Rive into the command queue. Returns the handle in either [onFileLoaded] or an error
     * in [onFileError].
     *
     * @param bytes The bytes of the Rive file to load.
     * @return A [FileHandle] that represents the loaded Rive file.
     * @throws RuntimeException If the file could not be loaded, this will throw an exception. Note
     *    that the exception comes from the continuation callback.
     */
    @Throws(RuntimeException::class)
    suspend fun loadFile(bytes: ByteArray): FileHandle = suspendNativeRequest { requestID ->
        cppLoadFile(cppPointer, requestID, bytes)
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
     * Callback when there is *any* error on a file operation. This includes loading a file, but
     * also operations such as instantiating an artboard or state machine.
     *
     * @param requestID The request ID used when loading the file, used to complete the
     *    continuation.
     * @param error The error message.
     * @throws RuntimeException If the file could not be loaded, this will resume the continuation
     *    with an exception.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onFileError")
    @Throws(RuntimeException::class)
    internal fun onFileError(requestID: Long, error: String) {
        (pendingContinuations.remove(requestID) as? Continuation<FileHandle>)?.resumeWithException(
            RuntimeException("File error: $error")
        )
    }

    /**
     * Counterpart to [loadFile] to free the resources associated to the file handle.
     *
     * @param fileHandle The handle of the file to delete.
     */
    fun deleteFile(fileHandle: FileHandle) =
        cppDeleteFile(cppPointer, nextRequestID.getAndIncrement(), fileHandle.handle)

    /**
     * Query the file for available artboard names. Returns on [onArtboardsListed].
     *
     * @param fileHandle The handle of the file to query.
     * @return A list of artboard names in the file.
     */
    suspend fun getArtboardNames(fileHandle: FileHandle): List<String> =
        suspendNativeRequest { requestID ->
            cppGetArtboardNames(
                cppPointer,
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
     */
    suspend fun getStateMachineNames(artboardHandle: ArtboardHandle): List<String> =
        suspendNativeRequest { requestID ->
            cppGetStateMachineNames(
                cppPointer,
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
     */
    suspend fun getViewModelNames(fileHandle: FileHandle): List<String> =
        suspendNativeRequest { requestID ->
            cppGetViewModelNames(
                cppPointer,
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
     */
    suspend fun getViewModelInstanceNames(
        fileHandle: FileHandle,
        viewModelName: String
    ): List<String> = suspendNativeRequest { requestID ->
        cppGetViewModelInstanceNames(
            cppPointer,
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
     */
    suspend fun getViewModelProperties(
        fileHandle: FileHandle,
        viewModelName: String
    ): List<ViewModel.Property> = suspendNativeRequest { requestID ->
        cppGetViewModelProperties(
            cppPointer,
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
     */
    suspend fun getEnums(fileHandle: FileHandle): List<Enum> = suspendNativeRequest { requestID ->
        cppGetEnums(
            cppPointer,
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
     */
    fun createDefaultArtboard(fileHandle: FileHandle): ArtboardHandle =
        ArtboardHandle(
            cppCreateDefaultArtboard(
                cppPointer,
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
     */
    fun createArtboardByName(fileHandle: FileHandle, name: String): ArtboardHandle =
        ArtboardHandle(
            cppCreateArtboardByName(
                cppPointer,
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
     */
    fun deleteArtboard(artboardHandle: ArtboardHandle) =
        cppDeleteArtboard(cppPointer, nextRequestID.getAndIncrement(), artboardHandle.handle)

    /**
     * Create the default state machine for the given artboard. This is the state machine marked
     * "Default" in the Rive editor.
     *
     * @param artboardHandle The handle of the artboard that owns the state machine.
     * @return The handle of the created state machine.
     */
    fun createDefaultStateMachine(artboardHandle: ArtboardHandle): StateMachineHandle =
        StateMachineHandle(
            cppCreateDefaultStateMachine(
                cppPointer,
                nextRequestID.getAndIncrement(),
                artboardHandle.handle
            )
        )

    /**
     * Create a state machine by name for the given artboard. This is useful when the artboard has
     * multiple state machines and you want to create a specific one.
     *
     * @param artboardHandle The handle of the artboard that owns the state machine.
     * @param name The name of the state machine to create.
     */
    fun createStateMachineByName(artboardHandle: ArtboardHandle, name: String): StateMachineHandle =
        StateMachineHandle(
            cppCreateStateMachineByName(
                cppPointer,
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
     */
    fun deleteStateMachine(stateMachineHandle: StateMachineHandle) =
        cppDeleteStateMachine(
            cppPointer,
            nextRequestID.getAndIncrement(),
            stateMachineHandle.handle
        )

    /**
     * Advance the state machine by the given delta time in nanoseconds.
     *
     * @param stateMachineHandle The handle of the state machine to advance.
     * @param deltaTimeNs The delta time in nanoseconds to advance the state machine by.
     */
    fun advanceStateMachine(
        stateMachineHandle: StateMachineHandle,
        deltaTimeNs: Long
    ) = cppAdvanceStateMachine(
        cppPointer,
        stateMachineHandle.handle,
        deltaTimeNs
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
     */
    fun createViewModelInstance(
        fileHandle: FileHandle,
        source: ViewModelInstanceSource
    ): ViewModelInstanceHandle =
        when (source) {
            is ViewModelInstanceSource.Blank -> when (val vm = source.vmSource) {
                is ViewModelSource.Named ->
                    ViewModelInstanceHandle(
                        cppNamedVMCreateBlankVMI(
                            cppPointer,
                            nextRequestID.getAndIncrement(),
                            fileHandle.handle,
                            vm.viewModelName
                        )
                    )

                is ViewModelSource.DefaultForArtboard ->
                    ViewModelInstanceHandle(
                        cppDefaultVMCreateBlankVMI(
                            cppPointer,
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
                            cppPointer,
                            nextRequestID.getAndIncrement(),
                            fileHandle.handle,
                            vm.viewModelName
                        )
                    )

                is ViewModelSource.DefaultForArtboard ->
                    ViewModelInstanceHandle(
                        cppDefaultVMCreateDefaultVMI(
                            cppPointer,
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
                            cppPointer,
                            nextRequestID.getAndIncrement(),
                            fileHandle.handle,
                            vm.viewModelName,
                            source.instanceName
                        )
                    )

                is ViewModelSource.DefaultForArtboard ->
                    ViewModelInstanceHandle(
                        cppDefaultVMCreateNamedVMI(
                            cppPointer,
                            nextRequestID.getAndIncrement(),
                            fileHandle.handle,
                            vm.artboard.artboardHandle.handle,
                            source.instanceName
                        )
                    )
            }

            is ViewModelInstanceSource.Reference -> ViewModelInstanceHandle(
                cppReferenceNestedVMI(
                    cppPointer,
                    nextRequestID.getAndIncrement(),
                    source.instance.instanceHandle.handle,
                    source.path
                )
            )
        }

    /**
     * Delete a view model instance and free its resources. This is useful when you no longer need
     * the view model instance and want to free up memory. Counterpart to [createViewModelInstance].
     *
     * @param viewModelInstanceHandle The handle of the view model instance to delete.
     */
    fun deleteViewModelInstance(
        viewModelInstanceHandle: ViewModelInstanceHandle
    ) = cppDeleteViewModelInstance(
        cppPointer,
        nextRequestID.getAndIncrement(),
        viewModelInstanceHandle.handle
    )

    /**
     * Bind a view model instance to a state machine. This establishes the data binding for the
     * instance's properties.
     *
     * @param stateMachineHandle The handle of the state machine to bind to.
     * @param viewModelInstanceHandle The handle of the view model instance to bind.
     */
    fun bindViewModelInstance(
        stateMachineHandle: StateMachineHandle,
        viewModelInstanceHandle: ViewModelInstanceHandle
    ) = cppBindViewModelInstance(
        cppPointer,
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
     */
    fun setNumberProperty(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String,
        value: Float
    ) = cppSetNumberProperty(
        cppPointer,
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
     */
    suspend fun getNumberProperty(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String
    ): Float = suspendNativeRequest { requestID ->
        cppGetNumberProperty(
            cppPointer,
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
     */
    fun setStringProperty(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String,
        value: String
    ) = cppSetStringProperty(
        cppPointer,
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
     */
    suspend fun getStringProperty(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String
    ): String = suspendNativeRequest { requestID ->
        cppGetStringProperty(
            cppPointer,
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
     */
    fun setBooleanProperty(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String,
        value: Boolean
    ) = cppSetBooleanProperty(
        cppPointer,
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
     */
    suspend fun getBooleanProperty(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String
    ): Boolean = suspendNativeRequest { requestID ->
        cppGetBooleanProperty(
            cppPointer,
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
     */
    fun setEnumProperty(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String,
        value: String
    ) = cppSetEnumProperty(
        cppPointer,
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
     */
    suspend fun getEnumProperty(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String
    ): String = suspendNativeRequest { requestID ->
        cppGetEnumProperty(
            cppPointer,
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
     */
    fun setColorProperty(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String,
        @ColorInt value: Int
    ) = cppSetColorProperty(
        cppPointer,
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
     */
    suspend fun getColorProperty(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String
    ): Int = suspendNativeRequest { requestID ->
        cppGetColorProperty(
            cppPointer,
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
     */
    fun fireTriggerProperty(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String
    ) = cppFireTriggerProperty(
        cppPointer,
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
     */
    fun subscribeToProperty(
        viewModelInstanceHandle: ViewModelInstanceHandle,
        propertyPath: String,
        propertyType: ViewModel.PropertyDataType
    ) = cppSubscribeToProperty(
        cppPointer,
        viewModelInstanceHandle.handle,
        propertyPath,
        propertyType.value
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
     */
    @Throws(RuntimeException::class)
    suspend fun decodeImage(bytes: ByteArray): ImageHandle = suspendNativeRequest { requestID ->
        cppDecodeImage(cppPointer, requestID, bytes)
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
     * Callback when an image fails to decode, from [decodeImage].
     *
     * @param requestID The request ID used when decoding the image, used to complete the
     *    continuation.
     * @param error The error message describing the failure.
     * @throws RuntimeException If the image could not be decoded, this will resume the continuation
     *    with an exception.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onImageError")
    @Throws(RuntimeException::class)
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
     */
    fun deleteImage(imageHandle: ImageHandle) =
        cppDeleteImage(cppPointer, imageHandle.handle)

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
     */
    fun registerImage(name: String, imageHandle: ImageHandle) =
        cppRegisterImage(cppPointer, name, imageHandle.handle)

    /**
     * Unregister an image that was previously registered with [registerImage]. This will remove the
     * reference to the image from the CommandServer, allowing the memory to be freed if the image
     * handle was also deleted with [deleteImage].
     *
     * @param name The name of the referenced asset to unregister, the same used in [registerImage].
     */
    fun unregisterImage(name: String) =
        cppUnregisterImage(cppPointer, name)

    /**
     * Decode an audio file from the given bytes. The decoded audio is stored on the CommandServer.
     *
     * Audio may be used to supply a referenced asset in a Rive file with [registerAudio].
     *
     * @param bytes The bytes of the audio file to decode.
     * @return A handle to the decoded audio.
     * @throws RuntimeException If the audio could not be decoded, e.g. if the bytes are not a valid
     *    audio file.
     */
    @Throws(RuntimeException::class)
    suspend fun decodeAudio(bytes: ByteArray): AudioHandle = suspendNativeRequest { requestID ->
        cppDecodeAudio(cppPointer, requestID, bytes)
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
     * Callback when audio fails to decode, from [decodeAudio].
     *
     * @param requestID The request ID used when decoding the audio, used to complete the
     *    continuation.
     * @param error The error message describing the failure.
     * @throws RuntimeException If the audio could not be decoded, this will resume the continuation
     *    with an exception.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onAudioError")
    @Throws(RuntimeException::class)
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
     */
    fun deleteAudio(audioHandle: AudioHandle) =
        cppDeleteAudio(cppPointer, audioHandle.handle)

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
     */
    fun registerAudio(name: String, audioHandle: AudioHandle) =
        cppRegisterAudio(cppPointer, name, audioHandle.handle)

    /**
     * Unregister audio that was previously registered with [registerAudio]. This will remove the
     * reference to the audio from the CommandServer, allowing the memory to be freed if the audio
     * handle was also deleted with [deleteAudio].
     *
     * @param name The name of the referenced asset to unregister, the same used in [registerAudio].
     */
    fun unregisterAudio(name: String) =
        cppUnregisterAudio(cppPointer, name)

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
     */
    @Throws(RuntimeException::class)
    suspend fun decodeFont(bytes: ByteArray): FontHandle = suspendNativeRequest { requestID ->
        cppDecodeFont(cppPointer, requestID, bytes)
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
     * Callback when a font fails to decode, from [decodeFont].
     *
     * @param requestID The request ID used when decoding the font, used to complete the
     *    continuation.
     * @param error The error message describing the failure.
     * @throws RuntimeException If the font could not be decoded, this will resume the continuation
     *    with an exception.
     */
    @Keep // Called from JNI
    @Suppress("Unused")
    @JvmName("onFontError")
    @Throws(RuntimeException::class)
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
     */
    fun deleteFont(fontHandle: FontHandle) =
        cppDeleteFont(cppPointer, fontHandle.handle)

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
     */
    fun registerFont(name: String, fontHandle: FontHandle) =
        cppRegisterFont(cppPointer, name, fontHandle.handle)

    /**
     * Unregister a font that was previously registered with [registerFont]. This will remove the
     * reference to the font from the CommandServer, allowing the memory to be freed if the font
     * handle was also deleted with [deleteFont].
     *
     * @param name The name of the referenced asset to unregister, the same used in [registerFont].
     */
    fun unregisterFont(name: String) =
        cppUnregisterFont(cppPointer, name)

    /**
     * Notify the state machine that the pointer (typically a user's touch) has moved. This is used
     * to interact with the state machine, triggering pointer events.
     *
     * The additional parameters are required for calculating the pointer position in artboard
     * space.
     *
     * @param stateMachineHandle The handle of the state machine to notify.
     * @param fit The fit mode of the artboard.
     * @param alignment The alignment of the artboard.
     * @param surfaceWidth The width of the surface the artboard is drawn to.
     * @param surfaceHeight The height of the surface the artboard is drawn to.
     * @param pointerX The X coordinate of the pointer in surface space.
     * @param pointerY The Y coordinate of the pointer in surface space.
     */
    fun pointerMove(
        stateMachineHandle: StateMachineHandle,
        fit: Fit,
        alignment: Alignment,
        surfaceWidth: Float,
        surfaceHeight: Float,
        pointerID: Int,
        pointerX: Float,
        pointerY: Float
    ) = cppPointerMove(
        cppPointer,
        stateMachineHandle.handle,
        fit,
        alignment,
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
     * @param alignment The alignment of the artboard.
     * @param surfaceWidth The width of the surface the artboard is drawn to.
     * @param surfaceHeight The height of the surface the artboard is drawn to.
     * @param pointerX The X coordinate of the pointer in surface space.
     * @param pointerY The Y coordinate of the pointer in surface space.
     */
    fun pointerDown(
        stateMachineHandle: StateMachineHandle,
        fit: Fit,
        alignment: Alignment,
        surfaceWidth: Float,
        surfaceHeight: Float,
        pointerID: Int,
        pointerX: Float,
        pointerY: Float
    ) = cppPointerDown(
        cppPointer,
        stateMachineHandle.handle,
        fit,
        alignment,
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
     * @param alignment The alignment of the artboard.
     * @param surfaceWidth The width of the surface the artboard is drawn to.
     * @param surfaceHeight The height of the surface the artboard is drawn to.
     * @param pointerX The X coordinate of the pointer in surface space.
     * @param pointerY The Y coordinate of the pointer in surface space.
     */
    fun pointerUp(
        stateMachineHandle: StateMachineHandle,
        fit: Fit,
        alignment: Alignment,
        surfaceWidth: Float,
        surfaceHeight: Float,
        pointerID: Int,
        pointerX: Float,
        pointerY: Float
    ) = cppPointerUp(
        cppPointer,
        stateMachineHandle.handle,
        fit,
        alignment,
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
     * @param alignment The alignment of the artboard.
     * @param surfaceWidth The width of the surface the artboard is drawn to.
     * @param surfaceHeight The height of the surface the artboard is drawn to.
     * @param pointerX The X coordinate of the pointer in surface space.
     * @param pointerY The Y coordinate of the pointer in surface space.
     */
    fun pointerExit(
        stateMachineHandle: StateMachineHandle,
        fit: Fit,
        alignment: Alignment,
        surfaceWidth: Float,
        surfaceHeight: Float,
        pointerID: Int,
        pointerX: Float,
        pointerY: Float
    ) = cppPointerExit(
        cppPointer,
        stateMachineHandle.handle,
        fit,
        alignment,
        surfaceWidth,
        surfaceHeight,
        pointerID,
        pointerX,
        pointerY
    )

    /**
     * Poll messages from the CommandServer to the CommandQueue. This is the channel that all
     * callbacks and errors arrive on. Should be called every frame, regardless of whether there is
     * any advancing or drawing.
     */
    fun pollMessages() = cppPollMessages(cppPointer)

    /**
     * Draw the artboard with the given state machine.
     *
     * @param artboardHandle The handle of the artboard to draw.
     * @param stateMachineHandle The handle of the state machine to use for drawing.
     * @param fit The fit mode of the artboard.
     * @param alignment The alignment of the artboard.
     * @param surface The surface to draw to.
     * @param clearColor The color to clear the surface with before drawing, in AARRGGBB format.
     */
    fun draw(
        artboardHandle: ArtboardHandle,
        stateMachineHandle: StateMachineHandle,
        fit: Fit,
        alignment: Alignment,
        surface: RiveSurface,
        clearColor: Int = Color.TRANSPARENT
    ) = cppDraw(
        cppPointer,
        display.nativeHandle,
        surface.eglSurface.nativeHandle,
        context.nativeHandle,
        surface.drawKey.handle,
        artboardHandle.handle,
        stateMachineHandle.handle,
        surface.renderTargetPointer,
        surface.width,
        surface.height,
        fit,
        alignment,
        clearColor,
    )

    /**
     * The map of all pending continuations, keyed by request ID. Entries are added when a suspend
     * request is made, and removed when the request is completed.
     *
     * @see [suspendNativeRequest]
     */
    private val pendingContinuations = ConcurrentHashMap<Long, Continuation<Any>>()

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
     * To handle the ability for callers to run on different dispatchers, the native function is
     * called on the main thread using a [launch] on the [queueDispatcher]. This is because the C++
     * CommandQueue implementation is currently not thread-safe, and so must always be called from
     * the main thread. If this changes in the future, this can be removed.
     *
     * @param nativeFn A lambda function invoked by the caller, using trailing lambda syntax, which
     *    takes the request ID as a parameter and performs the JNI function call.
     * @return The result of the JNI function call.
     */
    private suspend inline fun <reified T> suspendNativeRequest(
        crossinline nativeFn: (Long) -> Unit
    ): T = suspendCancellableCoroutine { cont ->
        val requestID = nextRequestID.getAndIncrement()

        // Store the continuation
        @Suppress("UNCHECKED_CAST")
        pendingContinuations[requestID] = cont as Continuation<Any>

        cont.invokeOnCancellation {
            pendingContinuations.remove(requestID)
        }

        /** Ensure the call to native happens on the main thread. */
        scope.launch(queueDispatcher) {
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
 * A collection of four surface properties needed for rendering.
 * - An Android Surface, provided by an Android SurfaceTextureListener
 * - An EGLSurface, created from the Surface
 * - A Rive render target, created natively which renders to the GL framebuffer
 * - A draw key, which uniquely identifies draw operations in the CommandQueue
 *
 * It also stores the width and height of the surface.
 *
 * It assumes ownership of all resources and should be [disposed][dispose] when no longer needed.
 *
 * @param surface The Android Surface that will be used for rendering.
 * @param eglSurface The EGLSurface created from the Android Surface.
 * @param display The EGLDisplay used to create the EGLSurface.
 * @param renderTargetPointer The native pointer to the Rive render target.
 * @param drawKey The key used to uniquely identify the draw operation in the CommandQueue.
 * @param width The width of the surface in pixels.
 * @param height The height of the surface in pixels.
 */
@Keep // Called from JNI
data class RiveSurface(
    val surface: Surface,
    val eglSurface: EGLSurface,
    private val display: EGLDisplay,
    val renderTargetPointer: Long,
    val drawKey: DrawKey,
    val width: Int,
    val height: Int
) {
    private external fun cppDelete(renderTargetPointer: Long)

    /**
     * Dispose of the RiveSurface and free its resources. This should be called when the backing
     * Android Surface is no longer available.
     *
     * Releases the Android Surface, destroys the EGLSurface, and deletes the native render target.
     */
    fun dispose() {
        surface.release()
        EGL14.eglDestroySurface(display, eglSurface)
        cppDelete(renderTargetPointer)
    }
}

/**
 * Holds the native pointers to the listeners used by the CommandQueue. Android uses only one
 * listener of each type to simplify lifetime management. This is as opposed to a listener for each
 * handle.
 */
@Keep // Called from JNI
internal data class Listeners(
    val fileListener: Long,
    val artboardListener: Long,
    val stateMachineListener: Long,
    val viewModelInstanceListener: Long,
    val imageListener: Long,
    val audioListener: Long,
    val fontListener: Long,
) {
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
    internal fun dispose() = cppDelete(
        fileListener,
        artboardListener,
        stateMachineListener,
        viewModelInstanceListener,
        imageListener,
        audioListener,
        fontListener,
    )
}

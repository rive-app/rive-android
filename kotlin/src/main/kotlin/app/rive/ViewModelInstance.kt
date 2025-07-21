package app.rive

import androidx.annotation.ColorInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.rive.core.CommandQueue
import app.rive.core.ViewModelInstanceHandle
import app.rive.runtime.kotlin.core.ViewModel.PropertyDataType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * A view model instance for data binding which has properties that can be set and observed.
 *
 * The instance must be bound to a state machine for its values to take effect. This is done by
 * passing it to [RiveUI].
 *
 * @param instanceHandle The handle to the view model instance on the command server.
 * @param commandQueue The command queue that owns the view model instance.
 * @param parentScope The coroutine scope to use for launching coroutines.
 */
class ViewModelInstance(
    internal val instanceHandle: ViewModelInstanceHandle,
    private val commandQueue: CommandQueue,
    private val parentScope: CoroutineScope
) {
    private val _dirtyFlow = MutableSharedFlow<Unit>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    internal val dirtyFlow: SharedFlow<Unit> = _dirtyFlow

    private val numberFlows = mutableMapOf<String, StateFlow<Float>>()
    private val stringFlows = mutableMapOf<String, StateFlow<String>>()
    private val booleanFlows = mutableMapOf<String, StateFlow<Boolean>>()
    private val enumFlows = mutableMapOf<String, StateFlow<String>>()
    private val colorFlows = mutableMapOf<String, StateFlow<Int>>()
    private val triggerFlows = mutableMapOf<String, StateFlow<Unit>>()

    private fun <T> getPropertyFlow(
        propertyPath: String,
        initialValue: T,
        cache: MutableMap<String, StateFlow<T>>,
        getter: suspend (ViewModelInstanceHandle, String) -> T,
        updateFlow: SharedFlow<CommandQueue.PropertyUpdate<T>>,
        propertyType: PropertyDataType
    ): StateFlow<T> = cache.getOrPut(propertyPath) {
        val state = MutableStateFlow<T>(initialValue)
        commandQueue.subscribeToProperty(instanceHandle, propertyPath, propertyType)
        parentScope.launch {
            val initial = getter(instanceHandle, propertyPath)
            state.value = initial
            updateFlow
                .filter { it.handle == instanceHandle && it.propertyPath == propertyPath }
                .collect { state.value = it.value }
        }
        state
    }

    /**
     * Creates or retrieve from cache a [number][Float] property, represented as a [StateFlow].
     *
     * The flow will automatically subscribe to updates from the command queue.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @param initialValue The initial value of the property. Since the property is retrieved
     *    asynchronously, the initial value is used until the first update is received.
     */
    fun getNumberFlow(propertyPath: String, initialValue: Float): StateFlow<Float> =
        getPropertyFlow(
            propertyPath,
            initialValue,
            numberFlows,
            commandQueue::getNumberProperty,
            commandQueue.numberPropertyFlow,
            PropertyDataType.NUMBER
        )

    /**
     * Creates or retrieve from cache a [string][String] property, represented as a [StateFlow].
     *
     * The flow will automatically subscribe to updates from the command queue.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @param initialValue The initial value of the property. Since the property is retrieved
     *    asynchronously, the initial value is used until the first update is received.
     */
    fun getStringFlow(propertyPath: String, initialValue: String): StateFlow<String> =
        getPropertyFlow(
            propertyPath,
            initialValue,
            stringFlows,
            commandQueue::getStringProperty,
            commandQueue.stringPropertyFlow,
            PropertyDataType.STRING
        )

    /**
     * Creates or retrieve from cache a [boolean][Boolean] property, represented as a [StateFlow].
     *
     * The flow will automatically subscribe to updates from the command queue.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @param initialValue The initial value of the property. Since the property is retrieved
     *    asynchronously, the initial value is used until the first update is received.
     */
    fun getBooleanFlow(propertyPath: String, initialValue: Boolean): StateFlow<Boolean> =
        getPropertyFlow(
            propertyPath,
            initialValue,
            booleanFlows,
            commandQueue::getBooleanProperty,
            commandQueue.booleanPropertyFlow,
            PropertyDataType.BOOLEAN
        )

    /**
     * Creates or retrieve from cache an enum property, represented as a [StateFlow]. Enums are
     * represented as strings, and this flow will emit the string value of the enum.
     *
     * The flow will automatically subscribe to updates from the command queue.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @param initialValue The initial value of the property. Since the property is retrieved
     *    asynchronously, the initial value is used until the first update is received.
     */
    fun getEnumFlow(propertyPath: String, initialValue: String): StateFlow<String> =
        getPropertyFlow(
            propertyPath,
            initialValue,
            enumFlows,
            commandQueue::getEnumProperty,
            commandQueue.enumPropertyFlow,
            PropertyDataType.ENUM
        )

    /**
     * Creates or retrieve from cache a color property, represented as a [StateFlow]. Colors are
     * represented as AARRGGBB integers, and this flow will emit the integer value of the color.
     *
     * The flow will automatically subscribe to updates from the command queue.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @param initialValue The initial value of the property. Since the property is retrieved
     *    asynchronously, the initial value is used until the first update is received.
     */
    fun getColorFlow(propertyPath: String, @ColorInt initialValue: Int): StateFlow<Int> =
        getPropertyFlow(
            propertyPath,
            initialValue,
            colorFlows,
            commandQueue::getColorProperty,
            commandQueue.colorPropertyFlow,
            PropertyDataType.COLOR
        )


    /**
     * Creates or retrieve from cache a trigger property, represented as a [StateFlow]. Triggers
     * emit Unit as the value, which simply indicates that the trigger has been fired.
     */
    fun getTriggerFlow(propertyPath: String): StateFlow<Unit> =
        getPropertyFlow(
            propertyPath,
            Unit,
            triggerFlows,
            { handle, path -> }, // Triggers don't have a getter, so we return Unit
            commandQueue.triggerPropertyFlow,
            PropertyDataType.TRIGGER
        )

    private fun <T> setProperty(
        propertyPath: String,
        value: T,
        setter: (ViewModelInstanceHandle, String, T) -> Unit
    ) {
        setter(instanceHandle, propertyPath, value)
        _dirtyFlow.tryEmit(Unit)
    }

    /**
     * Sets a [number][Float] property on this view model instance.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @param value The value to set the property to.
     */
    fun setNumber(propertyPath: String, value: Float) =
        setProperty(propertyPath, value, commandQueue::setNumberProperty)

    /**
     * Sets a [string][String] property on this view model instance.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @param value The value to set the property to.
     */
    fun setString(propertyPath: String, value: String) =
        setProperty(propertyPath, value, commandQueue::setStringProperty)

    /**
     * Sets a [boolean][Boolean] property on this view model instance.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @param value The value to set the property to.
     */
    fun setBoolean(propertyPath: String, value: Boolean) =
        setProperty(propertyPath, value, commandQueue::setBooleanProperty)

    /**
     * Sets an enum property on this view model instance. Enums are represented as strings.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @param value The string value of the enum to set the property to.
     */
    fun setEnum(propertyPath: String, value: String) =
        setProperty(propertyPath, value, commandQueue::setEnumProperty)

    /**
     * Sets a color property on this view model instance. Colors are represented as AARRGGBB
     * integers.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @param value The integer value of the color to set the property to.
     */
    fun setColor(propertyPath: String, @ColorInt value: Int) =
        setProperty(propertyPath, value, commandQueue::setColorProperty)

    /**
     * Fires a trigger on this view model instance.
     *
     * @param propertyPath The path to the trigger property from this view model instance. Slash
     *    delimited to refer to nested properties.
     */
    fun fireTrigger(propertyPath: String) =
        commandQueue.fireTriggerProperty(instanceHandle, propertyPath)
}

/**
 * One half of a source for a [ViewModelInstance]. This represents the view model that
 * originates the instance. This can be either [Named] to refer to a specific view model, or
 * [DefaultForArtboard], which will use the default view model for the given [Artboard]. This is
 * usually the one the designer has intended to be used with the artboard.
 *
 * The [ViewModelInstanceSource] is the other half, which represents the specific view model
 * instance. The helper methods on this interface are provided as a builder pattern.
 */
sealed interface ViewModelSource {
    @JvmInline
    value class Named(val viewModelName: String) : ViewModelSource

    @JvmInline
    value class DefaultForArtboard(val artboard: Artboard) : ViewModelSource

    /** A view model instance with default initialized properties. */
    fun blankInstance(): ViewModelInstanceSource = ViewModelInstanceSource.Blank(this)

    /** The instance marked default in the editor. */
    fun defaultInstance(): ViewModelInstanceSource = ViewModelInstanceSource.Default(this)

    /** A specific instance by name. */
    fun namedInstance(instanceName: String): ViewModelInstanceSource =
        ViewModelInstanceSource.Named(this, instanceName)

}

/**
 * The second half of a source for a [ViewModelInstance]. This represents the specific instance of
 * the view model. This can be either:
 * - [Blank] for an instance with default initialized properties,
 * - [Default] for the default instance, or
 * - [Named] for a specific named instance.
 *
 * The [Reference] option is used to refer to a child instance, given an existing instance, at a
 * slash delimited path.
 */
sealed interface ViewModelInstanceSource {
    @JvmInline
    value class Blank(val vmSource: ViewModelSource) : ViewModelInstanceSource

    @JvmInline
    value class Default(val vmSource: ViewModelSource) : ViewModelInstanceSource

    data class Named(val vmSource: ViewModelSource, val instanceName: String) :
        ViewModelInstanceSource

    data class Reference(val instance: ViewModelInstance, val path: String) :
        ViewModelInstanceSource
}

/**
 * Creates a [ViewModelInstance] from the given [file] and [source].
 *
 * The lifetime of the [ViewModelInstance] is managed by this composable. It will release the
 * resources allocated to the instance when it falls out of scope.
 *
 * @param file The [RiveFile] to create the view model instance from.
 * @param source The source of the view model instance. Constructed from [ViewModelSource] combined
 *    with [ViewModelInstanceSource].
 */
@ExperimentalRiveComposeAPI
@Composable
fun rememberViewModelInstance(
    file: RiveFile,
    source: ViewModelInstanceSource,
): ViewModelInstance {
    val commandQueue = file.commandQueue
    val instanceScope = rememberCoroutineScope()

    val instance = remember(file, source) {
        val handle = commandQueue.createViewModelInstance(file.fileHandle, source)
        RiveLog.d(VM_INSTANCE_TAG) { "Created $handle from source: $source (${file.fileHandle})" }
        ViewModelInstance(handle, commandQueue, instanceScope)
    }

    DisposableEffect(instance) {
        onDispose {
            RiveLog.d(VM_INSTANCE_TAG) { "Deleting ${instance.instanceHandle} (${file.fileHandle})" }
            commandQueue.deleteViewModelInstance(instance.instanceHandle)
            instanceScope.cancel()
        }
    }

    return instance
}

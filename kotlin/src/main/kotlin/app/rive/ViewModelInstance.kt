package app.rive

import androidx.annotation.ColorInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import app.rive.core.CloseOnce
import app.rive.core.CommandQueue
import app.rive.core.FileHandle
import app.rive.core.ViewModelInstanceHandle
import app.rive.runtime.kotlin.core.ViewModel.PropertyDataType
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription

internal const val VM_INSTANCE_TAG = "Rive/VMI"

/**
 * A view model instance for data binding which has properties that can be set and observed.
 *
 * The instance must be bound to a state machine for its values to take effect. This is done by
 * passing it to [RiveUI].
 *
 * @param instanceHandle The handle to the view model instance on the command server.
 * @param commandQueue The command queue that owns the view model instance.
 */
class ViewModelInstance internal constructor(
    val instanceHandle: ViewModelInstanceHandle,
    private val commandQueue: CommandQueue,
    private val fileHandle: FileHandle,
) : AutoCloseable by CloseOnce("$instanceHandle", {
    RiveLog.d(VM_INSTANCE_TAG) { "Deleting $instanceHandle (${fileHandle})" }
    commandQueue.deleteViewModelInstance(instanceHandle)
}) {
    companion object {
        /**
         * Creates a new [ViewModelInstance].
         *
         * ⚠️ The lifetime of the returned view model instance is managed by the caller. Make sure
         * to call [close] when you are done with the instance to release its resources.
         *
         * @param file The [RiveFile] to create the view model instance from.
         * @param source The source of the view model instance. Constructed from [ViewModelSource]
         *    combined with [ViewModelInstanceSource].
         * @return The created view model instance.
         */
        fun fromFile(
            file: RiveFile,
            source: ViewModelInstanceSource
        ): ViewModelInstance {
            val handle = file.commandQueue.createViewModelInstance(file.fileHandle, source)
            RiveLog.d(VM_INSTANCE_TAG) { "Created $handle from source: $source (${file.fileHandle})" }
            return ViewModelInstance(handle, file.commandQueue, file.fileHandle)
        }
    }

    private val _dirtyFlow = MutableSharedFlow<Unit>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    internal val dirtyFlow: SharedFlow<Unit> = _dirtyFlow

    private val numberFlows = mutableMapOf<String, Flow<Float>>()
    private val stringFlows = mutableMapOf<String, Flow<String>>()
    private val booleanFlows = mutableMapOf<String, Flow<Boolean>>()
    private val enumFlows = mutableMapOf<String, Flow<String>>()
    private val colorFlows = mutableMapOf<String, Flow<Int>>()
    private val triggerFlows = mutableMapOf<String, Flow<Unit>>()

    private fun <T> getPropertyFlow(
        propertyPath: String,
        cache: MutableMap<String, Flow<T>>,
        getter: suspend (ViewModelInstanceHandle, String) -> T,
        updateFlow: SharedFlow<CommandQueue.PropertyUpdate<T>>,
        propertyType: PropertyDataType
    ): Flow<T> = cache.getOrPut(propertyPath) {
        updateFlow
            // Ensure we’re subscribed, then kick off fetching latest value
            .onSubscription {
                commandQueue.subscribeToProperty(instanceHandle, propertyPath, propertyType)
                // Fire the getter so its reply comes through as the first emission
                // (ignoring the immediately returned value).
                getter(instanceHandle, propertyPath)
            }
            .filter { it.handle == instanceHandle && it.propertyPath == propertyPath }
            .map { it.value }
            .distinctUntilChanged() // Don't emit duplicates
    }

    /**
     * Creates or retrieves from cache a [number][Float] property, represented as a cold [Flow].
     *
     * The flow is subscribed to updates from the command queue while it is being collected.
     *
     * This flow emits every distinct value (up to the backing buffer limit). If you process
     * the flow slowly, consider applying [conflate] if you only need the latest value to skip
     * intermediate values. Alternatively, if you need to process every value, consider using a
     * [buffer] operator with an appropriate buffer size to handle bursts.
     *
     * Collection of the flow may cause an exception:
     * - [RuntimeException]: If this class has been [closed][close], if the property does not exist
     *   on this view model instance, or is is of a different type.
     * - [IllegalStateException]: If the backing command queue has been released.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @return A cold [Flow] of [Float] values representing the property.
     */
    fun getNumberFlow(propertyPath: String): Flow<Float> =
        getPropertyFlow(
            propertyPath,
            numberFlows,
            commandQueue::getNumberProperty,
            commandQueue.numberPropertyFlow,
            PropertyDataType.NUMBER
        )

    /**
     * Creates or retrieves from cache a [string][String] property, represented as a cold [Flow].
     *
     * The collection of the flow may cause an exception. See [getNumberFlow] for details.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @return A cold [Flow] of [String] values representing the property.
     * @see getNumberFlow
     */
    fun getStringFlow(propertyPath: String): Flow<String> =
        getPropertyFlow(
            propertyPath,
            stringFlows,
            commandQueue::getStringProperty,
            commandQueue.stringPropertyFlow,
            PropertyDataType.STRING
        )

    /**
     * Creates or retrieves from cache a [boolean][Boolean] property, represented as a cold [Flow].
     *
     * The collection of the flow may cause an exception. See [getNumberFlow] for details.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @return A cold [Flow] of [Boolean] values representing the property.
     * @see getNumberFlow
     */
    fun getBooleanFlow(propertyPath: String): Flow<Boolean> =
        getPropertyFlow(
            propertyPath,
            booleanFlows,
            commandQueue::getBooleanProperty,
            commandQueue.booleanPropertyFlow,
            PropertyDataType.BOOLEAN
        )

    /**
     * Creates or retrieves from cache an enum property, represented as a cold [Flow]. Enums are
     * represented as strings, and this flow will emit the string value of the enum.
     *
     * The collection of the flow may cause an exception. See [getNumberFlow] for details.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @return A cold [Flow] of [String] values representing the enum property.
     * @see getNumberFlow
     */
    fun getEnumFlow(propertyPath: String): Flow<String> =
        getPropertyFlow(
            propertyPath,
            enumFlows,
            commandQueue::getEnumProperty,
            commandQueue.enumPropertyFlow,
            PropertyDataType.ENUM
        )

    /**
     * Creates or retrieves from cache a color property, represented as a cold [Flow]. Colors are
     * represented as AARRGGBB integers, and this flow will emit the integer value of the color.
     *
     * The collection of the flow may cause an exception. See [getNumberFlow] for details.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @return A cold [Flow] of [Int] values representing the color property.
     * @see getNumberFlow
     */
    fun getColorFlow(propertyPath: String): Flow<Int> =
        getPropertyFlow(
            propertyPath,
            colorFlows,
            commandQueue::getColorProperty,
            commandQueue.colorPropertyFlow,
            PropertyDataType.COLOR
        )

    /**
     * Creates or retrieves from cache a trigger property, represented as a cold [Flow]. Triggers
     * emit Unit as the value, which simply indicates that the trigger has been fired.
     *
     * The collection of the flow may cause an exception. See [getNumberFlow] for details.
     *
     * @param propertyPath The path to the trigger property from this view model instance. Slash
     *    delimited to refer to nested properties.
     * @return A cold [Flow] of [Unit] values representing trigger events.
     * @see getNumberFlow
     */
    fun getTriggerFlow(propertyPath: String): Flow<Unit> = triggerFlows.getOrPut(propertyPath) {
        commandQueue.triggerPropertyFlow
            .onSubscription {
                commandQueue.subscribeToProperty(
                    instanceHandle,
                    propertyPath,
                    PropertyDataType.TRIGGER
                )
            }
            .filter { it.handle == instanceHandle && it.propertyPath == propertyPath }
            .map { /* Unit */ }
            .buffer(32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }

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
     * ℹ️ Changes to bound Rive elements will not be reflected until the next state machine advance.
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
     * ℹ️ Changes to bound Rive elements will not be reflected until the next state machine advance.
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
     * ℹ️ Changes to bound Rive elements will not be reflected until the next state machine advance.
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
     * ℹ️ Changes to bound Rive elements will not be reflected until the next state machine advance.
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
     * ℹ️ Changes to bound Rive elements will not be reflected until the next state machine advance.
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
     * ℹ️ Changes to bound Rive elements will not be reflected until the next state machine advance.
     *
     * @param propertyPath The path to the trigger property from this view model instance. Slash
     *    delimited to refer to nested properties.
     */
    fun fireTrigger(propertyPath: String) =
        commandQueue.fireTriggerProperty(instanceHandle, propertyPath)

    /**
     * Assigns the given image to the image property on this view model instance.
     *
     * ℹ️ Changes to bound Rive elements will not be reflected until the next state machine advance.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @param image The image to assign to the property.
     */
    fun setImage(propertyPath: String, image: ImageAsset) {
        RiveLog.d(VM_INSTANCE_TAG) { "Assigning $image to $propertyPath (${fileHandle})" }
        setProperty(propertyPath, image.handle, commandQueue::setImageProperty)
    }

    /**
     * Assigns the given artboard to the bindable artboard property on this view model instance.
     *
     * ℹ️ Changes to bound Rive elements will not be reflected until the next state machine advance.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @param artboard The artboard to assign to the property.
     */
    fun setArtboard(propertyPath: String, artboard: Artboard) {
        RiveLog.d(VM_INSTANCE_TAG) { "Assigning $artboard to $propertyPath (${fileHandle})" }
        setProperty(propertyPath, artboard.artboardHandle, commandQueue::setArtboardProperty)
    }

    suspend fun getListSize(propertyPath: String): Int =
        commandQueue.getListSize(instanceHandle, propertyPath)

    /**
     * Inserts an item into a list property at the specified index.
     *
     * ℹ️ Changes to bound Rive elements will not be reflected until the next state machine advance.
     *
     * Once the item is added to the list, you do not need to hold a reference to its instance. The
     * list will also maintain a reference to the item.
     *
     * @param propertyPath The path to the list property from this view model instance. Slash
     *    delimited to refer to nested properties.
     * @param index The index at which to insert the item.
     * @param item The view model instance to insert into the list.
     */
    fun insertToListAtIndex(propertyPath: String, index: Int, item: ViewModelInstance) {
        commandQueue.insertToListAtIndex(instanceHandle, propertyPath, index, item.instanceHandle)
        _dirtyFlow.tryEmit(Unit)
    }

    /**
     * Appends an item to the end of a list property.
     *
     * ℹ️ Changes to bound Rive elements will not be reflected until the next state machine advance.
     *
     * Once the item is added to the list, you do not need to hold a reference to its instance. The
     * list will also maintain a reference to the item.
     *
     * @param propertyPath The path to the list property from this view model instance. Slash
     *    delimited to refer to nested properties.
     * @param item The view model instance to append to the list.
     */
    fun appendToList(propertyPath: String, item: ViewModelInstance) {
        commandQueue.appendToList(instanceHandle, propertyPath, item.instanceHandle)
        _dirtyFlow.tryEmit(Unit)
    }

    /**
     * Removes an item from a list property at the specified index.
     *
     * ℹ️ Changes to bound Rive elements will not be reflected until the next state machine advance.
     *
     * @param propertyPath The path to the list property from this view model instance. Slash
     *    delimited to refer to nested properties.
     * @param index The index of the item to remove.
     */
    fun removeFromListAtIndex(propertyPath: String, index: Int) {
        commandQueue.removeFromListAtIndex(instanceHandle, propertyPath, index)
        _dirtyFlow.tryEmit(Unit)
    }

    /**
     * Removes an item from a list property.
     *
     * ℹ️ Changes to bound Rive elements will not be reflected until the next state machine advance.
     *
     * @param propertyPath The path to the list property from this view model instance. Slash
     *    delimited to refer to nested properties.
     * @param item The view model instance to remove from the list.
     */
    fun removeFromList(propertyPath: String, item: ViewModelInstance) {
        commandQueue.removeFromList(instanceHandle, propertyPath, item.instanceHandle)
        _dirtyFlow.tryEmit(Unit)
    }

    /**
     * Swaps two items in a list property by their indices.
     *
     * ℹ️ Changes to bound Rive elements will not be reflected until the next state machine advance.
     *
     * @param propertyPath The path to the list property from this view model instance. Slash
     *    delimited to refer to nested properties.
     * @param indexA The index of the first item to swap.
     * @param indexB The index of the second item to swap.
     */
    fun swapListItems(propertyPath: String, indexA: Int, indexB: Int) {
        commandQueue.swapListItems(instanceHandle, propertyPath, indexA, indexB)
        _dirtyFlow.tryEmit(Unit)
    }
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
    /** A specific view model by [name][viewModelName]. */
    @JvmInline
    value class Named(val viewModelName: String) : ViewModelSource

    /** The default view model for the given [artboard]. */
    @JvmInline
    value class DefaultForArtboard(val artboard: Artboard) : ViewModelSource

    /**
     * A view model instance with default initialized properties.
     *
     * @see [ViewModelInstanceSource.Blank]
     */
    fun blankInstance(): ViewModelInstanceSource = ViewModelInstanceSource.Blank(this)

    /**
     * The instance marked "Default" in the Rive file.
     *
     * @see [ViewModelInstanceSource.Default]
     */
    fun defaultInstance(): ViewModelInstanceSource = ViewModelInstanceSource.Default(this)

    /**
     * A specific instance by name.
     *
     * @see [ViewModelInstanceSource.Named]
     */
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
    /**
     * Create a new view model instance with default value initialized properties, e.g. 0 for
     * integers, empty strings, etc.
     *
     * @param vmSource The source of the view model that owns this instance.
     */
    @JvmInline
    value class Blank(val vmSource: ViewModelSource) : ViewModelInstanceSource

    /**
     * Create a new view model instance with properties initialized to the values of the instance
     * labelled "Default" in the Rive file.
     *
     * @param vmSource The source of the view model that owns this instance.
     */
    @JvmInline
    value class Default(val vmSource: ViewModelSource) : ViewModelInstanceSource

    /**
     * Create a new view model instance with properties initialized to the values of the instance
     * with the given name.
     *
     * @param vmSource The source of the view model that owns this instance.
     * @param instanceName The name of the instance in the Rive file to reference when creating the
     *    new instance.
     */
    data class Named(val vmSource: ViewModelSource, val instanceName: String) :
        ViewModelInstanceSource

    /**
     * Create a reference to an existing child view model instance.
     *
     * @param parentInstance The parent that contains the child.
     * @param path The path to the child instance. Slash delimited to refer to nested properties.
     */
    data class Reference(val parentInstance: ViewModelInstance, val path: String) :
        ViewModelInstanceSource

    /**
     * Create a reference to an existing child view model instance within a list at a given index.
     *
     * @param parentInstance The parent that contains the list.
     * @param pathToList The path to the list. Slash delimited to refer to nested properties.
     * @param index The index of the child instance in the list.
     */
    data class ReferenceListItem(
        val parentInstance: ViewModelInstance,
        val pathToList: String,
        val index: Int
    ) : ViewModelInstanceSource
}

/**
 * Creates a [ViewModelInstance] from the given [file] and [source].
 *
 * The lifetime of the instance is managed by this composable. It will delete the instance when it
 * falls out of scope.
 *
 * @param file The [RiveFile] to create the view model instance from.
 * @param source The source of the view model instance. Constructed from [ViewModelSource] combined
 *    with [ViewModelInstanceSource]. If none is provided, the default artboard for the file will be
 *    created, and the default view model and view model instance will be used.
 *
 * If you already have an artboard, prefer to use [ViewModelSource.DefaultForArtboard], since that
 * will avoid instantiating another artboard.
 *
 * This is the equivalent of "auto-binding" in other SDKs.
 *
 * @return The created [ViewModelInstance].
 */
@ExperimentalRiveComposeAPI
@Composable
fun rememberViewModelInstance(
    file: RiveFile,
    source: ViewModelInstanceSource? = null,
): ViewModelInstance {
    val sourceToUse =
        source ?: ViewModelSource.DefaultForArtboard(rememberArtboard(file)).defaultInstance()

    val instance = remember(file, sourceToUse) {
        ViewModelInstance.fromFile(file, sourceToUse)
    }

    DisposableEffect(instance) {
        onDispose { instance.close() }
    }

    return instance
}

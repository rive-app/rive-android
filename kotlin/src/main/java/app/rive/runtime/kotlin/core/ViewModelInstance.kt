package app.rive.runtime.kotlin.core

import android.graphics.Color
import androidx.annotation.WorkerThread
import app.rive.runtime.kotlin.core.errors.ViewModelException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents an instantiated set of properties on a ViewModel. With this class you have access to
 * the individual properties to get and set values from bindings.
 *
 * Before the property modifications have any effect, you need to assign the instance to an artboard
 * with [Artboard.viewModelInstance].
 *
 * Instances can be created from one [File] and applied to another. See [transfer] for more
 * information.
 *
 * @param unsafeCppPointer Pointer to the C++ counterpart.
 */
class ViewModelInstance internal constructor(unsafeCppPointer: Long) :
    NativeObject(unsafeCppPointer) {
    private external fun cppName(cppPointer: Long): String
    private external fun cppPropertyNumber(cppPointer: Long, path: String): Long
    private external fun cppPropertyString(cppPointer: Long, path: String): Long
    private external fun cppPropertyBoolean(cppPointer: Long, path: String): Long
    private external fun cppPropertyColor(cppPointer: Long, path: String): Long
    private external fun cppPropertyEnum(cppPointer: Long, path: String): Long
    private external fun cppPropertyTrigger(cppPointer: Long, path: String): Long
    private external fun cppPropertyInstance(cppPointer: Long, path: String): Long
    private external fun cppSetInstanceProperty(
        cppPointer: Long,
        path: String,
        instancePointer: Long
    ): Boolean

    private external fun cppRefInstance(cppPointer: Long)
    private external fun cppDerefInstance(cppPointer: Long)

    private var properties: MutableMap<String, ViewModelProperty<*>> = mutableMapOf()
    private var children: MutableMap<String, ViewModelInstance> = mutableMapOf()

    init {
        // Keep an extra reference to the instance. Cleaned up in cppDelete.
        // This is to facilitate the transfer use case where a user holds an instance after its
        // originating file has been deleted.
        cppRefInstance(cppPointer)
    }

    // Un-ref the extra reference created in init
    override fun cppDelete(pointer: Long) = cppDerefInstance(pointer)

    /** Get the [name] of the viewmodel instance. */
    val name: String
        get() = cppName(cppPointer)

    /**
     * Poll all subscribed properties for changes. This is called from advance, and is therefore
     * running on the worker thread.
     */
    @WorkerThread
    internal fun pollChanges() {
        properties.values.filter { it.isSubscribed }.forEach { it.pollChanges() }
        children.values.forEach { it.pollChanges() }
    }

    /**
     * Get a named [number property][ViewModelNumberProperty].
     *
     * @param path The path to the property. Normally this is just the name of the property, but if
     *    it is in a nested [ViewModelInstance], you can refer to it from a higher level with `/`
     *    characters as delimiters, e.g. `"My Nested VM Property/My Nested Number Property"`.
     * @throws ViewModelException If [path] does not refer to a valid property or if the property is
     *    not a number property.
     */
    @Throws(ViewModelException::class)
    fun getNumberProperty(path: String): ViewModelNumberProperty =
        getProperty(path, ::cppPropertyNumber, ::ViewModelNumberProperty)

    /**
     * Get a named [string property][ViewModelStringProperty].
     *
     * @throws ViewModelException If [path] does not refer to a valid property or if the property is
     *    not a string property.
     * @see getNumberProperty
     */
    @Throws(ViewModelException::class)
    fun getStringProperty(path: String): ViewModelStringProperty =
        getProperty(path, ::cppPropertyString, ::ViewModelStringProperty)

    /**
     * Get a named [boolean property][ViewModelBooleanProperty].
     *
     * @throws ViewModelException If [path] does not refer to a valid property or if the property is
     *    not a boolean property.
     * @see getNumberProperty
     */
    @Throws(ViewModelException::class)
    fun getBooleanProperty(path: String): ViewModelBooleanProperty =
        getProperty(path, ::cppPropertyBoolean, ::ViewModelBooleanProperty)

    /**
     * Get a named [color property][ViewModelColorProperty].
     *
     * Numbers are represented as integers in 0xAARRGGBB format.
     *
     * @throws ViewModelException If [path] does not refer to a valid property or if the property is
     *    not a color property.
     * @see getNumberProperty
     */
    @Throws(ViewModelException::class)
    fun getColorProperty(path: String): ViewModelColorProperty =
        getProperty(path, ::cppPropertyColor, ::ViewModelColorProperty)

    /**
     * Get a named [enum property][ViewModelEnumProperty].
     *
     * Enums are represented as strings.
     *
     * @throws ViewModelException If [path] does not refer to a valid property or if the property is
     *    not an enum property.
     * @see getNumberProperty
     */
    @Throws(ViewModelException::class)
    fun getEnumProperty(path: String): ViewModelEnumProperty =
        getProperty(path, ::cppPropertyEnum, ::ViewModelEnumProperty)

    /**
     * Get a named [trigger property][ViewModelTriggerProperty].
     *
     * @throws ViewModelException If [path] does not refer to a valid property or if the property is
     *    not a trigger property.
     * @see getNumberProperty
     */
    @Throws(ViewModelException::class)
    fun getTriggerProperty(path: String): ViewModelTriggerProperty =
        getProperty(path, ::cppPropertyTrigger, ::ViewModelTriggerProperty)


    /**
     * Get a nested, named [ViewModelInstance][ViewModelInstance].
     *
     * Note that unlike other properties, instances aren't changed with a `value` member. However
     * they can be reassigned with [setInstanceProperty].
     *
     * @throws ViewModelException If [path] does not refer to a valid property.
     * @see getNumberProperty
     */
    @Throws(ViewModelException::class)
    fun getInstanceProperty(path: String): ViewModelInstance = traverse(path.split("/"))

    /**
     * Set a nested, named [ViewModelInstance][ViewModelInstance].
     *
     * @param path The path to the property. Normally this is just the name of the property, but if
     *    it is in a nested [ViewModelInstance], you can refer to it from a higher level with /
     *    characters as delimiters, e.g. "My Nested VM Property/My Nested Number Property".
     * @param instance The new instance to set.
     * @throws ViewModelException If the new instance is incompatible with the property's view model
     *    definition, i.e. it does not have the same properties defined; or if the property path
     *    does not refer to a valid property.
     */
    @Throws(ViewModelException::class)
    fun setInstanceProperty(path: String, instance: ViewModelInstance) {
        val pathParts = path.split("/")
        val nestedParts = pathParts.subList(0, pathParts.size - 1)
        val propertyName = pathParts.last()
        val parentInstance = traverse(nestedParts)

        if (!cppSetInstanceProperty(parentInstance.cppPointer, propertyName, instance.cppPointer)) {
            throw ViewModelException("Property not found: $path; or instance is incompatible.")
        }

        // Update the parent's cache
        parentInstance.children[propertyName] = instance
    }

    // `reified T` avoids type erasure so that we can type check the cached property.
    // `inline` and `crossinline` are necessary to allow the use of reified type parameters in lambdas.
    @Throws(ViewModelException::class)
    private inline fun <reified T : ViewModelProperty<*>> getProperty(
        path: String,
        crossinline cppGetPropertyFn: (Long, String) -> Long,
        crossinline constructor: (Long) -> T
    ): T {
        val pathParts = path.split("/")
        val nestedParts = pathParts.subList(0, pathParts.size - 1)
        val propertyName = pathParts.last()
        val finalInstance = traverse(nestedParts)

        // Check if the property is already cached
        return finalInstance.properties[propertyName]?.let { cachedProperty ->
            if (cachedProperty !is T) {
                throw ViewModelException("Property '$propertyName' exists but is not of the expected type.")
            }
            cachedProperty
        } ?: run {
            // Else create a new property
            val propertyPointer = cppGetPropertyFn(finalInstance.cppPointer, propertyName)
            if (propertyPointer == NULL_POINTER) {
                throw ViewModelException("Property not found: $path")
            }

            val property = constructor(propertyPointer)
            finalInstance.properties[propertyName] = property
            dependencies.add(property)

            property
        }
    }

    // Recursive function, given a path, to get instances
    @Throws(ViewModelException::class)
    private fun traverse(parts: List<String>): ViewModelInstance {
        // Base case: if there are no more parts, return this instance
        if (parts.isEmpty()) {
            return this
        }

        val childName = parts.first()

        // When not found in the cache, create a new instance
        fun createChildInstance(): ViewModelInstance {
            val childPointer = cppPropertyInstance(cppPointer, childName)
            if (childPointer == NULL_POINTER) {
                throw ViewModelException("Property not found: $childName")
            }
            val child = ViewModelInstance(childPointer)

            children[childName] = child
            dependencies.add(child)

            return child
        }

        // Get from the cache or create a new instance
        val child = children.getOrPut(childName) { createChildInstance() }

        // Recurse
        val childParts = parts.subList(1, parts.size)
        return child.traverse(childParts)
    }

    /**
     * Wrapper type for transferring a view model instance between [Files][File].
     *
     * Exists to encode reference counting operations at the type-level.
     *
     * @see [Artboard.viewModelInstance]
     */
    class Transfer internal constructor(private val instance: ViewModelInstance) {
        private var valid: Boolean = true

        init {
            if (instance.refCount <= 0) {
                throw ViewModelException("Cannot transfer a disposed ViewModelInstance.")
            }

            instance.cppRefInstance(instance.cppPointer)
            instance.acquire()
        }

        /**
         * Dispose of the transfer. This will release the C++ reference to the instance. Call this
         * manually to abort a transfer.
         *
         * Note: this operation is not idempotent. Calling this multiple times will throw an error.
         *
         * @throws [ViewModelException] if the transfer has already ended.
         */
        @Throws(ViewModelException::class)
        fun dispose() {
            if (!valid) {
                throw ViewModelException("Transfer of ViewModelInstance $instance already ended. Cannot dispose.")
            }

            valid = false
            instance.release()
        }

        /**
         * End the transfer, getting the original [ViewModelInstance] back. Used internally when
         * transferring ownership to an artboard or state machine.
         *
         * Note: this operation is not idempotent. Calling this multiple times will throw an error.
         *
         * @return The original [ViewModelInstance] that was transferred.
         * @throws [ViewModelException] if the transfer has already ended.
         */
        @Throws(ViewModelException::class)
        internal fun end(): ViewModelInstance {
            if (!valid) {
                throw ViewModelException("Transfer of ViewModelInstance $instance already ended. Cannot end transfer again.")
            }

            valid = false
            return instance
        }
    }

    /**
     * Start a transfer of this [ViewModelInstance] to another [File]. This is necessary because
     * normally the C++ lifetime of the instance is managed by the File it belongs to. When
     * transferring, the instance needs to survive the possible deletion of its owning File.
     *
     * You can omit this if you're certain that the originating File will not be deleted before this
     * instance is assigned.
     *
     * Important: once created, you *must* call [Transfer.end] or assign the instance to a
     * [StateMachineInstance] or [Artboard], otherwise the internal C++ reference count will leak.
     *
     * @throws [ViewModelException] if the instance has already been disposed.
     * @see [Artboard.viewModelInstance]
     */
    fun transfer(): Transfer = Transfer(this)
}

/**
 * A property of type [T] of a [ViewModelInstance]. Use [value] to mutate the property. use
 * [valueFlow] to subscribe to changes on the value.
 */
abstract class ViewModelProperty<T>(unsafeCppPointer: Long) : NativeObject(unsafeCppPointer) {
    private external fun cppHasChanged(cppPointer: Long): Boolean
    private external fun cppFlushChanges(cppPointer: Long): Boolean

    /**
     * The current value of the property, whether set by this property or by a data binding update.
     */
    var value: T
        get() = _valueFlow.value
        set(value) {
            nativeSetValue(value)
            _valueFlow.value = value
        }

    protected abstract fun nativeGetValue(): T
    protected abstract fun nativeSetValue(value: T)

    private val _valueFlow = MutableStateFlow(nativeGetValue())
    internal val isSubscribed: Boolean
        get() = _valueFlow.subscriptionCount.value > 0

    /** A flow of the property's value. Use for observing changes. */
    val valueFlow = _valueFlow.asStateFlow()

    internal fun pollChanges() {
        if (cppHasChanged(cppPointer)) {
            _valueFlow.value = nativeGetValue()
            cppFlushChanges(cppPointer)
        }
    }
}

/** A number property of a [ViewModelInstance]. Use [value] to mutate the property. */
class ViewModelNumberProperty(unsafeCppPointer: Long) : ViewModelProperty<Float>(unsafeCppPointer) {
    private external fun cppGetValue(cppPointer: Long): Float
    private external fun cppSetValue(cppPointer: Long, value: Float)

    override fun nativeGetValue(): Float = cppGetValue(cppPointer)
    override fun nativeSetValue(value: Float) = cppSetValue(cppPointer, value)
}

/** @see ViewModelNumberProperty */
class ViewModelStringProperty(unsafeCppPointer: Long) :
    ViewModelProperty<String>(unsafeCppPointer) {
    private external fun cppGetValue(cppPointer: Long): String
    private external fun cppSetValue(cppPointer: Long, value: String)

    override fun nativeGetValue(): String = cppGetValue(cppPointer)
    override fun nativeSetValue(value: String) = cppSetValue(cppPointer, value)
}

/** @see ViewModelNumberProperty */
class ViewModelBooleanProperty(unsafeCppPointer: Long) :
    ViewModelProperty<Boolean>(unsafeCppPointer) {

    private external fun cppGetValue(cppPointer: Long): Boolean
    private external fun cppSetValue(cppPointer: Long, value: Boolean)

    override fun nativeGetValue(): Boolean = cppGetValue(cppPointer)
    override fun nativeSetValue(value: Boolean) = cppSetValue(cppPointer, value)
}

/**
 * A color property of a [ViewModelInstance]. Values are represented as integers in 0xAARRGGBB
 * format.
 *
 * The Android [Color] class is not available prior to API 26, and because this library has minSDK
 * 21, we use integers. If you are using API 26+, you can convert to Color with [Color.valueOf]
 * and to an Int with [Color.toArgb]. If you are supporting prior to API 26, you can still use
 * [Color.argb] to handle the bit manipulations.
 *
 * @see ViewModelNumberProperty
 */
class ViewModelColorProperty(unsafeCppPointer: Long) :
    ViewModelProperty<Int>(unsafeCppPointer) {

    private external fun cppGetValue(cppPointer: Long): Int
    private external fun cppSetValue(cppPointer: Long, value: Int)

    override fun nativeGetValue(): Int = cppGetValue(cppPointer)
    override fun nativeSetValue(value: Int) = cppSetValue(cppPointer, value)
}

/**
 * An enum property of a [ViewModelInstance]. Values are represented as strings.
 *
 * @see ViewModelNumberProperty
 */
class ViewModelEnumProperty(unsafeCppPointer: Long) :
    ViewModelProperty<String>(unsafeCppPointer) {

    private external fun cppGetValue(cppPointer: Long): String
    private external fun cppSetValue(cppPointer: Long, value: String)

    override fun nativeGetValue(): String = cppGetValue(cppPointer)
    override fun nativeSetValue(value: String) = cppSetValue(cppPointer, value)
}

/** A trigger property of a [ViewModelInstance]. Use [trigger] fire the trigger. */
class ViewModelTriggerProperty(unsafeCppPointer: Long) :
    ViewModelProperty<ViewModelTriggerProperty.TriggerUnit>(unsafeCppPointer) {

    /**
     * A type similar to [Unit] for triggers. Unlike [Unit], this type can have unique instances to
     * trigger [kotlinx.coroutines.flow.StateFlow] updates.
     *
     * @see ViewModelTriggerProperty
     */
    class TriggerUnit

    private external fun cppTrigger(cppPointer: Long)

    // Return Unit, as triggers don't have a value.
    override fun nativeGetValue(): TriggerUnit = TriggerUnit()

    // No-op, triggers don't have a value to set.
    override fun nativeSetValue(value: TriggerUnit) {}

    fun trigger() = cppTrigger(cppPointer)
}

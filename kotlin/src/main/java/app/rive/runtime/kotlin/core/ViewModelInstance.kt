package app.rive.runtime.kotlin.core

import android.graphics.Color
import androidx.annotation.VisibleForTesting
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
    private external fun cppPropertyImage(cppPointer: Long, path: String): Long
    private external fun cppPropertyList(cppPointer: Long, path: String): Long
    private external fun cppPropertyArtboard(cppPointer: Long, path: String): Long
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

    /** Get the [name] of the view model instance. */
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
     * Get a named [image property][ViewModelImageProperty].
     *
     * @throws ViewModelException If [path] does not refer to a valid property or if the property is
     *    not an image property.
     * @see getNumberProperty
     */
    @Throws(ViewModelException::class)
    fun getImageProperty(path: String): ViewModelImageProperty =
        getProperty(path, ::cppPropertyImage, ::ViewModelImageProperty)

    /**
     * Get a named [list property][ViewModelListProperty].
     *
     * @throws ViewModelException If [path] does not refer to a valid property or if the property is
     *    not a list property.
     * @see getNumberProperty
     */
    @Throws(ViewModelException::class)
    fun getListProperty(path: String): ViewModelListProperty =
        getProperty(path, ::cppPropertyList, ::ViewModelListProperty)

    @Throws(ViewModelException::class)
    fun getArtboardProperty(path: String): ViewModelArtboardProperty =
        getProperty(path, ::cppPropertyArtboard, ::ViewModelArtboardProperty)

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
    external fun cppName(cppPointer: Long): String

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    external fun cppHasChanged(cppPointer: Long): Boolean
    private external fun cppFlushChanges(cppPointer: Long): Boolean

    /** The name of the property. */
    val name: String
        get() = cppName(cppPointer)

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

/**
 * An image property of a [ViewModelInstance]. Values are [RiveRenderImage] type, and must be
 * constructed from encoded bytes with [RiveRenderImage.make].
 *
 * Unlike other property types, this property can only be set with [set]. It cannot be read or
 * observed. It's [value] and [valueFlow] are not applicable and will always return [Unit].
 *
 * Setting to null will remove the image and release the reference to the image.
 */
class ViewModelImageProperty(unsafeCppPointer: Long) : ViewModelProperty<Unit>(unsafeCppPointer) {
    private external fun cppSetValue(cppPointer: Long, value: Long)

    fun set(image: RiveRenderImage?) = cppSetValue(cppPointer, image?.cppPointer ?: NULL_POINTER)

    // Return Unit, as images don't have a value to get.
    override fun nativeGetValue() = Unit

    // No-op, images are set with `set`, not `value`.
    override fun nativeSetValue(value: Unit) {}
}

/**
 * A list property of [ViewModelInstance]s.
 *
 * The list is mutable, and can be modified at runtime using [add], [remove], [removeAt], and
 * [swap].
 *
 * The list does not have one particular value to get or set, so [value] and [valueFlow] are not
 * applicable and will always return [Unit].
 */
class ViewModelListProperty(unsafeCppPointer: Long) : ViewModelProperty<Unit>(unsafeCppPointer) {
    private external fun cppSize(cppPointer: Long): Int
    private external fun cppElementAt(cppPointer: Long, index: Int): Long
    private external fun cppAdd(cppPointer: Long, itemPointer: Long)
    private external fun cppAddAt(cppPointer: Long, index: Int, itemPointer: Long)
    private external fun cppRemove(cppPointer: Long, itemPointer: Long)
    private external fun cppRemoveAt(cppPointer: Long, index: Int)
    private external fun cppSwap(cppPointer: Long, index1: Int, index2: Int)

    private data class CacheEntry(
        val instance: ViewModelInstance,
        var count: Int
    )

    private var cachedItems: MutableMap<Long, CacheEntry> = mutableMapOf()

    override fun cppDelete(pointer: Long) {
        super.cppDelete(pointer)

        // Release all cached items when the list is deleted
        cachedItems.values.forEach { it.instance.release() }
        cachedItems.clear()
    }

    @Throws(IndexOutOfBoundsException::class)
    private fun boundsCheck(index: Int) {
        if (index < 0 || index >= size) {
            throw IndexOutOfBoundsException("Index out of bounds for ViewModelListProperty.")
        }
    }

    /** The number of items in the list. */
    val size: Int
        get() = cppSize(cppPointer)

    /**
     * Get the [ViewModelInstance] at the specified [index].
     *
     * @throws IndexOutOfBoundsException if [index] is out of bounds.
     */
    @Throws(IndexOutOfBoundsException::class)
    fun elementAt(index: Int): ViewModelInstance {
        boundsCheck(index)

        val cppPointer = cppElementAt(cppPointer, index)
        // Check if the item is cached
        return cachedItems[cppPointer]?.instance ?: run {
            /**
             * If not cached, create a new instance and cache it. The instance will have a reference
             * count of 1, which will be released when either the list is deleted or the item is
             * removed from the list.
             */
            val newItem = ViewModelInstance(cppPointer)
            cachedItems[cppPointer] = CacheEntry(newItem, count = 1)
            newItem
        }
    }

    operator fun get(index: Int): ViewModelInstance = elementAt(index)

    /**
     * Append the [ViewModelInstance] to the end of the list.
     *
     * @throws IllegalArgumentException if [item] is disposed.
     */
    @Throws(IllegalArgumentException::class)
    fun add(item: ViewModelInstance) {
        require(item.hasCppObject) { "Cannot add a disposed ViewModelProperty to ViewModelListProperty." }

        cachedItems.getOrPut(item.cppPointer) {
            item.acquire()
            CacheEntry(item, count = 0)
        }.count++

        cppAdd(cppPointer, item.cppPointer)
    }

    /**
     * Insert the [ViewModelInstance] at the specified [index]. The item currently at that index and
     * all subsequent items will be shifted one position to the right.
     *
     * @throws IndexOutOfBoundsException if [index] is out of bounds.
     * @throws IllegalArgumentException if [item] is disposed.
     */
    @Throws(IndexOutOfBoundsException::class, IllegalArgumentException::class)
    fun add(index: Int, item: ViewModelInstance) {
        boundsCheck(index)
        require(item.hasCppObject) { "Cannot add a disposed ViewModelProperty to ViewModelListProperty." }

        cachedItems.getOrPut(item.cppPointer) {
            item.acquire()
            CacheEntry(item, count = 0)
        }.count++

        cppAddAt(cppPointer, index, item.cppPointer)
    }

    /**
     * Remove all instances of [ViewModelInstance] from the list.
     *
     * @throws IllegalArgumentException if [item] is disposed.
     */
    @Throws(IllegalArgumentException::class)
    fun remove(item: ViewModelInstance) {
        require(item.hasCppObject) { "Cannot remove a disposed ViewModelProperty from ViewModelListProperty." }

        cachedItems.remove(item.cppPointer)?.also { it.instance.release() }

        cppRemove(cppPointer, item.cppPointer)
    }

    /**
     * Remove the item at the specified [index] from the list. The items after the removed item will
     * be shifted one position to the left.
     *
     * @throws IndexOutOfBoundsException if [index] is out of bounds.
     */
    @Throws(IndexOutOfBoundsException::class)
    fun removeAt(index: Int) {
        boundsCheck(index)

        val itemPointer = cppElementAt(cppPointer, index)
        cachedItems[itemPointer]?.let { entry ->
            // Decrement the count and release the instance if it reaches zero
            if (--entry.count == 0) {
                cachedItems.remove(itemPointer)?.also { it.instance.release() }
            }
        }

        cppRemoveAt(cppPointer, index)
    }

    /**
     * Swap the items at indices [index1] and [index2].
     *
     * @throws IndexOutOfBoundsException if either index is out of bounds.
     */
    @Throws(IndexOutOfBoundsException::class)
    fun swap(index1: Int, index2: Int) {
        boundsCheck(index1)
        boundsCheck(index2)

        if (index1 == index2) {
            // No-op, swapping the same index does nothing.
            return
        }
        cppSwap(cppPointer, index1, index2)
    }

    // Return Unit, as lists don't have a value to get.
    override fun nativeGetValue() = Unit

    // No-op, lists don't have a value to set.
    override fun nativeSetValue(value: Unit) {}
}

/**
 * An artboard property of a [ViewModelInstance].
 *
 * Unlike other property types, this property can only be [set]. It cannot be read or observed. It's
 * [value] and [valueFlow] are not applicable and will always return [Unit].
 */
class ViewModelArtboardProperty(unsafeCppPointer: Long) :
    ViewModelProperty<Unit>(unsafeCppPointer) {

    private external fun cppSetValue(cppPointer: Long, value: Long)

    fun set(artboard: Artboard) = cppSetValue(cppPointer, artboard.cppPointer)

    // Return Unit, as artboards don't have a value to get.
    override fun nativeGetValue() = Unit

    // No-op, artboards are set with `set`, not `value`.
    override fun nativeSetValue(value: Unit) {}
}

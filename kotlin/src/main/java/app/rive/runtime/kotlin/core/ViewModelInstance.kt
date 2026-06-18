package app.rive.runtime.kotlin.core

import android.graphics.Color
import androidx.annotation.OpenForTesting
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import app.rive.runtime.kotlin.core.errors.RiveException
import app.rive.runtime.kotlin.core.errors.ViewModelException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * File lock migration can race with another migration. If two threads move wrappers A -> B and B ->
 * A, taking "old then new" can deadlock; if the volatile field changes while a thread waits, it can
 * also update without holding the current lock. Take old/new locks in identity order and retry if
 * the current lock changed before both locks were held.
 *
 * @param currentFileLock Function that returns the wrapper's volatile file lock field. It must
 *    re-read the field (i.e. call the function), rather than capture a lock value, so retry
 *    validation can detect if another thread changed the lock while this thread waited.
 * @param newFileLock The file lock this wrapper should move to.
 * @param setFileLock Sets the wrapper's file lock field once the old and new locks are held. The
 *    argument is the new file lock.
 * @param snapshotOfDependents Captures any child wrappers that must be updated after releasing
 *    old/new locks. This is a function so the snapshot is taken after this helper has acquired both
 *    migration locks and confirmed the wrapper still uses the expected old lock. Recursive child
 *    updates may need to acquire their own old/new file locks, so doing that work inside this
 *    helper's ordered lock section can reintroduce deadlock risk. Snapshotting while both locks are
 *    held keeps the cache view consistent, then lets callers propagate the migration without
 *    holding this wrapper's migration locks.
 * @return The [snapshotOfDependents] result when the lock changed, or `null` when [newFileLock] is
 *    already current.
 */
private inline fun <T> updateFileLockWithRetry(
    currentFileLock: () -> ReentrantLock,
    newFileLock: ReentrantLock,
    crossinline setFileLock: (ReentrantLock) -> Unit,
    crossinline snapshotOfDependents: () -> T
): T? {
    while (true) {
        val oldFileLock = currentFileLock()
        if (oldFileLock === newFileLock) {
            return null
        }

        var updated = false
        var result: T? = null
        withFileLocksInOrder(oldFileLock, newFileLock) {
            if (currentFileLock() === oldFileLock) {
                setFileLock(newFileLock)
                result = snapshotOfDependents()
                updated = true
            }
        }

        if (updated) {
            return result
        }
    }
}

/**
 * Shared monitor used only when two different file locks have the same identity hash code.
 *
 * [withFileLocksInOrder] normally orders lock acquisition by [System.identityHashCode]. Hash
 * collisions are rare but possible, so colliding pairs synchronize on this object first to ensure
 * all threads still acquire the two file locks in one consistent order.
 */
private val fileLockUpdateTieLock = Any()

/**
 * Runs [block] while holding [firstLock] and [secondLock] in deterministic order.
 *
 * This prevents deadlocks when two threads migrate wrappers between the same pair of file locks in
 * opposite directions.
 *
 * @param firstLock One file lock to hold.
 * @param secondLock The other file lock to hold.
 * @param block Work to run after both locks are held.
 */
private inline fun withFileLocksInOrder(
    firstLock: ReentrantLock,
    secondLock: ReentrantLock,
    block: () -> Unit
) {
    if (firstLock === secondLock) {
        synchronized(firstLock) { block() }
        return
    }

    val firstHash = System.identityHashCode(firstLock)
    val secondHash = System.identityHashCode(secondLock)
    when {
        firstHash < secondHash -> synchronized(firstLock) { synchronized(secondLock) { block() } }
        firstHash > secondHash -> synchronized(secondLock) { synchronized(firstLock) { block() } }
        else -> synchronized(fileLockUpdateTieLock) {
            synchronized(firstLock) { synchronized(secondLock) { block() } }
        }
    }
}

/**
 * Runs [block] while holding the wrapper's current file lock.
 *
 * File-lock migration can change the volatile lock field after a thread reads it but before that
 * thread enters the monitor. Retrying after acquisition ensures [block] only runs under the lock
 * that is still current.
 *
 * @param currentFileLock Function that returns the wrapper's volatile file lock field.
 * @param block Work to run after the current lock is held.
 * @return The value returned by [block].
 */
private inline fun <R> withCurrentFileLock(
    crossinline currentFileLock: () -> ReentrantLock,
    block: () -> R
): R {
    while (true) {
        val lock = currentFileLock()
        synchronized(lock) {
            if (currentFileLock() === lock) {
                return block()
            }
        }
    }
}

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
 * @param fileLock Lock shared by the [File] and native graph this instance belongs to. Updated
 *    through [updateFileLock] when assigned to another native graph.
 */
@OpenForTesting
class ViewModelInstance internal constructor(
    unsafeCppPointer: Long,
    @Volatile private var fileLock: ReentrantLock
) :
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

    protected var properties: MutableMap<String, ViewModelProperty<*>> = ConcurrentHashMap()
    protected var children: MutableMap<String, ViewModelInstance> = ConcurrentHashMap()

    init {
        // Keep an extra reference to the instance. Cleaned up in cppDelete.
        // This is to facilitate the transfer use case where a user holds an instance after its
        // originating file has been deleted.
        synchronized(fileLock) { cppRefInstance(cppPointer) }
    }

    // Un-ref the extra reference created in init
    override fun cppDelete(pointer: Long) = synchronized(fileLock) { cppDerefInstance(pointer) }

    /**
     * Release this instance while holding the current file lock.
     *
     * [NativeObject.release] is synchronized on this object and may call [cppDelete], which also
     * needs the file lock. Taking the file lock first keeps disposal ordered as file lock -> object
     * monitor -> native delete, matching other file-locked native wrappers.
     *
     * @return The new reference count.
     * @throws IllegalArgumentException if this instance has already been released.
     */
    @Throws(IllegalArgumentException::class)
    override fun release(): Int = withCurrentFileLock({ fileLock }) { super.release() }

    /**
     * Updates this instance and any cached child/property wrappers to synchronize on [newFileLock].
     *
     * This is used when an instance is bound to an [Artboard] or [StateMachineInstance]. A
     * [ViewModelInstance] can be created from one [File] and later transferred to another, so the
     * instance's original file lock may not be the lock that protects the native graph it now
     * mutates. Adopting the target file lock serializes property writes with frame advancement.
     *
     * @param newFileLock The file lock for the native graph this instance is currently bound to.
     */
    internal fun updateFileLock(newFileLock: ReentrantLock) {
        val cachedDependents = updateFileLockWithRetry(
            currentFileLock = { fileLock },
            newFileLock = newFileLock,
            setFileLock = { fileLock = it },
            snapshotOfDependents = { properties.values.toList() to children.values.toList() }
        ) ?: return

        cachedDependents.first.forEach { it.updateFileLock(newFileLock) }
        cachedDependents.second.forEach { it.updateFileLock(newFileLock) }
    }

    /** Get the [name] of the view model instance. */
    val name: String
        get() = cppName(cppPointer)

    /**
     * Poll all properties for changes. This is called from advance, and is therefore running on the
     * worker thread.
     */
    @WorkerThread
    internal fun pollChanges() {
        synchronized(fileLock) {
            properties.values.forEach { it.pollChanges() }
            children.values.forEach { it.pollChanges() }
        }
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
        val destinationFileLock = parentInstance.fileLock
        instance.updateFileLock(destinationFileLock)

        synchronized(destinationFileLock) {
            if (!cppSetInstanceProperty(
                    parentInstance.cppPointer,
                    propertyName,
                    instance.cppPointer
                )
            ) {
                throw ViewModelException("Property not found: $path; or instance is incompatible.")
            }

            // Update the parent's cache
            parentInstance.children[propertyName] = instance
        }
    }

    // `reified T` avoids type erasure so that we can type check the cached property.
    // `inline` and `crossinline` are necessary to allow the use of reified type parameters in lambdas.
    @Throws(ViewModelException::class)
    private inline fun <reified T : ViewModelProperty<*>> getProperty(
        path: String,
        crossinline cppGetPropertyFn: (Long, String) -> Long,
        crossinline constructor: (Long, ReentrantLock) -> T
    ): T {
        val pathParts = path.split("/")
        val nestedParts = pathParts.subList(0, pathParts.size - 1)
        val propertyName = pathParts.last()
        val finalInstance = traverse(nestedParts)

        // Check if the property is already cached
        synchronized(finalInstance.fileLock) {
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

                val property = constructor(propertyPointer, finalInstance.fileLock)
                finalInstance.properties[propertyName] = property
                dependencies.add(property)

                property
            }
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
            val childPointer = synchronized(fileLock) { cppPropertyInstance(cppPointer, childName) }
            if (childPointer == NULL_POINTER) {
                throw ViewModelException("Property not found: $childName")
            }
            val child = ViewModelInstance(childPointer, fileLock)

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

            synchronized(instance.fileLock) { instance.cppRefInstance(instance.cppPointer) }
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
 *
 * @param unsafeCppPointer Pointer to the C++ counterpart.
 * @param fileLock Lock shared by the [File] and native graph this property mutates. Subclasses use
 *    it to synchronize native access and to migrate dependent wrappers in [updateFileLock]. Writes
 *    should be limited to [updateFileLock] implementations.
 */
abstract class ViewModelProperty<T>(
    unsafeCppPointer: Long,
    @Volatile protected var fileLock: ReentrantLock
) : NativeObject(unsafeCppPointer) {
    external fun cppName(cppPointer: Long): String

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    external fun cppHasChanged(cppPointer: Long): Boolean
    private external fun cppFlushChanges(cppPointer: Long): Boolean

    /**
     * Release this property while holding the current file lock.
     *
     * [NativeObject.release] is synchronized on this object and subclasses may enter [fileLock]
     * during [cppDelete]. Taking the file lock first keeps disposal ordered as file lock -> object
     * monitor -> native delete.
     *
     * @return The new reference count.
     * @throws IllegalArgumentException if this property has already been released.
     */
    @Throws(IllegalArgumentException::class)
    override fun release(): Int = withLock { super.release() }

    /**
     * Updates this property wrapper to synchronize native access on [newFileLock].
     *
     * Properties may be cached before their [ViewModelInstance] is assigned to an artboard or state
     * machine. When that assignment happens, the property must adopt the same file lock as frame
     * advancement so calls such as [value] writes, [pollChanges], and type-specific setters do not
     * race native data-binding updates.
     *
     * @param newFileLock The file lock for the native graph this property currently mutates.
     */
    internal open fun updateFileLock(newFileLock: ReentrantLock) {
        updateFileLockWithRetry(
            currentFileLock = { fileLock },
            newFileLock = newFileLock,
            setFileLock = { fileLock = it },
            snapshotOfDependents = {}
        )
    }

    /**
     * Run [block] while holding the current file lock.
     *
     * Subclasses should use this for native reads or writes that can overlap with frame advancement
     * or data-binding updates.
     *
     * @param block Work to run after the current file lock is held.
     * @return The value returned by [block].
     */
    protected fun <R> withLock(block: () -> R): R =
        withCurrentFileLock({ fileLock }, block)

    /** The name of the property. */
    val name: String
        get() = cppName(cppPointer)

    /**
     * The current value of the property, whether set by this property or by a data binding update.
     */
    var value: T
        get() = _valueFlow.value
        set(value) {
            withLock { nativeSetValue(value) }
            _valueFlow.value = value
        }

    protected abstract fun nativeGetValue(): T
    protected abstract fun nativeSetValue(value: T)

    private val _valueFlow = MutableStateFlow(withLock { nativeGetValue() })
    internal val isSubscribed: Boolean
        get() = _valueFlow.subscriptionCount.value > 0

    /** A flow of the property's value. Use for observing changes. */
    val valueFlow = _valueFlow.asStateFlow()

    internal open fun pollChanges() = withLock {
        if (cppHasChanged(cppPointer)) {
            _valueFlow.value = nativeGetValue()
            cppFlushChanges(cppPointer)
        }
    }
}

/**
 * A number property of a [ViewModelInstance]. Use [value] to mutate the property.
 *
 * @param unsafeCppPointer Pointer to the C++ counterpart.
 * @param fileLock Lock shared by the [File] and native graph this property mutates. Updated through
 *    [ViewModelProperty.updateFileLock] when assigned to another native graph.
 */
class ViewModelNumberProperty(
    unsafeCppPointer: Long,
    fileLock: ReentrantLock
) : ViewModelProperty<Float>(unsafeCppPointer, fileLock) {
    private external fun cppGetValue(cppPointer: Long): Float
    private external fun cppSetValue(cppPointer: Long, value: Float)

    override fun nativeGetValue(): Float = cppGetValue(cppPointer)
    override fun nativeSetValue(value: Float) = cppSetValue(cppPointer, value)
}

/** @see ViewModelNumberProperty */
class ViewModelStringProperty(
    unsafeCppPointer: Long,
    fileLock: ReentrantLock
) : ViewModelProperty<String>(unsafeCppPointer, fileLock) {
    private external fun cppGetValue(cppPointer: Long): String
    private external fun cppSetValue(cppPointer: Long, value: String)

    override fun nativeGetValue(): String = cppGetValue(cppPointer)
    override fun nativeSetValue(value: String) = cppSetValue(cppPointer, value)
}

/** @see ViewModelNumberProperty */
class ViewModelBooleanProperty(
    unsafeCppPointer: Long,
    fileLock: ReentrantLock
) : ViewModelProperty<Boolean>(unsafeCppPointer, fileLock) {

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
class ViewModelColorProperty(
    unsafeCppPointer: Long,
    fileLock: ReentrantLock
) : ViewModelProperty<Int>(unsafeCppPointer, fileLock) {

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
class ViewModelEnumProperty(
    unsafeCppPointer: Long,
    fileLock: ReentrantLock
) : ViewModelProperty<String>(unsafeCppPointer, fileLock) {

    private external fun cppGetValue(cppPointer: Long): String
    private external fun cppSetValue(cppPointer: Long, value: String)

    override fun nativeGetValue(): String = cppGetValue(cppPointer)
    override fun nativeSetValue(value: String) = cppSetValue(cppPointer, value)
}

/**
 * A trigger property of a [ViewModelInstance]. Use [trigger] fire the trigger.
 *
 * @see ViewModelNumberProperty
 */
class ViewModelTriggerProperty(
    unsafeCppPointer: Long,
    fileLock: ReentrantLock
) : ViewModelProperty<ViewModelTriggerProperty.TriggerUnit>(unsafeCppPointer, fileLock) {

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

    fun trigger() = withLock { cppTrigger(cppPointer) }
}

/**
 * An image property of a [ViewModelInstance]. Values are [RiveRenderImage] type, and must be
 * constructed from encoded bytes with [RiveRenderImage.make].
 *
 * Unlike other property types, this property can only be set with [set]. It cannot be read or
 * observed. It's [value] and [valueFlow] are not applicable and will always return [Unit].
 *
 * Setting to null will remove the image and release the reference to the image.
 *
 * @see ViewModelNumberProperty
 */
class ViewModelImageProperty(
    unsafeCppPointer: Long,
    fileLock: ReentrantLock
) : ViewModelProperty<Unit>(unsafeCppPointer, fileLock) {
    private external fun cppSetValue(cppPointer: Long, value: Long)

    fun set(image: RiveRenderImage?) =
        withLock { cppSetValue(cppPointer, image?.cppPointer ?: NULL_POINTER) }

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
 *
 * @see ViewModelNumberProperty
 */
class ViewModelListProperty(
    unsafeCppPointer: Long,
    fileLock: ReentrantLock
) : ViewModelProperty<Unit>(unsafeCppPointer, fileLock) {
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

    /**
     * Updates this list property and cached list items to synchronize on [newFileLock].
     *
     * List items are themselves [ViewModelInstance] wrappers. If the list is rebound or transferred
     * to a different native graph, cached item wrappers must follow the list's file lock so later
     * item property writes are serialized with the same frame advancement.
     *
     * @param newFileLock The file lock for the native graph this list currently mutates.
     */
    override fun updateFileLock(newFileLock: ReentrantLock) {
        val cachedInstances =
            updateFileLockWithRetry(
                currentFileLock = { fileLock },
                newFileLock = newFileLock,
                setFileLock = { fileLock = it },
                snapshotOfDependents = {
                    cachedItems.values.map { it.instance }
                }
            ) ?: return

        cachedInstances.forEach { it.updateFileLock(newFileLock) }
    }

    override fun cppDelete(pointer: Long) = withLock {
        super.cppDelete(pointer)

        // Release all cached items when the list is deleted
        cachedItems.values.forEach { it.instance.release() }
        cachedItems.clear()
    }

    @Throws(IndexOutOfBoundsException::class)
    private fun boundsCheck(index: Int) {
        boundsCheck(index, size)
    }

    /**
     * Checks [index] against an already-read [size].
     *
     * Use this when the caller already holds the file lock and has read the native list size inside
     * that lock.
     *
     * @param index The index to validate.
     * @param size The current list size to validate against.
     * @throws IndexOutOfBoundsException if [index] is out of bounds.
     */
    @Throws(IndexOutOfBoundsException::class)
    private fun boundsCheck(index: Int, size: Int) {
        if (index < 0 || index >= size) {
            throw IndexOutOfBoundsException("Index out of bounds for ViewModelListProperty.")
        }
    }

    /** The number of items in the list. */
    val size: Int
        get() = withLock { cppSize(cppPointer) }

    /**
     * Get the [ViewModelInstance] at the specified [index].
     *
     * @throws IndexOutOfBoundsException if [index] is out of bounds.
     */
    @Throws(IndexOutOfBoundsException::class)
    fun elementAt(index: Int): ViewModelInstance = withLock {
        boundsCheck(index)

        val cppPointer = cppElementAt(cppPointer, index)
        // Check if the item is cached
        cachedItems[cppPointer]?.instance ?: run {
            /**
             * If not cached, create a new instance and cache it. The instance will have a reference
             * count of 1, which will be released when either the list is deleted or the item is
             * removed from the list.
             */
            val newItem = ViewModelInstance(cppPointer, fileLock)
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
        val destinationFileLock = fileLock
        item.updateFileLock(destinationFileLock)

        synchronized(destinationFileLock) {
            cachedItems.getOrPut(item.cppPointer) {
                item.acquire()
                CacheEntry(item, count = 0)
            }.count++

            cppAdd(cppPointer, item.cppPointer)
        }
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
        require(item.hasCppObject) { "Cannot add a disposed ViewModelProperty to ViewModelListProperty." }
        val destinationFileLock = fileLock
        item.updateFileLock(destinationFileLock)

        synchronized(destinationFileLock) {
            boundsCheck(index, cppSize(cppPointer))
            cachedItems.getOrPut(item.cppPointer) {
                item.acquire()
                CacheEntry(item, count = 0)
            }.count++

            cppAddAt(cppPointer, index, item.cppPointer)
        }
    }

    /**
     * Remove all instances of [ViewModelInstance] from the list.
     *
     * @throws IllegalArgumentException if [item] is disposed.
     */
    @Throws(IllegalArgumentException::class)
    fun remove(item: ViewModelInstance) = withLock {
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
    fun removeAt(index: Int) = withLock {
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
    fun swap(index1: Int, index2: Int) = withLock {
        boundsCheck(index1)
        boundsCheck(index2)

        if (index1 == index2) {
            // No-op, swapping the same index does nothing.
            return@withLock
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
 *
 * @param unsafeCppPointer Pointer to the C++ counterpart.
 * @param fileLock Lock shared by the [File] and native graph this property mutates. Updated through
 *    [ViewModelProperty.updateFileLock] when assigned to another native graph.
 */
class ViewModelArtboardProperty(
    unsafeCppPointer: Long,
    fileLock: ReentrantLock
) : ViewModelProperty<Unit>(unsafeCppPointer, fileLock) {

    private external fun cppSetArtboard(
        cppPointer: Long,
        fileCppPointer: Long,
        artboardCppPointer: Long
    )

    private external fun cppSetBindableArtboard(
        cppPointer: Long,
        bindableArtboardCppPointer: Long,
        boundInstancePointer: Long
    )

    /**
     * Set the [artboard] for this property.
     *
     * The bound artboard will retain none of the state of the passed in [artboard]. It effectively
     * creates a new instance of the artboard with its initial state.
     *
     * @throws RiveException if [artboard] has been disposed, if its file doesn't exist, or if its
     *    file has been disposed.
     * @deprecated This method is unsafe as the [artboard]'s lifetime is bound to that of the [File]
     *    that created it. Use a [BindableArtboard] to ensure proper lifetimes.
     */
    @Throws(RiveException::class)
    @Deprecated(
        "This method is unsafe as the Artboard's lifetime is bound to that of the File " +
                "that created it. Use a BindableArtboard to ensure proper lifetimes.",
        ReplaceWith("set(bindableArtboard)")
    )
    fun set(artboard: Artboard) = withLock {
        if (!artboard.hasCppObject) {
            throw RiveException("Cannot set a disposed Artboard to a ViewModelArtboardProperty.")
        } else if (artboard.file == null) {
            throw RiveException("Cannot set an Artboard with no File reference to a ViewModelArtboardProperty.")
        } else if (!artboard.file!!.hasCppObject) {
            throw RiveException("Cannot set an Artboard whose File has been disposed to a ViewModelArtboardProperty.")
        }
        cppSetArtboard(cppPointer, artboard.file!!.cppPointer, artboard.cppPointer)
    }

    /**
     * Set the bindable artboard for this property.
     *
     * To create a [BindableArtboard], use [File.createBindableArtboardByName] or
     * [File.createDefaultBindableArtboard].
     *
     * Pass `null` to clear the artboard from the property.
     *
     * ⚠️ If you are done with the [BindableArtboard] instance, be sure to call
     * [BindableArtboard.release] after assigning. The property will retain its own reference to the
     * bindable artboard.
     *
     * @throws RiveException if [bindableArtboard] has been disposed.
     */
    fun set(bindableArtboard: BindableArtboard?) {
        // Update the file lock of the bindable artboard's VMI to match this property's file lock.
        val destinationFileLock = fileLock
        val viewModelInstance = bindableArtboard?.viewModelInstance
        viewModelInstance?.updateFileLock(destinationFileLock)

        synchronized(destinationFileLock) {
            cppSetBindableArtboard(
                cppPointer,
                bindableArtboard?.cppPointer ?: NULL_POINTER,
                viewModelInstance?.cppPointer ?: NULL_POINTER
            )

            // Remove any existing BindableArtboard dependency
            dependencies.filterIsInstance<BindableArtboard>().forEach {
                it.release()
                dependencies.remove(it)
            }

            // If non-null, acquire a reference and add it to the property's dependencies
            bindableArtboard?.let {
                bindableArtboard.acquire()
                dependencies.add(it)
            }
        }
    }

    /** The set [BindableArtboard] may contain a view model instance that requires polling. */
    override fun pollChanges() = withLock {
        super.pollChanges()
        dependencies.filterIsInstance<BindableArtboard>().forEach {
            it.viewModelInstance?.pollChanges()
        }
    }

    // Return Unit, as artboards don't have a value to get.
    override fun nativeGetValue() = Unit

    // No-op, artboards are set with `set`, not `value`.
    override fun nativeSetValue(value: Unit) {}
}

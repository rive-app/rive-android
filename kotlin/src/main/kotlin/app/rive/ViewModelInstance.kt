package app.rive

import androidx.annotation.ColorInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import app.rive.core.CheckableAutoCloseable
import app.rive.core.CloseOnce
import app.rive.core.FileHandle
import app.rive.core.RivePropertyUpdate
import app.rive.core.RiveWorker
import app.rive.core.ViewModelInstanceHandle
import app.rive.runtime.kotlin.core.ViewModel.PropertyDataType
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

internal const val VM_INSTANCE_TAG = "Rive/VMI"

/**
 * Ensures every Rive resource referenced by this source remains open.
 *
 * @throws RiveResourceClosedException If a referenced resource has been closed.
 */
@Throws(RiveResourceClosedException::class)
internal fun ViewModelInstanceSource.checkOpen() {
    when (this) {
        is ViewModelInstanceSource.Blank -> vmSource.checkOpen()
        is ViewModelInstanceSource.Default -> vmSource.checkOpen()
        is ViewModelInstanceSource.Named -> vmSource.checkOpen()
        is ViewModelInstanceSource.Reference -> parentInstance.checkOpen()
        is ViewModelInstanceSource.ReferenceListItem -> parentInstance.checkOpen()
    }
}

/**
 * Ensures every Rive resource referenced by this view model source remains open.
 *
 * @throws RiveResourceClosedException If a referenced resource has been closed.
 */
@Throws(RiveResourceClosedException::class)
internal fun ViewModelSource.checkOpen() {
    if (this is ViewModelSource.DefaultForArtboard) {
        artboard.checkOpen()
    }
}

/**
 * Requires every Rive resource referenced by this source to be compatible with the given owner.
 *
 * @param worker The worker used to create the view model instance.
 * @param fileHandle The file used to create the view model instance.
 * @throws RiveIncompatibleResourceException If a referenced resource has different ownership.
 */
@Throws(RiveIncompatibleResourceException::class)
internal fun ViewModelInstanceSource.requireCompatibleWith(
    worker: RiveWorker,
    fileHandle: FileHandle,
) {
    when (this) {
        is ViewModelInstanceSource.Blank -> vmSource.requireCompatibleWith(worker, fileHandle)
        is ViewModelInstanceSource.Default -> vmSource.requireCompatibleWith(worker, fileHandle)
        is ViewModelInstanceSource.Named -> vmSource.requireCompatibleWith(worker, fileHandle)
        is ViewModelInstanceSource.Reference -> parentInstance.requireOwnedBy(worker)
        is ViewModelInstanceSource.ReferenceListItem -> parentInstance.requireOwnedBy(worker)
    }
}

/**
 * Requires every Rive resource referenced by this view model source to be compatible with the
 * given owner.
 *
 * @param worker The worker required to own any referenced artboard.
 * @param fileHandle The file handle required to own any referenced artboard.
 * @throws RiveIncompatibleResourceException If a referenced artboard has different ownership.
 */
@Throws(RiveIncompatibleResourceException::class)
internal fun ViewModelSource.requireCompatibleWith(
    worker: RiveWorker,
    fileHandle: FileHandle,
) {
    if (this is ViewModelSource.DefaultForArtboard) {
        artboard.requireFromFile(worker, fileHandle)
    }
}

/**
 * Identifies one native view model property subscription.
 *
 * @param propertyPath The path to the property from its view model instance.
 * @param propertyType The property's data type.
 */
private data class PropertySubscriptionKey(
    val propertyPath: String,
    val propertyType: PropertyDataType,
)

/**
 * Shares each native property subscription across every Kotlin collector that depends on it.
 *
 * Native unsubscribe removes all matching subscriptions rather than one collector's subscription,
 * so commands are sent only when a property's collector count crosses between zero and one.
 * Starting and stopping collection is uncommon relative to property updates, making intrinsic
 * synchronization appropriate here without adding locking to the update path.
 *
 * @param riveWorker The worker that owns the native subscriptions.
 * @param instanceHandle The view model instance whose properties are observed.
 */
private class PropertySubscriptions(
    private val riveWorker: RiveWorker,
    private val instanceHandle: ViewModelInstanceHandle,
) {
    private val collectorCounts = mutableMapOf<PropertySubscriptionKey, Int>()
    private var closed = false

    /**
     * Adds a collector, subscribing natively when it is the first collector for [key].
     *
     * @param key The property subscription required by the collector.
     * @return false if all subscriptions have already been closed, otherwise true.
     * @throws RiveResourceClosedException If the owning Rive worker has been disposed.
     */
    @Synchronized
    @Throws(RiveResourceClosedException::class)
    fun acquire(key: PropertySubscriptionKey): Boolean {
        if (closed) return false

        val collectorCount = collectorCounts[key] ?: 0
        if (collectorCount == 0) {
            riveWorker.subscribeToProperty(
                instanceHandle,
                key.propertyPath,
                key.propertyType,
            )
        }
        collectorCounts[key] = collectorCount + 1
        return true
    }

    /**
     * Removes a collector, unsubscribing natively when it was the last collector for [key].
     *
     * This is a no-op after [closeAll], which has already removed the native subscription.
     * A disposed worker is also treated as successful cleanup because disposal removes all of its
     * native subscriptions.
     *
     * @param key The property subscription no longer required by the collector.
     */
    @Synchronized
    fun release(key: PropertySubscriptionKey) {
        if (closed) return

        val collectorCount = collectorCounts[key] ?: return
        if (collectorCount == 1) {
            try {
                riveWorker.unsubscribeFromProperty(
                    instanceHandle,
                    key.propertyPath,
                    key.propertyType,
                )
            } catch (_: RiveResourceClosedException) {
                // Worker disposal already releases the native subscription.
                RiveLog.d(VM_INSTANCE_TAG) {
                    "Skipping property unsubscribe for $instanceHandle because its worker is " +
                        "disposed"
                }
            }
            collectorCounts.remove(key)
        } else {
            collectorCounts[key] = collectorCount - 1
        }
    }

    /**
     * Prevents new subscriptions and unsubscribes every property with active collectors.
     *
     * All unsubscribe attempts are made before the first failure is rethrown.
     *
     * @throws RiveResourceClosedException If the owning Rive worker has been disposed.
     */
    @Synchronized
    @Throws(RiveResourceClosedException::class)
    fun closeAll() {
        if (closed) return
        closed = true

        val activeSubscriptions = collectorCounts.keys.toList()
        collectorCounts.clear()
        var firstFailure: RuntimeException? = null
        activeSubscriptions.forEach { key ->
            try {
                riveWorker.unsubscribeFromProperty(
                    instanceHandle,
                    key.propertyPath,
                    key.propertyType,
                )
            } catch (exception: RuntimeException) {
                if (firstFailure == null) {
                    firstFailure = exception
                }
            }
        }
        firstFailure?.let { throw it }
    }
}

/**
 * A view model instance for data binding which has properties that can be set and observed.
 *
 * The instance must be bound to a state machine for its values to take effect. This is done by
 * passing it to [Rive].
 *
 * @param instanceHandle The handle to the view model instance on the command server.
 * @param riveWorker The Rive worker that owns the view model instance.
 */
class ViewModelInstance internal constructor(
    val instanceHandle: ViewModelInstanceHandle,
    private val riveWorker: RiveWorker,
    private val fileHandle: FileHandle,
) : CheckableAutoCloseable {
    private val closeFlow = MutableSharedFlow<Unit>(replay = 1)
    private val propertySubscriptions = PropertySubscriptions(riveWorker, instanceHandle)
    private val closer = CloseOnce("$instanceHandle") {
        closeFlow.tryEmit(Unit)
        try {
            propertySubscriptions.closeAll()
        } finally {
            RiveLog.d(VM_INSTANCE_TAG) { "Deleting $instanceHandle (${fileHandle})" }
            riveWorker.deleteViewModelInstance(instanceHandle)
        }
    }

    /**
     * Closes this view model instance and schedules deletion on its Rive worker.
     *
     * Active property flows complete normally. Their native property subscriptions are removed
     * before the instance is deleted.
     *
     * @throws RiveResourceClosedException If the owning Rive worker has been disposed.
     */
    @Throws(RiveResourceClosedException::class)
    override fun close() = closer.close()

    /** Whether this view model instance has been closed. */
    override val closed: Boolean
        get() = closer.closed

    /**
     * Ensures this view model instance has not been closed.
     *
     * @throws RiveResourceClosedException If this view model instance has already been closed.
     */
    @Throws(RiveResourceClosedException::class)
    internal fun checkOpen() = closer.checkOpen()

    companion object {
        /**
         * Creates a new [ViewModelInstance] and suspends until its Rive worker confirms creation.
         *
         * This is the replacement for [fromFile]. Its temporary name avoids a source-incompatible
         * overload; it will be renamed to `fromFile` in 12.0 when the unconfirmed API is removed.
         *
         * ⚠️ The lifetime of a successfully created view model instance is managed by the caller.
         * Make sure to call [close] when you are done with it to release its resources.
         *
         * @param file The [RiveFile] to create the view model instance from.
         * @param source The source of the view model instance. Constructed from [ViewModelSource]
         *    combined with [ViewModelInstanceSource].
         * @return The created view model instance.
         * @throws RiveFileException If the requested view model instance cannot be resolved.
         * @throws RiveResourceClosedException If [file] or a resource referenced by [source] has
         *    been closed, or if the owning Rive worker has been disposed.
         * @throws RiveIncompatibleResourceException If a resource referenced by [source] cannot
         *    be used with [file].
         * @throws CancellationException If the coroutine is cancelled before creation is
         *    confirmed.
         */
        @Throws(
            RiveFileException::class,
            RiveIncompatibleResourceException::class,
            RiveResourceClosedException::class,
            CancellationException::class
        )
        suspend fun create(
            file: RiveFile,
            source: ViewModelInstanceSource
        ): ViewModelInstance {
            RiveLog.d(VM_INSTANCE_TAG) {
                "Creating view model instance from source: $source (${file.fileHandle})"
            }
            return try {
                file.checkOpen()
                val handle = file.riveWorker.createViewModelInstanceConfirmed(
                    file.fileHandle,
                    source
                )
                RiveLog.d(VM_INSTANCE_TAG) {
                    "Created $handle from source: $source (${file.fileHandle})"
                }
                ViewModelInstance(handle, file.riveWorker, file.fileHandle)
            } catch (ce: CancellationException) {
                RiveLog.d(VM_INSTANCE_TAG) {
                    "View model instance creation was cancelled for source: $source " +
                        "(${file.fileHandle})"
                }
                throw ce
            } catch (e: Exception) {
                RiveLog.e(VM_INSTANCE_TAG, e) {
                    "Error creating view model instance from source: $source " +
                        "(${file.fileHandle})"
                }
                throw e
            }
        }

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
         * @throws RiveResourceClosedException If [file] or a resource referenced by [source] has
         *    been closed, or if the owning Rive worker has been disposed.
         * @throws RiveIncompatibleResourceException If a resource referenced by [source] cannot
         *    be used with [file].
         * @deprecated Use [create]. This unconfirmed implementation will be removed in 12.0, when
         *    [create] will be renamed to `fromFile` as a suspending API.
         */
        @Throws(RiveIncompatibleResourceException::class, RiveResourceClosedException::class)
        @Deprecated(
            "Use create. This unconfirmed implementation will be removed in 12.0, when create " +
                "will be renamed to fromFile as a suspending API."
        )
        @Suppress("DEPRECATION")
        fun fromFile(
            file: RiveFile,
            source: ViewModelInstanceSource
        ): ViewModelInstance {
            file.checkOpen()
            val handle = file.riveWorker.createViewModelInstance(file.fileHandle, source)
            RiveLog.d(VM_INSTANCE_TAG) { "Created $handle from source: $source (${file.fileHandle})" }
            return ViewModelInstance(handle, file.riveWorker, file.fileHandle)
        }
    }

    /**
     * Requires this view model instance to be owned by [worker].
     *
     * This compatibility check assumes callers have already verified that participating resources
     * are open.
     *
     * @param worker The worker required to own this view model instance.
     * @throws RiveIncompatibleResourceException If this instance is owned by another worker.
     */
    @Throws(RiveIncompatibleResourceException::class)
    internal fun requireOwnedBy(worker: RiveWorker) {
        if (riveWorker !== worker) {
            throw RiveIncompatibleResourceException(
                "ViewModelInstance $instanceHandle is not owned by the required RiveWorker"
            )
        }
    }

    /**
     * Gets the name of the view model that defines this instance.
     *
     * Unlike [getName], which returns the editor-assigned name of this specific instance, this
     * returns the name of its view model definition. Multiple instances can therefore return the
     * same view model name while having different instance names.
     *
     * @return The name of the view model that defines this instance.
     * @throws RiveResourceClosedException If this view model instance has been closed or its Rive
     *    worker has been disposed.
     * @throws RiveViewModelInstanceException If the view model instance operation fails.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     */
    @Throws(
        RiveViewModelInstanceException::class,
        RiveResourceClosedException::class,
        CancellationException::class
    )
    suspend fun getViewModelName(): String {
        closer.checkOpen()
        return riveWorker.getViewModelInstanceViewModelName(instanceHandle)
    }

    /**
     * Gets the editor-assigned name of this view model instance.
     *
     * This works for all creation sources, including names the caller may not know upfront,
     * such as the name of the instance marked "Default" in the Rive file when created with
     * [ViewModelInstanceSource.Default], or the name of a list item obtained with
     * [ViewModelInstanceSource.ReferenceListItem].
     *
     * @return The name of this view model instance, or an empty string for instances without a
     *    name, e.g. blank instances.
     * @throws RiveResourceClosedException If this view model instance has been closed or its Rive
     *    worker has been disposed.
     * @throws RiveViewModelInstanceException If the view model instance operation fails.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     */
    @Throws(
        RiveViewModelInstanceException::class,
        RiveResourceClosedException::class,
        CancellationException::class
    )
    suspend fun getName(): String {
        closer.checkOpen()
        return riveWorker.getViewModelInstanceName(instanceHandle)
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

    /**
     * Completes this flow normally as soon as the view model instance is closed.
     *
     * The update collector starts undispatched so it is listening before a native subscription or
     * initial getter can produce a callback. A rendezvous output preserves downstream backpressure
     * rather than introducing another implicit property-value buffer.
     *
     * @return A flow that relays this flow until the view model instance closes.
     */
    private fun <T> Flow<T>.completeWhenClosed(): Flow<T> = channelFlow {
        val updatesJob = launch(start = CoroutineStart.UNDISPATCHED) {
            this@completeWhenClosed.collect { send(it) }
        }
        val closeJob = launch(start = CoroutineStart.UNDISPATCHED) {
            closeFlow.first()
            updatesJob.cancel()
        }
        try {
            updatesJob.join()
        } finally {
            closeJob.cancel()
        }
    }.buffer(Channel.RENDEZVOUS)

    /**
     * Creates or retrieves a cached property flow after verifying that this instance is open.
     *
     * The instance is checked again when collection starts because callers can retain a flow
     * across the instance's lifetime.
     *
     * @param propertyPath The path to the property from this view model instance.
     * @param cache The cache for flows of this property type.
     * @param getter The worker operation that requests the property's current value.
     * @param updateFlow The worker flow that delivers updates for this property type.
     * @param propertyType The property's data type.
     * @return The cached or newly created property flow.
     * @throws RiveResourceClosedException If this view model instance has been closed or its Rive
     *    worker has been disposed.
     */
    @Throws(RiveResourceClosedException::class)
    private fun <T> getPropertyFlow(
        propertyPath: String,
        cache: MutableMap<String, Flow<T>>,
        getter: suspend (ViewModelInstanceHandle, String) -> T,
        updateFlow: SharedFlow<RivePropertyUpdate<T>>,
        propertyType: PropertyDataType
    ): Flow<T> {
        closer.checkOpen()
        return cache.getOrPut(propertyPath) {
            flow {
                closer.checkOpen()
                val subscriptionKey = PropertySubscriptionKey(propertyPath, propertyType)
                var subscriptionAcquired = false
                try {
                    emitAll(
                        updateFlow
                            // Ensure we’re subscribed, then kick off fetching latest value.
                            .onSubscription {
                                subscriptionAcquired =
                                    propertySubscriptions.acquire(subscriptionKey)
                                if (subscriptionAcquired) {
                                    // Fire the getter so its reply comes through as the first
                                    // emission (ignoring the immediately returned value).
                                    try {
                                        getter(instanceHandle, propertyPath)
                                    } catch (exception: RiveViewModelInstanceException) {
                                        // Closure completes active flows normally even if the
                                        // native deletion error reaches this getter first.
                                        if (!closer.closed) throw exception
                                    }
                                }
                            }
                            .filter {
                                it.handle == instanceHandle && it.propertyPath == propertyPath
                            }
                            .map { it.value }
                            .distinctUntilChanged() // Don't emit duplicates
                            .completeWhenClosed()
                    )
                } finally {
                    if (subscriptionAcquired) {
                        propertySubscriptions.release(subscriptionKey)
                    }
                }
            }
        }
    }

    /**
     * Creates or retrieves from cache a [number][Float] property, represented as a cold [Flow].
     *
     * The flow is subscribed to updates from the Rive worker while it is being collected.
     * It completes normally if this view model instance is closed during collection.
     *
     * This flow emits every distinct value (up to the backing buffer limit). If you process
     * the flow slowly, consider applying [conflate] if you only need the latest value to skip
     * intermediate values. Alternatively, if you need to process every value, consider using a
     * [buffer] operator with an appropriate buffer size to handle bursts.
     *
     * Collection of the flow may cause an exception:
     * - [RiveViewModelInstanceException]: If the view model instance operation fails, such as when
     *   the property does not exist or has a different type.
     * - [RiveResourceClosedException]: If this view model instance has been closed before the flow
     *   is retrieved or collected, or if the owning Rive worker has been disposed.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @return A cold [Flow] of [Float] values representing the property.
     * @throws RiveResourceClosedException If this view model instance has been closed or its Rive
     *    worker has been disposed.
     */
    @Throws(RiveResourceClosedException::class)
    fun getNumberFlow(propertyPath: String): Flow<Float> {
        closer.checkOpen()
        return getPropertyFlow(
            propertyPath,
            numberFlows,
            riveWorker::getNumberProperty,
            riveWorker.numberPropertyFlow,
            PropertyDataType.NUMBER
        )
    }

    /**
     * Creates or retrieves from cache a [string][String] property, represented as a cold [Flow].
     *
     * The collection of the flow may cause an exception. See [getNumberFlow] for details.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @return A cold [Flow] of [String] values representing the property.
     * @throws RiveResourceClosedException If this view model instance has been closed or its Rive
     *    worker has been disposed.
     * @see getNumberFlow
     */
    @Throws(RiveResourceClosedException::class)
    fun getStringFlow(propertyPath: String): Flow<String> {
        closer.checkOpen()
        return getPropertyFlow(
            propertyPath,
            stringFlows,
            riveWorker::getStringProperty,
            riveWorker.stringPropertyFlow,
            PropertyDataType.STRING
        )
    }

    /**
     * Creates or retrieves from cache a [boolean][Boolean] property, represented as a cold [Flow].
     *
     * The collection of the flow may cause an exception. See [getNumberFlow] for details.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @return A cold [Flow] of [Boolean] values representing the property.
     * @throws RiveResourceClosedException If this view model instance has been closed or its Rive
     *    worker has been disposed.
     * @see getNumberFlow
     */
    @Throws(RiveResourceClosedException::class)
    fun getBooleanFlow(propertyPath: String): Flow<Boolean> {
        closer.checkOpen()
        return getPropertyFlow(
            propertyPath,
            booleanFlows,
            riveWorker::getBooleanProperty,
            riveWorker.booleanPropertyFlow,
            PropertyDataType.BOOLEAN
        )
    }

    /**
     * Creates or retrieves from cache an enum property, represented as a cold [Flow]. Enums are
     * represented as strings, and this flow will emit the string value of the enum.
     *
     * The collection of the flow may cause an exception. See [getNumberFlow] for details.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @return A cold [Flow] of [String] values representing the enum property.
     * @throws RiveResourceClosedException If this view model instance has been closed or its Rive
     *    worker has been disposed.
     * @see getNumberFlow
     */
    @Throws(RiveResourceClosedException::class)
    fun getEnumFlow(propertyPath: String): Flow<String> {
        closer.checkOpen()
        return getPropertyFlow(
            propertyPath,
            enumFlows,
            riveWorker::getEnumProperty,
            riveWorker.enumPropertyFlow,
            PropertyDataType.ENUM
        )
    }

    /**
     * Creates or retrieves from cache a color property, represented as a cold [Flow]. Colors are
     * represented as AARRGGBB integers, and this flow will emit the integer value of the color.
     *
     * The collection of the flow may cause an exception. See [getNumberFlow] for details.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @return A cold [Flow] of [Int] values representing the color property.
     * @throws RiveResourceClosedException If this view model instance has been closed or its Rive
     *    worker has been disposed.
     * @see getNumberFlow
     */
    @Throws(RiveResourceClosedException::class)
    fun getColorFlow(propertyPath: String): Flow<Int> {
        closer.checkOpen()
        return getPropertyFlow(
            propertyPath,
            colorFlows,
            riveWorker::getColorProperty,
            riveWorker.colorPropertyFlow,
            PropertyDataType.COLOR
        )
    }

    /**
     * Creates or retrieves from cache a trigger property, represented as a cold [Flow]. Triggers
     * emit Unit as the value, which simply indicates that the trigger has been fired.
     *
     * The collection of the flow may cause an exception. See [getNumberFlow] for details.
     *
     * @param propertyPath The path to the trigger property from this view model instance. Slash
     *    delimited to refer to nested properties.
     * @return A cold [Flow] of [Unit] values representing trigger events.
     * @throws RiveResourceClosedException If this view model instance has been closed or its Rive
     *    worker has been disposed.
     * @see getNumberFlow
     */
    @Throws(RiveResourceClosedException::class)
    fun getTriggerFlow(propertyPath: String): Flow<Unit> {
        closer.checkOpen()
        return triggerFlows.getOrPut(propertyPath) {
            flow {
                closer.checkOpen()
                val subscriptionKey =
                    PropertySubscriptionKey(propertyPath, PropertyDataType.TRIGGER)
                var subscriptionAcquired = false
                try {
                    emitAll(
                        riveWorker.triggerPropertyFlow
                            .onSubscription {
                                subscriptionAcquired =
                                    propertySubscriptions.acquire(subscriptionKey)
                            }
                            .filter {
                                it.handle == instanceHandle && it.propertyPath == propertyPath
                            }
                            .map { /* Unit */ }
                            .buffer(32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
                            .completeWhenClosed()
                    )
                } finally {
                    if (subscriptionAcquired) {
                        propertySubscriptions.release(subscriptionKey)
                    }
                }
            }
        }
    }

    private fun <T> setProperty(
        propertyPath: String,
        value: T,
        setter: (ViewModelInstanceHandle, String, T) -> Unit
    ) {
        closer.checkOpen()
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
     * @throws RiveResourceClosedException If this view model instance has been closed or its Rive
     *    worker has been disposed.
     */
    @Throws(RiveResourceClosedException::class)
    fun setNumber(propertyPath: String, value: Float) =
        setProperty(propertyPath, value, riveWorker::setNumberProperty)

    /**
     * Sets a [string][String] property on this view model instance.
     *
     * ℹ️ Changes to bound Rive elements will not be reflected until the next state machine advance.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @param value The value to set the property to.
     * @throws RiveResourceClosedException If this view model instance has been closed or its Rive
     *    worker has been disposed.
     */
    @Throws(RiveResourceClosedException::class)
    fun setString(propertyPath: String, value: String) =
        setProperty(propertyPath, value, riveWorker::setStringProperty)

    /**
     * Sets a [boolean][Boolean] property on this view model instance.
     *
     * ℹ️ Changes to bound Rive elements will not be reflected until the next state machine advance.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @param value The value to set the property to.
     * @throws RiveResourceClosedException If this view model instance has been closed or its Rive
     *    worker has been disposed.
     */
    @Throws(RiveResourceClosedException::class)
    fun setBoolean(propertyPath: String, value: Boolean) =
        setProperty(propertyPath, value, riveWorker::setBooleanProperty)

    /**
     * Sets an enum property on this view model instance. Enums are represented as strings.
     *
     * ℹ️ Changes to bound Rive elements will not be reflected until the next state machine advance.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @param value The string value of the enum to set the property to.
     * @throws RiveResourceClosedException If this view model instance has been closed or its Rive
     *    worker has been disposed.
     */
    @Throws(RiveResourceClosedException::class)
    fun setEnum(propertyPath: String, value: String) =
        setProperty(propertyPath, value, riveWorker::setEnumProperty)

    /**
     * Sets a color property on this view model instance. Colors are represented as AARRGGBB
     * integers.
     *
     * ℹ️ Changes to bound Rive elements will not be reflected until the next state machine advance.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @param value The integer value of the color to set the property to.
     * @throws RiveResourceClosedException If this view model instance has been closed or its Rive
     *    worker has been disposed.
     */
    @Throws(RiveResourceClosedException::class)
    fun setColor(propertyPath: String, @ColorInt value: Int) =
        setProperty(propertyPath, value, riveWorker::setColorProperty)

    /**
     * Fires a trigger on this view model instance.
     *
     * ℹ️ Changes to bound Rive elements will not be reflected until the next state machine advance.
     *
     * @param propertyPath The path to the trigger property from this view model instance. Slash
     *    delimited to refer to nested properties.
     * @throws RiveResourceClosedException If this view model instance has been closed or its Rive
     *    worker has been disposed.
     */
    @Throws(RiveResourceClosedException::class)
    fun fireTrigger(propertyPath: String) {
        closer.checkOpen()
        riveWorker.fireTriggerProperty(instanceHandle, propertyPath)
        _dirtyFlow.tryEmit(Unit)
    }

    /**
     * Assigns the given image to the image property on this view model instance, or clears the
     * property if [image] is null.
     *
     * ℹ️ Changes to bound Rive elements will not be reflected until the next state machine advance.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @param image The image to assign to the property, or null to clear the property.
     * @throws RiveResourceClosedException If this view model instance or [image] has been closed,
     *    or if the owning Rive worker has been disposed.
     * @throws RiveIncompatibleResourceException If [image] is owned by another Rive worker.
     */
    @Throws(RiveIncompatibleResourceException::class, RiveResourceClosedException::class)
    fun setImage(propertyPath: String, image: ImageAsset?) {
        closer.checkOpen()
        image?.checkOpen()
        image?.requireOwnedBy(riveWorker)
        val message = image?.let { "Assigning $it" } ?: "Clearing image"
        RiveLog.d(VM_INSTANCE_TAG) { "$message for $propertyPath (${fileHandle})" }
        setProperty(propertyPath, image?.handle, riveWorker::setImageProperty)
    }

    /**
     * Assigns the given artboard to the bindable artboard property on this view model instance, or
     * clears the property if [artboard] is null.
     *
     * ℹ️ Changes to bound Rive elements will not be reflected until the next state machine advance.
     *
     * @param propertyPath The path to the property from this view model instance. Slash delimited
     *    to refer to nested properties.
     * @param artboard The artboard to assign to the property, or null to clear the property.
     * @throws RiveResourceClosedException If this view model instance or [artboard] has been
     *    closed, or if the owning Rive worker has been disposed.
     * @throws RiveIncompatibleResourceException If [artboard] is owned by another Rive worker.
     */
    @Throws(RiveIncompatibleResourceException::class, RiveResourceClosedException::class)
    fun setArtboard(propertyPath: String, artboard: Artboard?) {
        closer.checkOpen()
        artboard?.checkOpen()
        artboard?.requireOwnedBy(riveWorker)
        val message = artboard?.let { "Assigning $it" } ?: "Clearing artboard"
        RiveLog.d(VM_INSTANCE_TAG) { "$message for $propertyPath (${fileHandle})" }
        setProperty(propertyPath, artboard?.artboardHandle, riveWorker::setArtboardProperty)
    }

    /**
     * Assigns the given view model instance to the nested view model property on this view model
     * instance.
     *
     * ℹ️ Changes to bound Rive elements will not be reflected until the next state machine advance.
     * 
     * Once the nested view model instance is added to the view model property, you do not need to
     * keep your reference to it. The parent view model instance maintains its own native reference
     * to the nested instance.
     *
     * If you created the view model instance manually (for example via [ViewModelInstance.create]),
     * you may [close][ViewModelInstance.close] it to release your reference once you no longer need
     * that view model instance elsewhere.
     *
     * If you used [rememberViewModelInstanceResult], do not close a successful result manually. It
     * is closed automatically when the Composable leaves composition.
     *
     * @param propertyPath The path to the view model property from this view model instance. Slash
     *    delimited to refer to nested properties.
     * @param instance The view model instance to assign to the property.
     * @throws RiveResourceClosedException If this view model instance or [instance] has been
     *    closed, or if the owning Rive worker has been disposed.
     * @throws RiveIncompatibleResourceException If [instance] is owned by another Rive worker.
     */
    @Throws(RiveIncompatibleResourceException::class, RiveResourceClosedException::class)
    fun setViewModelInstance(propertyPath: String, instance: ViewModelInstance) {
        closer.checkOpen()
        instance.checkOpen()
        instance.requireOwnedBy(riveWorker)
        RiveLog.d(VM_INSTANCE_TAG) { "Assigning $instance to $propertyPath (${fileHandle})" }
        setProperty(propertyPath, instance.instanceHandle, riveWorker::setViewModelInstanceProperty)
    }

    /**
     * Gets the number of items in a list property.
     *
     * @param propertyPath The path to the list property from this view model instance. Slash
     *    delimited to refer to nested properties.
     * @return The number of items in the list.
     * @throws RiveResourceClosedException If this view model instance has been closed or its Rive
     *    worker has been disposed.
     * @throws RiveViewModelInstanceException If the view model instance operation fails.
     * @throws CancellationException If the coroutine is cancelled before the operation completes.
     */
    @Throws(
        RiveViewModelInstanceException::class,
        RiveResourceClosedException::class,
        CancellationException::class
    )
    suspend fun getListSize(propertyPath: String): Int {
        closer.checkOpen()
        return riveWorker.getListSize(instanceHandle, propertyPath)
    }

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
     * @throws RiveResourceClosedException If this view model instance or [item] has been closed,
     *    or if the owning Rive worker has been disposed.
     * @throws RiveIncompatibleResourceException If [item] is owned by another Rive worker.
     */
    @Throws(RiveIncompatibleResourceException::class, RiveResourceClosedException::class)
    fun insertToListAtIndex(propertyPath: String, index: Int, item: ViewModelInstance) {
        closer.checkOpen()
        item.checkOpen()
        item.requireOwnedBy(riveWorker)
        riveWorker.insertToListAtIndex(instanceHandle, propertyPath, index, item.instanceHandle)
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
     * @throws RiveResourceClosedException If this view model instance or [item] has been closed,
     *    or if the owning Rive worker has been disposed.
     * @throws RiveIncompatibleResourceException If [item] is owned by another Rive worker.
     */
    @Throws(RiveIncompatibleResourceException::class, RiveResourceClosedException::class)
    fun appendToList(propertyPath: String, item: ViewModelInstance) {
        closer.checkOpen()
        item.checkOpen()
        item.requireOwnedBy(riveWorker)
        riveWorker.appendToList(instanceHandle, propertyPath, item.instanceHandle)
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
     * @throws RiveResourceClosedException If this view model instance has been closed or its Rive
     *    worker has been disposed.
     */
    @Throws(RiveResourceClosedException::class)
    fun removeFromListAtIndex(propertyPath: String, index: Int) {
        closer.checkOpen()
        riveWorker.removeFromListAtIndex(instanceHandle, propertyPath, index)
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
     * @throws RiveResourceClosedException If this view model instance or [item] has been closed,
     *    or if the owning Rive worker has been disposed.
     * @throws RiveIncompatibleResourceException If [item] is owned by another Rive worker.
     */
    @Throws(RiveIncompatibleResourceException::class, RiveResourceClosedException::class)
    fun removeFromList(propertyPath: String, item: ViewModelInstance) {
        closer.checkOpen()
        item.checkOpen()
        item.requireOwnedBy(riveWorker)
        riveWorker.removeFromList(instanceHandle, propertyPath, item.instanceHandle)
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
     * @throws RiveResourceClosedException If this view model instance has been closed or its Rive
     *    worker has been disposed.
     */
    @Throws(RiveResourceClosedException::class)
    fun swapListItems(propertyPath: String, indexA: Int, indexB: Int) {
        closer.checkOpen()
        riveWorker.swapListItems(instanceHandle, propertyPath, indexA, indexB)
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
 * @throws RiveResourceClosedException If [file] or a resource referenced by [source] has been
 *    closed, or if the owning Rive worker has been disposed.
 * @throws RiveIncompatibleResourceException If a resource referenced by [source] cannot be used
 *    with [file].
 * @deprecated Use [rememberViewModelInstanceResult]. This implementation will be removed in 12.0,
 *    when [rememberViewModelInstanceResult] will be renamed to `rememberViewModelInstance` and
 *    return a [Result].
 */
@Throws(RiveIncompatibleResourceException::class, RiveResourceClosedException::class)
@Deprecated(
    "Use rememberViewModelInstanceResult. This implementation will be removed in 12.0, when " +
        "rememberViewModelInstanceResult will be renamed to rememberViewModelInstance and " +
        "return a Result."
)
@Suppress("DEPRECATION")
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

/**
 * Creates a [ViewModelInstance] from [file] and exposes its creation state.
 *
 * The lifetime of a successfully created instance is managed by this composable. It will delete
 * the instance when it falls out of scope.
 *
 * This is the replacement for [rememberViewModelInstance]; its temporary name will become
 * `rememberViewModelInstance` in 12.0 when the unconfirmed API is removed.
 *
 * If [source] is null, the default artboard is created first and used to resolve its default view
 * model and instance. If you already have an artboard, prefer
 * [ViewModelSource.DefaultForArtboard] to avoid instantiating another artboard.
 *
 * @param file The [RiveFile] to create the view model instance from.
 * @param source The source of the view model instance, or null to use the default instance for the
 *    file's default artboard.
 * @return The current creation result: loading, error, or success with the created
 *    [ViewModelInstance]. Changing [file] or [source] synchronously returns loading while the
 *    replacement is created.
 */
@Composable
fun rememberViewModelInstanceResult(
    file: RiveFile,
    source: ViewModelInstanceSource? = null,
): Result<ViewModelInstance> {
    if (source != null) {
        return rememberViewModelInstanceResultFromSource(file, source)
    }

    return when (val artboardResult = rememberArtboardResult(file)) {
        is Result.Loading -> Result.Loading
        is Result.Error -> Result.Error(artboardResult.throwable)
        is Result.Success -> {
            val defaultSource = remember(artboardResult.value) {
                ViewModelSource.DefaultForArtboard(artboardResult.value).defaultInstance()
            }
            rememberViewModelInstanceResultFromSource(file, defaultSource)
        }
    }
}

/**
 * Creates and owns a confirmed view model instance for an already resolved [source].
 *
 * @param file The [RiveFile] to create the view model instance from.
 * @param source The resolved source of the view model instance.
 * @return The current creation result.
 */
@Composable
private fun rememberViewModelInstanceResultFromSource(
    file: RiveFile,
    source: ViewModelInstanceSource,
): Result<ViewModelInstance> = key(file, source) {
    produceState<Result<ViewModelInstance>>(Result.Loading) {
        val instance = try {
            ViewModelInstance.create(file, source)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            value = Result.Error(e)
            return@produceState
        }

        value = Result.Success(instance)
        awaitDispose { instance.close() }
    }.value
}

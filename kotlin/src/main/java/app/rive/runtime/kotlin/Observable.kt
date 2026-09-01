package app.rive.runtime.kotlin

/**
 * Registers and unregisters legacy controller listeners.
 *
 * @param ListenerType The listener type accepted by this observable.
 * @deprecated Controller listeners are deprecated. Use data binding instead.
 */
@Deprecated("Controller listeners are deprecated. Use data binding instead.")
interface Observable<ListenerType> {
    /**
     * Registers [listener].
     *
     * @param listener The listener to register.
     * @deprecated Controller listeners are deprecated. Use data binding instead.
     */
    @Deprecated("Controller listeners are deprecated. Use data binding instead.")
    fun registerListener(listener: ListenerType)

    /**
     * Unregisters [listener].
     *
     * @param listener The listener to unregister.
     * @deprecated Controller listeners are deprecated. Use data binding instead.
     */
    @Deprecated("Controller listeners are deprecated. Use data binding instead.")
    fun unregisterListener(listener: ListenerType)
}

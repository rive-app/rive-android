package app.rive.runtime.kotlin

interface Observable<ListenerType> {
    fun registerListener(listener: ListenerType)
    fun unregisterListener(listener: ListenerType)
}

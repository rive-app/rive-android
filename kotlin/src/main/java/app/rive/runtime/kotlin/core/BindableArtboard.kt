package app.rive.runtime.kotlin.core

/**
 * A BindableArtboard is an artboard reference that can be assigned to a
 * [ViewModelArtboardProperty]. It should only be used for that purpose and cannot be used as a
 * regular [Artboard].
 *
 * Instances of this class are created via [File.createBindableArtboardByName] or
 * [File.createDefaultBindableArtboard].
 *
 * ⚠️Important: This bindable artboard can outlive the [File] instance that created it, but in order
 * to do so it has an extra reference count. You need to call [release] when you are done with it,
 * otherwise it will leak memory.
 */
class BindableArtboard(unsafeCppPointer: Long) : NativeObject(unsafeCppPointer) {
    init {
        // Keep an extra reference count so that this object can outlive the File that created it.
        acquire()
    }

    external override fun cppDelete(pointer: Long)
    external fun cppName(pointer: Long): String

    /** The name of the artboard. */
    val name: String
        get() = cppName(cppPointer)
}

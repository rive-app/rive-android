package app.rive.runtime.kotlin.core

import androidx.annotation.OpenForTesting
import androidx.annotation.VisibleForTesting
import app.rive.runtime.kotlin.core.errors.ArtboardException
import app.rive.runtime.kotlin.core.errors.RiveException
import app.rive.runtime.kotlin.core.errors.ViewModelException
import java.util.concurrent.locks.ReentrantLock

/**
 * [File]s are created in the Rive editor.
 *
 * This object has a counterpart in C++, which implements much of its functionality. The base
 * class's [cppPointer] keeps track of this relationship.
 *
 * You can export .riv from the editor and use this class to load them. [File]s may contain multiple
 * artboards.
 *
 * If the given file cannot be loaded this will throw a [RiveException]. The Rive [File] format is
 * evolving, and while we attempt to keep backwards (and forwards) compatibility where possible,
 * there are times when this is not possible.
 *
 * The Rive editor will always export your file in the latest runtime format.
 *
 * ⚠️ Important: If you create a [File] yourself using this constructor, you are responsible for
 * calling [release] when you are done with it, otherwise it will leak memory.
 *
 * @param bytes The bytes of the .riv file.
 * @param rendererType The [RendererType] to use when rendering this file. This defaults to
 *    [Rive.defaultRendererType], which is [RendererType.Rive].
 * @param fileAssetLoader An optional [FileAssetLoader] to use when loading external assets (images,
 *    fonts, audio) referenced by this file. If it is not provided you will not be able to load
 *    external assets.
 */
@OpenForTesting
class File(
    bytes: ByteArray,
    val rendererType: RendererType = Rive.defaultRendererType,
    fileAssetLoader: FileAssetLoader? = null,
) : NativeObject(NULL_POINTER) {
    init {
        // Set the correct renderer type and make sure we make this a dependency.
        // In fact, when importing a file, the FileAssetLoader rcp is incremented and Kotlin
        // should do the same.
        fileAssetLoader?.let {
            it.setRendererType(rendererType)
            it.acquire()
            dependencies.add(it)
        }

        cppPointer = import(
            bytes,
            bytes.size,
            rendererType.value,
            fileAssetLoader?.cppPointer ?: NULL_POINTER
        )
        refs.incrementAndGet()
    }

    val lock = ReentrantLock()

    private external fun import(
        bytes: ByteArray,
        length: Int,
        rendererType: Int,
        fileAssetLoaderPointer: Long,
    ): Long

    private external fun cppArtboardByName(cppPointer: Long, name: String): Long

    @Suppress("ProtectedInFinal")
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    protected external fun cppArtboardByIndex(cppPointer: Long, index: Int): Long
    private external fun cppArtboardNameByIndex(cppPointer: Long, index: Int): String
    private external fun cppArtboardCount(cppPointer: Long): Int
    private external fun cppCreateBindableArtboardByName(cppPointer: Long, name: String): Long
    private external fun cppCreateDefaultBindableArtboard(cppPointer: Long): Long
    private external fun cppEnums(cppPointer: Long): List<Enum>
    private external fun cppViewModelCount(cppPointer: Long): Int
    private external fun cppViewModelByIndex(cppPointer: Long, viewModelIdx: Int): Long

    @Suppress("ProtectedInFinal")
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    protected external fun cppViewModelByName(cppPointer: Long, viewModelName: String): Long
    private external fun cppDefaultViewModelForArtboard(
        cppPointer: Long,
        artboardPointer: Long,
    ): Long

    external override fun cppDelete(pointer: Long)

    /** Get the first (i.e. the default) artboard in the file. */
    val firstArtboard: Artboard
        @Throws(RiveException::class)
        get() = artboard(0)

    /**
     * Get the artboard called [name] in the file.
     *
     * If multiple [Artboard]s have the same [name] it will return the first match.
     */
    @Throws(RiveException::class)
    fun artboard(name: String): Artboard {
        // Creates a new artboard instance.
        val artboardPointer = cppArtboardByName(cppPointer, name)
        if (artboardPointer == NULL_POINTER) {
            throw ArtboardException(
                "Artboard \"$name\" not found. " +
                        "Available Artboards: ${artboardNames.map { "\"$it\"" }}"
            )
        }

        val ab = Artboard(artboardPointer, lock, this)
        dependencies.add(ab)
        return ab
    }

    /**
     * Get the artboard at a given [index] in the [File].
     *
     * This starts at 0.
     */
    @Throws(RiveException::class)
    fun artboard(index: Int): Artboard {
        // Creates a new Artboard instance.
        val artboardPointer = cppArtboardByIndex(cppPointer, index)
        if (artboardPointer == NULL_POINTER) {
            throw ArtboardException("No Artboard found at index $index.")
        }
        val ab = Artboard(artboardPointer, lock, this)
        dependencies.add(ab)
        return ab
    }

    /**
     * Create a [BindableArtboard] by name. This is can then be bound to a
     * [ViewModelArtboardProperty].
     *
     * ⚠️ Important: The bindable artboard can outlive this [File] instance, but in order to do so
     * it has an extra reference count. You need to call [BindableArtboard.release] when you are
     * done with it, otherwise it will leak memory.
     *
     * @param name The name of the artboard in the Rive file to create.
     * @return A new [BindableArtboard] instance.
     * @throws ArtboardException If no artboard with the given name exists.
     */
    fun createBindableArtboardByName(name: String): BindableArtboard {
        val artboardPointer = cppCreateBindableArtboardByName(cppPointer, name)
        if (artboardPointer == NULL_POINTER) {
            throw ArtboardException("No BindableArtboard found with name $name.")
        }
        return BindableArtboard(artboardPointer).also { dependencies.add(it) }
    }

    /**
     * Create the default [BindableArtboard], as marked in the Rive file. This is can then be bound
     * to a [ViewModelArtboardProperty].
     *
     * ⚠️ Important: The bindable artboard can outlive this [File] instance, but in order to do so
     * it has an extra reference count. You need to call [BindableArtboard.release] when you are
     * done with it, otherwise it will leak memory.
     *
     * @return A new [BindableArtboard] instance.
     * @throws ArtboardException If no default artboard exists.
     */
    fun createDefaultBindableArtboard(): BindableArtboard {
        val artboardPointer = cppCreateDefaultBindableArtboard(cppPointer)
        if (artboardPointer == NULL_POINTER) {
            throw ArtboardException("No default BindableArtboard.")
        }
        return BindableArtboard(artboardPointer).also { dependencies.add(it) }
    }

    /** Get the number of artboards in the file. Useful for index-based iteration. */
    val artboardCount: Int
        get() = cppArtboardCount(cppPointer)

    /** Get the names of the artboards in the file. */
    val artboardNames: List<String>
        get() = (0 until artboardCount).map {
            val name = cppArtboardNameByIndex(cppPointer, it)
            name
        }

    /** The available [enums][Enum] in the file. */
    val enums: List<Enum>
        get() = cppEnums(cppPointer)

    /** The number of [ViewModel]s in the file. Useful for index-based iteration. */
    val viewModelCount: Int
        get() = cppViewModelCount(cppPointer)

    /**
     * Get the [ViewModel] definition from the file.
     *
     * @param viewModelIdx The Rive file 0-based index of the ViewModel.
     * @return The [ViewModel] definition.
     * @throws ViewModelException If the ViewModel is not found.
     */
    fun getViewModelByIndex(viewModelIdx: Int): ViewModel {
        val vmPointer = cppViewModelByIndex(cppPointer, viewModelIdx)
        if (vmPointer == NULL_POINTER) {
            throw ViewModelException("No ViewModel found at index $viewModelIdx.")
        }
        return ViewModel(vmPointer).also { dependencies.add(it) }
    }

    /**
     * Get the [ViewModel] definition from the file.
     *
     * @param viewModelName The Rive file name of the ViewModel.
     * @return The [ViewModel] definition.
     * @throws ViewModelException If the ViewModel is not found.
     */
    fun getViewModelByName(viewModelName: String): ViewModel {
        val vmPointer = cppViewModelByName(cppPointer, viewModelName)
        if (vmPointer == NULL_POINTER) {
            throw ViewModelException("No ViewModel found with name $viewModelName.")
        }
        return ViewModel(vmPointer).also { dependencies.add(it) }
    }

    /**
     * Get the default [ViewModel] for an [Artboard]. Usually this will be the ViewModel intended
     * for use with this artboard.
     *
     * @param artboard The artboard to get the default ViewModel for.
     * @return The default ViewModel for the artboard.
     * @throws ViewModelException If the default ViewModel is not found.
     */
    fun defaultViewModelForArtboard(artboard: Artboard): ViewModel {
        val vmPointer = cppDefaultViewModelForArtboard(cppPointer, artboard.cppPointer)
        if (vmPointer == NULL_POINTER) {
            throw ViewModelException("No default ViewModel found for artboard ${artboard.name}.")
        }
        return ViewModel(vmPointer).also { dependencies.add(it) }
    }

    override fun release(): Int {
        // `super.release()` is already @Synchronized, but wrap this in its own lock.
        synchronized(lock) { return super.release() }
    }

    /** The name and values of an enum, whether system or user defined. */
    data class Enum(
        val name: String,
        val values: List<String>,
    )
}

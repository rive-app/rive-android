package app.rive.runtime.kotlin.core

import androidx.annotation.OpenForTesting
import androidx.annotation.VisibleForTesting
import app.rive.runtime.kotlin.core.errors.ViewModelException

/**
 * A description of a ViewModel in the Rive file. It can be used to retrieve property definitions
 * at runtime. However, properties cannot be modified - that requires an instance. Use one of
 * the createInstance methods to create an instance with mutable properties from this ViewModel.
 *
 * @param unsafeCppPointer Pointer to the C++ counterpart.
 */
@OpenForTesting
class ViewModel internal constructor(unsafeCppPointer: Long) :
    NativeObject(unsafeCppPointer) {
    private external fun cppName(cppPointer: Long): String
    private external fun cppInstanceCount(cppPointer: Long): Int
    private external fun cppPropertyCount(cppPointer: Long): Int
    private external fun cppGetProperties(cppPointer: Long): List<Property>

    @Suppress("ProtectedInFinal")
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    protected external fun cppCreateBlankInstance(cppPointer: Long): Long
    private external fun cppCreateDefaultInstance(cppPointer: Long): Long
    private external fun cppCreateInstanceFromIndex(cppPointer: Long, index: Int): Long
    private external fun cppCreateInstanceFromName(cppPointer: Long, name: String): Long

    /** Get the [name] of the ViewModel. */
    val name: String
        get() = cppName(cppPointer)

    /** The number of instances of this ViewModel. Useful for index-based iteration. */
    val instanceCount: Int
        get() = cppInstanceCount(cppPointer)

    /** The number of properties in this ViewModel. */
    val propertyCount: Int
        get() = cppPropertyCount(cppPointer)

    /** The available [properties][Property] of the ViewModel. */
    val properties: List<Property>
        get() = cppGetProperties(cppPointer)

    /**
     * Create a new blank ViewModelInstance. Use Artboard::setViewModel to apply it. The instance
     * name will be an empty string.
     *
     * Property values will be defaulted:
     * - Numbers will be 0
     * - Strings will be empty
     * - Booleans will be false
     * - Colors will be ARGB(0xFF, 0, 0, 0)
     * - Enums will be their first value
     *
     * Useful when you want to supply all property values yourself.
     *
     * @return A new instance of the ViewModel with initial values defaulted.
     * @throws ViewModelException If a blank instance cannot be created.
     */
    fun createBlankInstance(): ViewModelInstance {
        val instancePointer = cppCreateBlankInstance(cppPointer)
        if (instancePointer == NULL_POINTER) {
            throw ViewModelException("Could not create a blank ViewModel instance")
        }
        return ViewModelInstance(instancePointer).also { dependencies.add(it) }
    }

    /**
     * Create a new ViewModelInstance. Use Artboard::setViewModel to apply it.
     *
     * Property values will be those of the instance marked as "Default" in the Rive editor.
     *
     * @return A new instance of the ViewModel with initial values supplied by the default instance.
     * @throws ViewModelException If the default instance cannot be created.
     */
    fun createDefaultInstance(): ViewModelInstance {
        val instancePointer = cppCreateDefaultInstance(cppPointer)
        if (instancePointer == NULL_POINTER) {
            throw ViewModelException("Could not create default ViewModel instance")
        }
        return ViewModelInstance(instancePointer).also { dependencies.add(it) }
    }

    /**
     * Create a new ViewModelInstance. Use Artboard::setViewModel to apply it.
     *
     * Useful when the Rive file has a set of default property values that you want to initialize
     * with.
     *
     * @param index Rive file 0-based index for this instance.
     * @return A new instance of the ViewModel with initial values supplied by the indexed instance.
     * @throws ViewModelException If the instance is not found.
     */
    fun createInstanceFromIndex(index: Int): ViewModelInstance {
        val instancePointer = cppCreateInstanceFromIndex(cppPointer, index)
        if (instancePointer == NULL_POINTER) {
            throw ViewModelException("ViewModel instance not found: $index")
        }
        return ViewModelInstance(instancePointer).also { dependencies.add(it) }
    }

    /**
     * Create a new ViewModelInstance. Use Artboard::setViewModel to apply it.
     *
     * Useful when the Rive file has a set of default property values that you want to initialize
     * with.
     *
     * @param name Rive file name for this instance.
     * @return A new instance of the ViewModel with initial values supplied by the named instance.
     * @throws ViewModelException If the instance is not found.
     */
    fun createInstanceFromName(name: String): ViewModelInstance {
        val instancePointer = cppCreateInstanceFromName(cppPointer, name)
        if (instancePointer == NULL_POINTER) {
            throw ViewModelException("ViewModel instance not found: $name")
        }
        return ViewModelInstance(instancePointer).also { dependencies.add(it) }
    }

    /**
     * A description of a property in the ViewModel.
     *
     * These can't be used to get or set the value of the property. For that, you will need a
     * ViewModelInstance and use its methods.
     */
    data class Property(
        val type: PropertyDataType,
        val name: String,
    )

    // Enum values mirror those in rive::DataType
    enum class PropertyDataType(val value: Int) {
        NONE(0),
        STRING(1),
        NUMBER(2),
        BOOLEAN(3),
        COLOR(4),
        LIST(5),
        ENUM(6),
        TRIGGER(7),
        VIEW_MODEL(8),
        INTEGER(9),
        SYMBOL_LIST_INDEX(10),
        ASSET_IMAGE(11),
        ARTBOARD(12);

        companion object {
            private val map = entries.associateBy(PropertyDataType::value)

            @JvmStatic // For JNI construction
            fun fromInt(type: Int) = map[type]
        }
    }
}

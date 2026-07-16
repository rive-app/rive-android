package app.rive.core

import kotlin.jvm.JvmStatic

/**
 * A description of a property in a ViewModel.
 *
 * These can't be used to get or set the value of the property. For that, you will need a
 * ViewModelInstance and use its methods.
 */
data class ViewModelProperty(
    val type: ViewModelPropertyDataType,
    val name: String,
)

// Enum values mirror those in rive::DataType
enum class ViewModelPropertyDataType(val value: Int) {
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
        private val map = entries.associateBy(ViewModelPropertyDataType::value)

        @JvmStatic // For JNI construction
        fun fromInt(type: Int) = map[type]
    }
}

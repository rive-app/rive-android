package app.rive.semantics

import app.rive.ExperimentalRiveSemantics

/**
 * Actions that can be fired on a Rive semantic node.
 *
 * Actions are fire-and-forget. Their resulting visual or semantic changes become observable after
 * the state machine is advanced and its next semantic diff is drained.
 */
@ExperimentalRiveSemantics
enum class SemanticActionType(internal val value: Int) {
    /** Activates the node, equivalent to a platform click or press. */
    Tap(0),

    /** Advances a slider by one step authored in the Rive file. */
    Increase(1),

    /** Reduces a slider by one step authored in the Rive file. */
    Decrease(2),
}

/**
 * Semantic roles produced by the Rive runtime.
 *
 * @property value Stable core integer value stored in [SemanticNodeData.role].
 */
@ExperimentalRiveSemantics
enum class SemanticRole(val value: Int) {
    /** Generic or untyped semantic container. */
    None(0),

    /** Push button or other primary action trigger. */
    Button(1),

    /** Navigational hyperlink. */
    Link(2),

    /** Binary or indeterminate check control. */
    Checkbox(3),

    /** On/off switch control. */
    SwitchControl(4),

    /**
     * Continuous or stepped range input.
     *
     * Core currently supplies a display value and discrete [SemanticActionType.Increase] and
     * [SemanticActionType.Decrease] actions, but not numeric range metadata or set-progress input.
     */
    Slider(5),

    /**
     * Text input carrying text-field-specific state.
     *
     * Android currently exposes its content and state as non-editable metadata until core provides
     * text mutation and IME commands.
     */
    TextField(6),

    /** Static text, optionally marked as a heading. */
    Text(7),

    /** Decorative or informational image whose label supplies alternative text. */
    Image(8),

    /** Generic semantic grouping that preserves discrete children. */
    Group(9),

    /** Ordered or unordered list container. */
    List(10),

    /** Item within a list container. */
    ListItem(11),

    /** Selectable tab within a tab list. */
    Tab(12),

    /** Container for a group of tabs. */
    TabList(13),

    /** Dialog container, optionally carrying the [SemanticState.Modal] state. */
    Dialog(14),

    /** Alert dialog requiring user attention. */
    AlertDialog(15),

    /** Container for mutually exclusive radio buttons. */
    RadioGroup(16),

    /** Mutually exclusive choice within a radio group. */
    RadioButton(17);

    companion object {
        /**
         * Converts a raw core role value to a known [SemanticRole].
         *
         * The original integer remains available from [SemanticNodeData.role] for adapters that
         * need to preserve a role added by a newer runtime.
         *
         * @param value Core role integer to interpret.
         * @return Matching role, or [None] when this SDK does not recognize [value].
         */
        fun fromValue(value: Int): SemanticRole =
            entries.firstOrNull { it.value == value } ?: None
    }
}

/**
 * Capability bits stored in [SemanticNodeData.traitFlags].
 *
 * Traits determine whether their corresponding state is applicable. A missing trait means the
 * state should be omitted by an accessibility adapter rather than interpreted as `false`.
 * Unknown future bits remain in the raw flags and are ignored by [has] unless explicitly queried.
 */
@ExperimentalRiveSemantics
object SemanticTrait {
    /** Gates [SemanticState.Expanded]. */
    const val Expandable = 1 shl 0

    /** Gates [SemanticState.Selected]. */
    const val Selectable = 1 shl 1

    /** Gates [SemanticState.checkState]. */
    const val Checkable = 1 shl 2

    /** Gates [SemanticState.Toggled]. */
    const val Toggleable = 1 shl 3

    /** Gates [SemanticState.Required]. */
    const val Requirable = 1 shl 4

    /** Gates [SemanticState.Disabled]. */
    const val Enablable = 1 shl 5

    /** Gates [SemanticState.Focused] and semantic focus requests. */
    const val Focusable = 1 shl 6

    /**
     * Returns whether [trait] is present in [flags].
     *
     * @param flags Bitmask from [SemanticNodeData.traitFlags].
     * @param trait Trait bit to test.
     * @return `true` when [trait] is set.
     */
    fun has(flags: Int, trait: Int): Boolean = (flags and trait) != 0
}

/**
 * Tri-state value encoded in the check-state field of [SemanticNodeData.stateFlags].
 *
 * The value is meaningful only when [SemanticTrait.Checkable] is present.
 */
@ExperimentalRiveSemantics
enum class SemanticCheckState {
    /** The node is not checked. */
    Unchecked,

    /** The node is checked. */
    Checked,

    /** The node is in an indeterminate or mixed state. */
    Mixed,
}

/**
 * State values stored in [SemanticNodeData.stateFlags].
 *
 * Bits zero through seven are meaningful only when their corresponding [SemanticTrait] is
 * present. Check state is a two-bit field occupying bits two and three rather than two independent
 * flags; read it with [checkState]. The remaining bits apply directly. Unknown future bits remain
 * in the raw flags and are ignored by [has] unless explicitly queried.
 */
@ExperimentalRiveSemantics
object SemanticState {
    private const val CHECK_STATE_OFFSET = 2
    private const val CHECK_STATE_MASK = 0x3 shl CHECK_STATE_OFFSET

    /** Node is expanded; requires [SemanticTrait.Expandable]. */
    const val Expanded = 1 shl 0

    /** Node is selected; requires [SemanticTrait.Selectable]. */
    const val Selected = 1 shl 1

    /** Switch is on; requires [SemanticTrait.Toggleable]. */
    const val Toggled = 1 shl 4

    /** Form field is required; requires [SemanticTrait.Requirable]. */
    const val Required = 1 shl 5

    /** Node is disabled; requires [SemanticTrait.Enablable]. */
    const val Disabled = 1 shl 6

    /** Node holds authored Rive focus; requires [SemanticTrait.Focusable]. */
    const val Focused = 1 shl 7

    /** Node and its subtree are absent from the accessibility hierarchy. */
    const val Hidden = 1 shl 8

    /** Changes to the node's accessible label should be announced politely. */
    const val LiveRegion = 1 shl 9

    /** Text field content is visible but not editable. */
    const val ReadOnly = 1 shl 10

    /** Dialog or alert-dialog content is modal within its Rive accessibility host. */
    const val Modal = 1 shl 11

    /** Text-field content is sensitive and must not be exposed as plain text. */
    const val Obscured = 1 shl 12

    /** Text field accepts or displays multiple lines. */
    const val Multiline = 1 shl 13

    /**
     * Returns whether [state] is present in [flags].
     *
     * This checks only the state bit. Callers must separately verify any required trait.
     *
     * @param flags Bitmask from [SemanticNodeData.stateFlags].
     * @param state State bit to test.
     * @return `true` when [state] is set.
     */
    fun has(flags: Int, state: Int): Boolean = (flags and state) != 0

    /**
     * Decodes the two-bit check-state field.
     *
     * The field can physically encode an unassigned fourth value. That value is treated as
     * [SemanticCheckState.Mixed], preserving the historical behavior when both former check-state
     * bits were set. Callers must still verify [SemanticTrait.Checkable].
     *
     * @param flags Bitmask from [SemanticNodeData.stateFlags].
     * @return The decoded check state.
     */
    fun checkState(flags: Int): SemanticCheckState =
        when ((flags and CHECK_STATE_MASK) ushr CHECK_STATE_OFFSET) {
            0 -> SemanticCheckState.Unchecked
            1 -> SemanticCheckState.Checked
            else -> SemanticCheckState.Mixed
        }

    /**
     * Returns whether the decoded check state is checked.
     *
     * This checks the state field only; callers must still verify [SemanticTrait.Checkable].
     *
     * @param flags Bitmask from [SemanticNodeData.stateFlags].
     * @return `true` when [checkState] returns [SemanticCheckState.Checked].
     */
    fun effectiveChecked(flags: Int): Boolean =
        checkState(flags) == SemanticCheckState.Checked

    /**
     * Returns whether the effective check state is mixed or indeterminate.
     *
     * This checks the state field only; callers must still verify [SemanticTrait.Checkable].
     *
     * @param flags Bitmask from [SemanticNodeData.stateFlags].
     * @return `true` when [checkState] returns [SemanticCheckState.Mixed].
     */
    fun effectiveMixed(flags: Int): Boolean =
        checkState(flags) == SemanticCheckState.Mixed
}

/**
 * Full node payload used by `added`, `moved`, and `updatedSemantic`.
 */
internal data class SemanticsDiffNode(
    val id: Int,
    val role: Int,
    val label: String,
    val value: String,
    val hint: String,
    val stateFlags: Int,
    val traitFlags: Int,
    val headingLevel: Int,
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
    val parentId: Int,
    val siblingIndex: Int,
)

/**
 * Authoritative child ordering for a parent.
 */
internal data class SemanticsChildrenUpdate(
    val parentId: Int,
    val childIds: IntArray,
)

/**
 * Bounds-only update.
 */
internal data class SemanticsBoundsUpdate(
    val id: Int,
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
)

/**
 * Incremental semantic diff emitted by the runtime.
 */
internal data class SemanticsDiff(
    val treeVersion: Long,
    val frameNumber: Long,
    val rootId: Int,
    val removed: IntArray,
    val added: Array<SemanticsDiffNode>,
    val moved: Array<SemanticsDiffNode>,
    val childrenUpdated: Array<SemanticsChildrenUpdate>,
    val updatedSemantic: Array<SemanticsDiffNode>,
    val updatedGeometry: Array<SemanticsBoundsUpdate>,
) {
    val isEmpty: Boolean
        get() = removed.isEmpty() &&
            added.isEmpty() &&
            moved.isEmpty() &&
            childrenUpdated.isEmpty() &&
            updatedSemantic.isEmpty() &&
            updatedGeometry.isEmpty()

    companion object {
        val Empty = SemanticsDiff(
            treeVersion = 0L,
            frameNumber = 0L,
            rootId = 0,
            removed = intArrayOf(),
            added = emptyArray(),
            moved = emptyArray(),
            childrenUpdated = emptyArray(),
            updatedSemantic = emptyArray(),
            updatedGeometry = emptyArray()
        )
    }
}

package app.rive.semantics

/** Toolkit-neutral toggle state decoded from Rive semantic flags. */
internal enum class SemanticToggleState {
    Off,
    On,
    Mixed,
}

/**
 * Toolkit-neutral state decoded from a semantic node's trait and state flags.
 *
 * Nullable properties preserve the distinction between a supported capability whose current
 * value is `false` and a capability that does not apply to the node.
 */
internal data class SemanticNodeState(
    val expanded: Boolean?,
    val selected: Boolean?,
    val toggleState: SemanticToggleState?,
    val required: Boolean?,
    val enabled: Boolean?,
    val focused: Boolean?,
    val hidden: Boolean,
    val liveRegion: Boolean,
    val readOnly: Boolean,
    val modal: Boolean,
    val obscured: Boolean,
    val multiline: Boolean,
)

/**
 * Actions an accessibility backend may expose for a semantic node.
 *
 * [semanticActions] are dispatched through `fireSemanticAction`. Focus is separate because it
 * uses the semantic focus API and remains available for disabled nodes that accessibility
 * services can still navigate to.
 */
internal data class SemanticNodeActions(
    val semanticActions: Set<SemanticActionType>,
    val canRequestFocus: Boolean,
)

/** Describes the authored purpose of semantic node content without selecting toolkit properties. */
internal enum class SemanticNodeContentKind {
    General,
    Text,
    TextField,
}

/**
 * Toolkit-neutral behavior for projecting a semantic node's authored children.
 *
 * [Structural] nodes are exported as containers when they carry accessible content; otherwise,
 * their children are promoted into the surrounding hierarchy.
 * [ExplicitContainer] nodes preserve their projected children as discrete descendants.
 * [AbsorbingLeaf] nodes represent their complete accessible meaning and do not export descendants.
 */
internal enum class SemanticNodeTopology {
    Structural,
    ExplicitContainer,
    AbsorbingLeaf,
}

/** Toolkit-neutral result of projecting a semantic node into an accessibility hierarchy. */
internal enum class SemanticNodeProjection {
    /** Excludes the node and all of its descendants from the exported hierarchy. */
    PruneSubtree,

    /** Omits the node while attaching its projected children to the surrounding hierarchy. */
    PromoteChildren,

    /** Exports the node and preserves its projected children as discrete descendants. */
    ExportContainer,

    /** Exports the node while suppressing its descendants as separate accessibility nodes. */
    ExportLeaf,
}

/**
 * Toolkit-neutral semantic content safe for presentation to an accessibility backend.
 *
 * [value] is `null` for obscured text fields so no backend can accidentally expose the authored
 * secret. Text-field-only state is cleared for every other content kind so backends cannot expose
 * incompatible editable-text metadata.
 */
internal data class SemanticNodeContent(
    val kind: SemanticNodeContentKind,
    val label: String?,
    val value: String?,
    val hint: String?,
    val isReadOnly: Boolean,
    val isObscured: Boolean,
    val isMultiline: Boolean,
)

/**
 * Classifies this role's child topology without selecting a platform accessibility API.
 *
 * `None` remains structural because its accessible content determines whether it is exported or
 * its children are promoted. Explicit container roles preserve child nodes. All concrete content
 * and control roles absorb descendants because their authored label represents the complete
 * accessible node.
 *
 * @return Toolkit-neutral child projection behavior for this role.
 */
internal fun SemanticRole.toSemanticNodeTopology(): SemanticNodeTopology = when (this) {
    SemanticRole.None -> SemanticNodeTopology.Structural
    SemanticRole.Group,
    SemanticRole.List,
    SemanticRole.TabList,
    SemanticRole.Dialog,
    SemanticRole.AlertDialog,
    SemanticRole.RadioGroup -> SemanticNodeTopology.ExplicitContainer
    SemanticRole.Button,
    SemanticRole.Link,
    SemanticRole.Checkbox,
    SemanticRole.SwitchControl,
    SemanticRole.Slider,
    SemanticRole.TextField,
    SemanticRole.Text,
    SemanticRole.Image,
    SemanticRole.ListItem,
    SemanticRole.Tab,
    SemanticRole.RadioButton -> SemanticNodeTopology.AbsorbingLeaf
}

/**
 * Returns whether this role and [state] identify an active modal container.
 *
 * The modal state is role-gated by the core semantics contract. A stray modal bit on any other
 * role must not restrict the exported accessibility hierarchy.
 *
 * @param state Classified semantic state for this node.
 * @return `true` for a modal dialog or alert-dialog container.
 */
internal fun SemanticRole.isActiveModal(state: SemanticNodeState): Boolean =
    state.modal && (this == SemanticRole.Dialog || this == SemanticRole.AlertDialog)

/**
 * Classifies how a semantic node participates in an exported accessibility hierarchy.
 *
 * Hidden nodes prune their complete subtree. Nodes without finite positive-area bounds are omitted
 * while their children remain eligible for export. Otherwise, structural nodes are exported only
 * when they carry accessible content, explicit containers retain their projected children, and
 * absorbing leaves suppress descendants.
 *
 * @param role Authored semantic role that determines the node's topology.
 * @param content Classified accessible content used to distinguish structural containers from
 * pass-through nodes.
 * @param state Classified node state used to remove hidden subtrees.
 * @param minX First horizontal edge of the node's bounds.
 * @param minY First vertical edge of the node's bounds.
 * @param maxX Second horizontal edge of the node's bounds.
 * @param maxY Second vertical edge of the node's bounds.
 * @return The node's toolkit-neutral hierarchy projection.
 */
internal fun classifySemanticNodeProjection(
    role: SemanticRole,
    content: SemanticNodeContent,
    state: SemanticNodeState,
    minX: Float,
    minY: Float,
    maxX: Float,
    maxY: Float,
): SemanticNodeProjection {
    if (state.hidden) {
        return SemanticNodeProjection.PruneSubtree
    }
    if (!hasFinitePositiveArea(minX, minY, maxX, maxY)) {
        return SemanticNodeProjection.PromoteChildren
    }

    return when (role.toSemanticNodeTopology()) {
        SemanticNodeTopology.Structural -> if (content.hasAccessibleContent()) {
            SemanticNodeProjection.ExportContainer
        } else {
            SemanticNodeProjection.PromoteChildren
        }
        SemanticNodeTopology.ExplicitContainer -> SemanticNodeProjection.ExportContainer
        SemanticNodeTopology.AbsorbingLeaf -> SemanticNodeProjection.ExportLeaf
    }
}

/**
 * Decodes semantic trait and state flags without depending on a UI toolkit.
 *
 * Checkable state takes precedence over toggleable state when both traits are present.
 *
 * @param traitFlags Bitmask composed from [SemanticTrait] values.
 * @param stateFlags Bitmask composed from [SemanticState] values.
 * @return Decoded state with trait-gated values omitted when their capability does not apply.
 */
internal fun mapSemanticNodeState(traitFlags: Int, stateFlags: Int): SemanticNodeState {
    val toggleState = when {
        SemanticTrait.has(traitFlags, SemanticTrait.Checkable) -> when (
            SemanticState.checkState(stateFlags)
        ) {
            SemanticCheckState.Unchecked -> SemanticToggleState.Off
            SemanticCheckState.Checked -> SemanticToggleState.On
            SemanticCheckState.Mixed -> SemanticToggleState.Mixed
        }
        SemanticTrait.has(traitFlags, SemanticTrait.Toggleable) -> {
            if (SemanticState.has(stateFlags, SemanticState.Toggled)) {
                SemanticToggleState.On
            } else {
                SemanticToggleState.Off
            }
        }
        else -> null
    }

    return SemanticNodeState(
        expanded = gatedState(
            traitFlags,
            SemanticTrait.Expandable,
            stateFlags,
            SemanticState.Expanded
        ),
        selected = gatedState(
            traitFlags,
            SemanticTrait.Selectable,
            stateFlags,
            SemanticState.Selected
        ),
        toggleState = toggleState,
        required = gatedState(
            traitFlags,
            SemanticTrait.Requirable,
            stateFlags,
            SemanticState.Required
        ),
        enabled = gatedState(
            traitFlags,
            SemanticTrait.Enablable,
            stateFlags,
            SemanticState.Disabled
        )?.not(),
        focused = gatedState(
            traitFlags,
            SemanticTrait.Focusable,
            stateFlags,
            SemanticState.Focused
        ),
        hidden = SemanticState.has(stateFlags, SemanticState.Hidden),
        liveRegion = SemanticState.has(stateFlags, SemanticState.LiveRegion),
        readOnly = SemanticState.has(stateFlags, SemanticState.ReadOnly),
        modal = SemanticState.has(stateFlags, SemanticState.Modal),
        obscured = SemanticState.has(stateFlags, SemanticState.Obscured),
        multiline = SemanticState.has(stateFlags, SemanticState.Multiline)
    )
}

/**
 * Classifies the actions an accessibility backend may expose for a semantic node.
 *
 * Explicitly disabled nodes expose no tap, increase, or decrease action. Nodes without the
 * enablable trait retain their role actions because enabled state does not apply to them.
 *
 * @param role Rive role that determines supported semantic actions.
 * @param state Decoded node state used for disabled and focus capability checks.
 * @return Available semantic actions and focus-request capability.
 */
internal fun mapSemanticNodeActions(
    role: SemanticRole,
    state: SemanticNodeState,
): SemanticNodeActions {
    val semanticActions = when {
        state.enabled == false -> emptySet()
        role in tapRoles -> setOf(SemanticActionType.Tap)
        role == SemanticRole.Slider -> setOf(
            SemanticActionType.Increase,
            SemanticActionType.Decrease
        )
        else -> emptySet()
    }

    return SemanticNodeActions(
        semanticActions = semanticActions,
        canRequestFocus = state.focused != null
    )
}

/**
 * Classifies authored content without selecting backend-specific accessibility properties.
 *
 * @param role Rive role that determines the authored content kind.
 * @param label Authored accessible label.
 * @param value Authored current value.
 * @param hint Authored usage hint.
 * @param state Decoded node state used to redact obscured text-field values.
 * @return Toolkit-neutral content with obscured text-field values removed.
 */
internal fun mapSemanticNodeContent(
    role: SemanticRole,
    label: String,
    value: String,
    hint: String,
    state: SemanticNodeState,
): SemanticNodeContent {
    val kind = when (role) {
        SemanticRole.Text -> SemanticNodeContentKind.Text
        SemanticRole.TextField -> SemanticNodeContentKind.TextField
        else -> SemanticNodeContentKind.General
    }
    val isTextField = kind == SemanticNodeContentKind.TextField
    val isObscured = isTextField && state.obscured

    return SemanticNodeContent(
        kind = kind,
        label = label.ifEmpty { null },
        value = when {
            isObscured -> null
            isTextField -> value
            else -> value.ifEmpty { null }
        },
        hint = hint.ifEmpty { null },
        isReadOnly = isTextField && state.readOnly,
        isObscured = isObscured,
        isMultiline = isTextField && state.multiline
    )
}

/**
 * Reads a state only when its corresponding trait applies to the node.
 *
 * @param traitFlags Node trait bitmask.
 * @param trait Trait required for the state to apply.
 * @param stateFlags Node state bitmask.
 * @param state State whose value should be read.
 * @return The state value, or `null` when the trait is absent.
 */
private fun gatedState(
    traitFlags: Int,
    trait: Int,
    stateFlags: Int,
    state: Int,
): Boolean? = if (SemanticTrait.has(traitFlags, trait)) {
    SemanticState.has(stateFlags, state)
} else {
    null
}

/** Returns whether this content contributes anything an accessibility service can announce. */
private fun SemanticNodeContent.hasAccessibleContent(): Boolean =
    !label.isNullOrEmpty() || !value.isNullOrEmpty() || !hint.isNullOrEmpty()

/** Returns whether the supplied edges describe finite bounds with positive width and height. */
private fun hasFinitePositiveArea(
    minX: Float,
    minY: Float,
    maxX: Float,
    maxY: Float,
): Boolean = minX.isFinite() && minY.isFinite() && maxX.isFinite() && maxY.isFinite() &&
    minX != maxX && minY != maxY

/** Rive roles whose primary accessibility action dispatches a semantic tap. */
private val tapRoles = setOf(
    SemanticRole.Button,
    SemanticRole.Link,
    SemanticRole.Checkbox,
    SemanticRole.SwitchControl,
    SemanticRole.Tab,
    SemanticRole.RadioButton
)

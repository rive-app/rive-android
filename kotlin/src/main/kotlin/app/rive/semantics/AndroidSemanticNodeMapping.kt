package app.rive.semantics

import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * Maps this Rive semantic role to a faithful Android accessibility class name.
 *
 * Android accessibility services use this metadata to understand the standard control represented
 * by a virtual node. It does not instantiate or otherwise depend on the corresponding View widget.
 * Roles without a direct Android equivalent remain unset so their behavior can be expressed
 * through other node properties rather than claiming a misleading widget type.
 *
 * @return The matching Android class name, or `null` when there is no direct equivalent.
 */
internal fun SemanticRole.toAndroidAccessibilityClassName(): String? = when (this) {
    SemanticRole.Button -> "android.widget.Button"
    SemanticRole.Checkbox -> "android.widget.CheckBox"
    SemanticRole.SwitchControl -> "android.widget.Switch"
    SemanticRole.Slider -> "android.widget.SeekBar"
    SemanticRole.TextField -> "android.widget.EditText"
    SemanticRole.Text -> "android.widget.TextView"
    SemanticRole.Image -> "android.widget.ImageView"
    SemanticRole.List -> "android.widget.ListView"
    SemanticRole.RadioGroup -> "android.widget.RadioGroup"
    SemanticRole.RadioButton -> "android.widget.RadioButton"
    SemanticRole.None,
    SemanticRole.Link,
    SemanticRole.Group,
    SemanticRole.ListItem,
    SemanticRole.Tab,
    SemanticRole.TabList,
    SemanticRole.Dialog,
    SemanticRole.AlertDialog -> null
}

/**
 * Applies the direct Android accessibility role mapping to this node.
 *
 * @param role Rive role to expose through Android accessibility.
 */
internal fun AccessibilityNodeInfoCompat.applySemanticNodeRole(role: SemanticRole) {
    role.toAndroidAccessibilityClassName()?.let { mappedClassName ->
        className = mappedClassName
    }
}

/**
 * Returns whether an authored Rive heading level represents an Android heading.
 *
 * Android accessibility exposes heading status but not the authored numeric heading level, so all
 * positive Rive levels map to the same platform property.
 *
 * @param headingLevel Authored Rive heading level, where zero means not a heading.
 * @return `true` when the node should be exposed as an Android accessibility heading.
 */
internal fun isAndroidAccessibilityHeading(headingLevel: Int): Boolean = headingLevel > 0

/**
 * Applies an authored Rive heading level to this Android accessibility node.
 *
 * @param headingLevel Authored Rive heading level, where zero means not a heading.
 */
internal fun AccessibilityNodeInfoCompat.applySemanticNodeHeading(headingLevel: Int) {
    isHeading = isAndroidAccessibilityHeading(headingLevel)
}

/**
 * Maps this node's trait-gated expanded state to Android accessibility metadata.
 *
 * Rive currently represents expansion as a binary state, so an expanded node maps to fully
 * expanded rather than Android's partially expanded state.
 *
 * @return The Android expanded-state constant, or `null` when expansion does not apply.
 */
internal fun SemanticNodeState.toAndroidAccessibilityExpandedState(): Int? =
    expanded?.let { isExpanded ->
        if (isExpanded) {
            AccessibilityNodeInfo.EXPANDED_STATE_FULL
        } else {
            AccessibilityNodeInfo.EXPANDED_STATE_COLLAPSED
        }
    }

/**
 * Maps this toolkit-neutral toggle state to Android's checked-state metadata.
 *
 * @return The corresponding Android checked-state constant.
 */
internal fun SemanticToggleState.toAndroidAccessibilityCheckedState(): Int = when (this) {
    SemanticToggleState.Off -> AccessibilityNodeInfo.CHECKED_STATE_FALSE
    SemanticToggleState.On -> AccessibilityNodeInfo.CHECKED_STATE_TRUE
    SemanticToggleState.Mixed -> AccessibilityNodeInfo.CHECKED_STATE_PARTIAL
}

/**
 * Maps this node's live-region state to Android accessibility metadata.
 *
 * @return Android's polite live-region constant when active, or its none constant otherwise.
 */
internal fun SemanticNodeState.toAndroidAccessibilityLiveRegion(): Int = if (liveRegion) {
    View.ACCESSIBILITY_LIVE_REGION_POLITE
} else {
    View.ACCESSIBILITY_LIVE_REGION_NONE
}

/**
 * Applies generic semantic state that maps directly to Android accessibility node properties.
 *
 * Hidden state is handled while projecting the tree. Semantic focus is synchronized through the
 * accessibility node provider rather than Android input-focus properties. Modal and text-field
 * states require their respective container and content mappings.
 *
 * @param state Toolkit-neutral state classified by [mapSemanticNodeState].
 */
internal fun AccessibilityNodeInfoCompat.applySemanticNodeState(state: SemanticNodeState) {
    state.toAndroidAccessibilityExpandedState()?.let(::setExpandedState)
    state.selected?.let { isSelected ->
        this.isSelected = isSelected
    }
    state.toggleState?.let { toggleState ->
        isCheckable = true
        setChecked(toggleState.toAndroidAccessibilityCheckedState())
    }
    state.required?.let(::setFieldRequired)
    state.enabled?.let { isEnabled = it }
    liveRegion = state.toAndroidAccessibilityLiveRegion()
}

/**
 * Maps this Rive semantic action to the corresponding standard Android accessibility action ID.
 *
 * @return The Android accessibility action ID used by services and node providers.
 */
internal fun SemanticActionType.toAndroidAccessibilityActionId(): Int = when (this) {
    SemanticActionType.Tap -> AccessibilityNodeInfoCompat.ACTION_CLICK
    SemanticActionType.Increase -> AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD
    SemanticActionType.Decrease -> AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD
}

/**
 * Advertises the semantic actions supported by this Android accessibility node.
 *
 * Semantic focus is intentionally omitted because [SemanticNodeActions.canRequestFocus] is handled
 * through the virtual-node provider's standard accessibility-focus actions.
 *
 * @param actions Toolkit-neutral actions classified by [mapSemanticNodeActions].
 */
internal fun AccessibilityNodeInfoCompat.applySemanticNodeActions(actions: SemanticNodeActions) {
    isClickable = SemanticActionType.Tap in actions.semanticActions
    actions.semanticActions.forEach { semanticAction ->
        addAction(
            AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                semanticAction.toAndroidAccessibilityActionId(),
                null
            )
        )
    }
}

/**
 * Applies classified Rive content to native Android accessibility properties.
 *
 * Controls expose their label as a content description and current value as a state description.
 * Static text uses Android's text property so services treat it as displayed content. Text fields
 * keep their accessible label separate from their entered text and expose password,
 * sensitive-data, and multiline metadata. They remain non-editable until Rive core exposes text
 * mutation and IME commands. Authored usage hints use supplemental description for every content
 * kind because they neither replace the label nor describe current state.
 *
 * Obscured text-field values have already been removed by [mapSemanticNodeContent] and are never
 * assigned to the Android node.
 *
 * @param content Toolkit-neutral content classified by [mapSemanticNodeContent].
 */
internal fun AccessibilityNodeInfoCompat.applySemanticNodeContent(content: SemanticNodeContent) {
    when (content.kind) {
        SemanticNodeContentKind.General -> {
            contentDescription = content.label
            stateDescription = content.value
        }
        SemanticNodeContentKind.Text -> {
            text = content.label
            stateDescription = content.value
        }
        SemanticNodeContentKind.TextField -> {
            contentDescription = content.label
            text = content.value
            // TODO: Expose editability and standard editing actions when core supports text input.
            isEditable = false
            isPassword = content.isObscured
            isAccessibilityDataSensitive = content.isObscured
            isMultiLine = content.isMultiline
        }
    }
    supplementalDescription = content.hint
}

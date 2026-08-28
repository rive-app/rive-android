package app.rive.semantics.compose

import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.isSensitiveData
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import app.rive.semantics.SemanticNodeContent
import app.rive.semantics.SemanticNodeContentKind
import app.rive.semantics.SemanticNodeState
import app.rive.semantics.SemanticRole
import app.rive.semantics.SemanticToggleState
import app.rive.semantics.mapSemanticNodeContent
import app.rive.semantics.mapSemanticNodeState

/** Compose container behavior associated with a Rive semantic role. */
internal enum class ComposeSemanticNodeContainer {
    None,
    Group,
    Collection,
    SelectableGroup,
    Dialog,
}

/**
 * Maps this Rive semantic role to its direct Compose equivalent.
 *
 * Roles without an honest Compose equivalent remain unset so Android does not announce an
 * incorrect role. Their platform behavior must instead be expressed through other semantics
 * properties.
 *
 * @return The matching Compose [Role], or `null` when Compose has no direct equivalent.
 */
internal fun SemanticRole.toComposeRole(): Role? = when (this) {
    SemanticRole.Button -> Role.Button
    SemanticRole.Checkbox -> Role.Checkbox
    SemanticRole.SwitchControl -> Role.Switch
    SemanticRole.Slider -> Role.ValuePicker
    SemanticRole.Image -> Role.Image
    SemanticRole.Tab -> Role.Tab
    SemanticRole.RadioButton -> Role.RadioButton
    SemanticRole.None,
    SemanticRole.Link,
    SemanticRole.TextField,
    SemanticRole.Text,
    SemanticRole.Group,
    SemanticRole.List,
    SemanticRole.ListItem,
    SemanticRole.TabList,
    SemanticRole.Dialog,
    SemanticRole.AlertDialog,
    SemanticRole.RadioGroup -> null
}

/**
 * Classifies this role's Compose container behavior.
 *
 * @return Container behavior to apply without changing semantic tree topology.
 */
internal fun SemanticRole.toComposeSemanticNodeContainer(): ComposeSemanticNodeContainer =
    when (this) {
        SemanticRole.Group -> ComposeSemanticNodeContainer.Group
        SemanticRole.List -> ComposeSemanticNodeContainer.Collection
        SemanticRole.TabList,
        SemanticRole.RadioGroup -> ComposeSemanticNodeContainer.SelectableGroup
        SemanticRole.Dialog,
        SemanticRole.AlertDialog -> ComposeSemanticNodeContainer.Dialog
        SemanticRole.None,
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
        SemanticRole.RadioButton -> ComposeSemanticNodeContainer.None
    }

/**
 * Applies classified content to Compose semantics properties.
 *
 * Static text uses Compose text semantics instead of a content description. Text fields expose an
 * empty visual value when obscured because Rive does not provide a masked representation, and
 * remain non-editable until Rive core exposes text mutation and IME commands. Other roles expose
 * value and hint through the current state-description fallback.
 *
 * @param content Toolkit-neutral content classified by [mapSemanticNodeContent].
 */
internal fun SemanticsPropertyReceiver.applySemanticNodeContent(content: SemanticNodeContent) {
    when (content.kind) {
        SemanticNodeContentKind.General -> {
            content.label?.let { description ->
                contentDescription = description
            }
            joinContentDescriptions(content.value, content.hint)?.let { description ->
                stateDescription = description
            }
        }
        SemanticNodeContentKind.Text -> {
            content.label?.let { staticText ->
                text = AnnotatedString(staticText)
            }
            joinContentDescriptions(content.value, content.hint)?.let { description ->
                stateDescription = description
            }
        }
        SemanticNodeContentKind.TextField -> {
            content.label?.let { description ->
                contentDescription = description
            }
            editableText = AnnotatedString(content.value.orEmpty())
            content.hint?.let { description ->
                stateDescription = description
            }
            // TODO: Expose editability and standard editing actions when core supports text input.
            if (content.isObscured) {
                password()
                isSensitiveData = true
            }
        }
    }
}

/**
 * Applies supported toolkit-neutral state to Compose semantics properties.
 *
 * Expanded, required, multiline, modal, and semantic focus need separate platform decisions and
 * are intentionally not approximated here.
 *
 * @param state State classified by [mapSemanticNodeState].
 */
internal fun SemanticsPropertyReceiver.applySemanticNodeState(state: SemanticNodeState) {
    if (state.liveRegion) {
        liveRegion = LiveRegionMode.Polite
    }
    if (state.enabled == false) {
        disabled()
    }
    state.selected?.let { isSelected ->
        selected = isSelected
    }
    state.toggleState?.let { toggleState ->
        toggleableState = toggleState.toComposeToggleableState()
    }
}

/**
 * Applies pre-classified container behavior to Compose semantics properties.
 *
 * List orientation and size are not encoded by the Rive role, so collection dimensions remain
 * unknown rather than assuming a vertical list. Compose derives selectable-group size from child
 * semantics.
 *
 * @param container Container behavior classified by [toComposeSemanticNodeContainer].
 */
internal fun SemanticsPropertyReceiver.applySemanticNodeContainer(
    container: ComposeSemanticNodeContainer
) {
    when (container) {
        ComposeSemanticNodeContainer.None -> Unit
        ComposeSemanticNodeContainer.Group -> {
            isTraversalGroup = true
        }
        ComposeSemanticNodeContainer.Collection -> {
            isTraversalGroup = true
            collectionInfo = CollectionInfo(rowCount = -1, columnCount = -1)
        }
        ComposeSemanticNodeContainer.SelectableGroup -> {
            isTraversalGroup = true
            selectableGroup()
        }
        ComposeSemanticNodeContainer.Dialog -> {
            isTraversalGroup = true
            dialog()
        }
    }
}

/**
 * Applies authored sibling order within the containing Compose traversal group.
 *
 * Compose otherwise considers node geometry when ordering accessibility traversal, which can
 * differ from the order authored in Rive.
 *
 * @param siblingIndex Zero-based position in the semantic tree's authoritative child list.
 */
internal fun SemanticsPropertyReceiver.applySemanticNodeTraversal(siblingIndex: Int) {
    traversalIndex = siblingIndex.toFloat()
}

/**
 * Converts toolkit-neutral toggle state to its Compose representation.
 *
 * @return Compose toggle state with mixed represented as indeterminate.
 */
private fun SemanticToggleState.toComposeToggleableState(): ToggleableState = when (this) {
    SemanticToggleState.Off -> ToggleableState.Off
    SemanticToggleState.On -> ToggleableState.On
    SemanticToggleState.Mixed -> ToggleableState.Indeterminate
}

/** Combines non-empty authored value and hint content for Compose's current state fallback. */
private fun joinContentDescriptions(value: String?, hint: String?): String? =
    listOfNotNull(value, hint).joinToString(", ").ifEmpty { null }

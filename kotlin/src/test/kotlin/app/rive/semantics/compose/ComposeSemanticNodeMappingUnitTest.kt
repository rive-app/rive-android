package app.rive.semantics.compose

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import app.rive.semantics.SemanticRole
import app.rive.semantics.SemanticState
import app.rive.semantics.SemanticTrait
import app.rive.semantics.mapSemanticNodeContent
import app.rive.semantics.mapSemanticNodeState
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val CHECK_STATE_MIXED_FLAGS = 2 shl 2

class ComposeSemanticNodeMappingUnitTest : FunSpec({
    test("Every semantic role has an explicit Compose role decision") {
        val expected = mapOf(
            SemanticRole.None to null,
            SemanticRole.Button to Role.Button,
            SemanticRole.Link to null,
            SemanticRole.Checkbox to Role.Checkbox,
            SemanticRole.SwitchControl to Role.Switch,
            SemanticRole.Slider to Role.ValuePicker,
            SemanticRole.TextField to null,
            SemanticRole.Text to null,
            SemanticRole.Image to Role.Image,
            SemanticRole.Group to null,
            SemanticRole.List to null,
            SemanticRole.ListItem to null,
            SemanticRole.Tab to Role.Tab,
            SemanticRole.TabList to null,
            SemanticRole.Dialog to null,
            SemanticRole.AlertDialog to null,
            SemanticRole.RadioGroup to null,
            SemanticRole.RadioButton to Role.RadioButton
        )

        expected.keys shouldBe SemanticRole.entries.toSet()
        expected.forEach { (semanticRole, composeRole) ->
            withClue(semanticRole) {
                semanticRole.toComposeRole() shouldBe composeRole
            }
        }
    }

    test("Every semantic role has an explicit Compose container decision") {
        val expected = mapOf(
            SemanticRole.None to ComposeSemanticNodeContainer.None,
            SemanticRole.Button to ComposeSemanticNodeContainer.None,
            SemanticRole.Link to ComposeSemanticNodeContainer.None,
            SemanticRole.Checkbox to ComposeSemanticNodeContainer.None,
            SemanticRole.SwitchControl to ComposeSemanticNodeContainer.None,
            SemanticRole.Slider to ComposeSemanticNodeContainer.None,
            SemanticRole.TextField to ComposeSemanticNodeContainer.None,
            SemanticRole.Text to ComposeSemanticNodeContainer.None,
            SemanticRole.Image to ComposeSemanticNodeContainer.None,
            SemanticRole.Group to ComposeSemanticNodeContainer.Group,
            SemanticRole.List to ComposeSemanticNodeContainer.Collection,
            SemanticRole.ListItem to ComposeSemanticNodeContainer.None,
            SemanticRole.Tab to ComposeSemanticNodeContainer.None,
            SemanticRole.TabList to ComposeSemanticNodeContainer.SelectableGroup,
            SemanticRole.Dialog to ComposeSemanticNodeContainer.Dialog,
            SemanticRole.AlertDialog to ComposeSemanticNodeContainer.Dialog,
            SemanticRole.RadioGroup to ComposeSemanticNodeContainer.SelectableGroup,
            SemanticRole.RadioButton to ComposeSemanticNodeContainer.None
        )

        expected.keys shouldBe SemanticRole.entries.toSet()
        expected.forEach { (semanticRole, container) ->
            withClue(semanticRole) {
                semanticRole.toComposeSemanticNodeContainer() shouldBe container
            }
        }
    }

    test("Group container applies only a traversal boundary") {
        val config = SemanticsConfiguration().apply {
            applySemanticNodeContainer(ComposeSemanticNodeContainer.Group)
        }

        config[SemanticsProperties.IsTraversalGroup] shouldBe true
        config.contains(SemanticsProperties.CollectionInfo) shouldBe false
        config.contains(SemanticsProperties.SelectableGroup) shouldBe false
        config.contains(SemanticsProperties.IsDialog) shouldBe false
    }

    test("Collection container uses unknown dimensions") {
        val config = SemanticsConfiguration().apply {
            applySemanticNodeContainer(ComposeSemanticNodeContainer.Collection)
        }
        val collectionInfo = config[SemanticsProperties.CollectionInfo]

        config[SemanticsProperties.IsTraversalGroup] shouldBe true
        collectionInfo.rowCount shouldBe -1
        collectionInfo.columnCount shouldBe -1
    }

    test("Selectable container applies selectable group semantics") {
        val config = SemanticsConfiguration().apply {
            applySemanticNodeContainer(ComposeSemanticNodeContainer.SelectableGroup)
        }

        config[SemanticsProperties.IsTraversalGroup] shouldBe true
        config.contains(SemanticsProperties.SelectableGroup) shouldBe true
        config.contains(SemanticsProperties.CollectionInfo) shouldBe false
    }

    test("Dialog container applies native dialog semantics") {
        val config = SemanticsConfiguration().apply {
            applySemanticNodeContainer(ComposeSemanticNodeContainer.Dialog)
        }

        config[SemanticsProperties.IsTraversalGroup] shouldBe true
        config.contains(SemanticsProperties.IsDialog) shouldBe true
    }

    test("Non-container applies no container semantics") {
        val config = SemanticsConfiguration().apply {
            applySemanticNodeContainer(ComposeSemanticNodeContainer.None)
        }

        config.contains(SemanticsProperties.IsTraversalGroup) shouldBe false
        config.contains(SemanticsProperties.CollectionInfo) shouldBe false
        config.contains(SemanticsProperties.SelectableGroup) shouldBe false
        config.contains(SemanticsProperties.IsDialog) shouldBe false
    }

    test("Authored sibling index applies Compose traversal order") {
        val config = SemanticsConfiguration().apply {
            applySemanticNodeTraversal(siblingIndex = 3)
        }

        config[SemanticsProperties.TraversalIndex] shouldBe 3f
    }

    test("Supported state applies native Compose properties") {
        val state = mapSemanticNodeState(
            traitFlags = SemanticTrait.Selectable or
                SemanticTrait.Checkable or
                SemanticTrait.Enablable,
            stateFlags = SemanticState.Selected or
                CHECK_STATE_MIXED_FLAGS or
                SemanticState.Disabled or
                SemanticState.LiveRegion
        )
        val config = SemanticsConfiguration().apply {
            applySemanticNodeState(state)
        }

        config[SemanticsProperties.Selected] shouldBe true
        config[SemanticsProperties.ToggleableState] shouldBe ToggleableState.Indeterminate
        config.contains(SemanticsProperties.Disabled) shouldBe true
        config[SemanticsProperties.LiveRegion] shouldBe LiveRegionMode.Polite
    }

    test("Static content applies native Compose text semantics") {
        val content = mapSemanticNodeContent(
            role = SemanticRole.Text,
            label = "Rendered text",
            value = "Current value",
            hint = "Updated today",
            state = mapSemanticNodeState(traitFlags = 0, stateFlags = 0)
        )
        val config = SemanticsConfiguration().apply {
            applySemanticNodeContent(content)
        }

        config[SemanticsProperties.Text] shouldBe listOf(AnnotatedString("Rendered text"))
        config[SemanticsProperties.StateDescription] shouldBe "Current value, Updated today"
        config.contains(SemanticsProperties.ContentDescription) shouldBe false
        config.contains(SemanticsProperties.EditableText) shouldBe false
    }

    test("Description content preserves label value and hint mapping") {
        val content = mapSemanticNodeContent(
            role = SemanticRole.Button,
            label = "Submit",
            value = "Ready",
            hint = "Activates form",
            state = mapSemanticNodeState(traitFlags = 0, stateFlags = 0)
        )
        val config = SemanticsConfiguration().apply {
            applySemanticNodeContent(content)
        }

        config[SemanticsProperties.ContentDescription] shouldBe listOf("Submit")
        config[SemanticsProperties.StateDescription] shouldBe "Ready, Activates form"
    }

    test("Obscured content applies password semantics without raw input") {
        val secret = "do not expose"
        val content = mapSemanticNodeContent(
            role = SemanticRole.TextField,
            label = "Password",
            value = secret,
            hint = "Required",
            state = mapSemanticNodeState(
                traitFlags = 0,
                stateFlags = SemanticState.Obscured
            )
        )
        val config = SemanticsConfiguration().apply {
            applySemanticNodeContent(content)
        }

        config[SemanticsProperties.ContentDescription] shouldBe listOf("Password")
        config[SemanticsProperties.EditableText] shouldBe AnnotatedString("")
        config[SemanticsProperties.StateDescription] shouldBe "Required"
        config.contains(SemanticsProperties.InputText) shouldBe false
        config.contains(SemanticsProperties.IsEditable) shouldBe false
        config.contains(SemanticsProperties.Password) shouldBe true
        config[SemanticsProperties.IsSensitiveData] shouldBe true
        config.toString().contains(secret) shouldBe false
    }

    test("Read-only content omits Compose editability property") {
        val content = mapSemanticNodeContent(
            role = SemanticRole.TextField,
            label = "Identifier",
            value = "1234",
            hint = "",
            state = mapSemanticNodeState(
                traitFlags = 0,
                stateFlags = SemanticState.ReadOnly
            )
        )
        val config = SemanticsConfiguration().apply {
            applySemanticNodeContent(content)
        }

        config[SemanticsProperties.EditableText] shouldBe AnnotatedString("1234")
        config.contains(SemanticsProperties.IsEditable) shouldBe false
    }
})

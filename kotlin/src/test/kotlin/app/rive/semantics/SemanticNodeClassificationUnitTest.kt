package app.rive.semantics

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val CHECK_STATE_CHECKED_FLAGS = 1 shl 2
private const val CHECK_STATE_MIXED_FLAGS = 2 shl 2
private const val CHECK_STATE_RESERVED_FLAGS = 3 shl 2

class SemanticNodeClassificationUnitTest : FunSpec({
    test("Trait-gated state is absent when its capability does not apply") {
        val gatedStateFlags = SemanticState.Expanded or
            SemanticState.Selected or
            CHECK_STATE_MIXED_FLAGS or
            SemanticState.Toggled or
            SemanticState.Required or
            SemanticState.Disabled or
            SemanticState.Focused

        val state = mapSemanticNodeState(traitFlags = 0, stateFlags = gatedStateFlags)

        state.expanded shouldBe null
        state.selected shouldBe null
        state.toggleState shouldBe null
        state.required shouldBe null
        state.enabled shouldBe null
        state.focused shouldBe null
    }

    test("Trait-gated state represents applicable false values") {
        val allTraitFlags = SemanticTrait.Expandable or
            SemanticTrait.Selectable or
            SemanticTrait.Checkable or
            SemanticTrait.Toggleable or
            SemanticTrait.Requirable or
            SemanticTrait.Enablable or
            SemanticTrait.Focusable

        val state = mapSemanticNodeState(traitFlags = allTraitFlags, stateFlags = 0)

        state.expanded shouldBe false
        state.selected shouldBe false
        state.toggleState shouldBe SemanticToggleState.Off
        state.required shouldBe false
        state.enabled shouldBe true
        state.focused shouldBe false
    }

    test("Trait-gated state represents applicable true values") {
        val allTraitFlags = SemanticTrait.Expandable or
            SemanticTrait.Selectable or
            SemanticTrait.Checkable or
            SemanticTrait.Toggleable or
            SemanticTrait.Requirable or
            SemanticTrait.Enablable or
            SemanticTrait.Focusable
        val allGatedStateFlags = SemanticState.Expanded or
            SemanticState.Selected or
            CHECK_STATE_MIXED_FLAGS or
            SemanticState.Toggled or
            SemanticState.Required or
            SemanticState.Disabled or
            SemanticState.Focused

        val state = mapSemanticNodeState(
            traitFlags = allTraitFlags,
            stateFlags = allGatedStateFlags
        )

        state.expanded shouldBe true
        state.selected shouldBe true
        state.toggleState shouldBe SemanticToggleState.Mixed
        state.required shouldBe true
        state.enabled shouldBe false
        state.focused shouldBe true
    }

    test("Checkable state decodes its tri-state field and wins over toggleable state") {
        val checkable = SemanticTrait.Checkable
        val toggleable = SemanticTrait.Toggleable

        mapSemanticNodeState(checkable, 0)
            .toggleState shouldBe SemanticToggleState.Off
        mapSemanticNodeState(checkable, CHECK_STATE_CHECKED_FLAGS)
            .toggleState shouldBe SemanticToggleState.On
        mapSemanticNodeState(checkable, CHECK_STATE_MIXED_FLAGS)
            .toggleState shouldBe SemanticToggleState.Mixed
        mapSemanticNodeState(checkable, CHECK_STATE_RESERVED_FLAGS)
            .toggleState shouldBe SemanticToggleState.Mixed
        mapSemanticNodeState(toggleable, SemanticState.Toggled)
            .toggleState shouldBe SemanticToggleState.On
        mapSemanticNodeState(checkable or toggleable, SemanticState.Toggled)
            .toggleState shouldBe SemanticToggleState.Off
    }

    test("Non-trait state is decoded directly") {
        val nonTraitStateFlags = SemanticState.Hidden or
            SemanticState.LiveRegion or
            SemanticState.ReadOnly or
            SemanticState.Modal or
            SemanticState.Obscured or
            SemanticState.Multiline

        val state = mapSemanticNodeState(traitFlags = 0, stateFlags = nonTraitStateFlags)

        state.hidden shouldBe true
        state.liveRegion shouldBe true
        state.readOnly shouldBe true
        state.modal shouldBe true
        state.obscured shouldBe true
        state.multiline shouldBe true
    }

    test("Every semantic role has an explicit neutral topology decision") {
        val expected = mapOf(
            SemanticRole.None to SemanticNodeTopology.Structural,
            SemanticRole.Button to SemanticNodeTopology.AbsorbingLeaf,
            SemanticRole.Link to SemanticNodeTopology.AbsorbingLeaf,
            SemanticRole.Checkbox to SemanticNodeTopology.AbsorbingLeaf,
            SemanticRole.SwitchControl to SemanticNodeTopology.AbsorbingLeaf,
            SemanticRole.Slider to SemanticNodeTopology.AbsorbingLeaf,
            SemanticRole.TextField to SemanticNodeTopology.AbsorbingLeaf,
            SemanticRole.Text to SemanticNodeTopology.AbsorbingLeaf,
            SemanticRole.Image to SemanticNodeTopology.AbsorbingLeaf,
            SemanticRole.Group to SemanticNodeTopology.ExplicitContainer,
            SemanticRole.List to SemanticNodeTopology.ExplicitContainer,
            SemanticRole.ListItem to SemanticNodeTopology.AbsorbingLeaf,
            SemanticRole.Tab to SemanticNodeTopology.AbsorbingLeaf,
            SemanticRole.TabList to SemanticNodeTopology.ExplicitContainer,
            SemanticRole.Dialog to SemanticNodeTopology.ExplicitContainer,
            SemanticRole.AlertDialog to SemanticNodeTopology.ExplicitContainer,
            SemanticRole.RadioGroup to SemanticNodeTopology.ExplicitContainer,
            SemanticRole.RadioButton to SemanticNodeTopology.AbsorbingLeaf
        )

        expected.keys shouldBe SemanticRole.entries.toSet()
        expected.forEach { (semanticRole, topology) ->
            withClue(semanticRole) {
                semanticRole.toSemanticNodeTopology() shouldBe topology
            }
        }
    }

    test("Modal state is restricted to dialog container roles") {
        val modalState = mapSemanticNodeState(
            traitFlags = 0,
            stateFlags = SemanticState.Modal,
        )

        SemanticRole.Dialog.isActiveModal(modalState) shouldBe true
        SemanticRole.AlertDialog.isActiveModal(modalState) shouldBe true
        SemanticRole.Group.isActiveModal(modalState) shouldBe false
        SemanticRole.Button.isActiveModal(modalState) shouldBe false
        SemanticRole.Dialog.isActiveModal(
            mapSemanticNodeState(traitFlags = 0, stateFlags = 0)
        ) shouldBe false
    }

    test("Every semantic role has an explicit semantic action decision") {
        val tap = setOf(SemanticActionType.Tap)
        val adjust = setOf(SemanticActionType.Increase, SemanticActionType.Decrease)
        val expected = mapOf(
            SemanticRole.None to emptySet(),
            SemanticRole.Button to tap,
            SemanticRole.Link to tap,
            SemanticRole.Checkbox to tap,
            SemanticRole.SwitchControl to tap,
            SemanticRole.Slider to adjust,
            SemanticRole.TextField to emptySet(),
            SemanticRole.Text to emptySet(),
            SemanticRole.Image to emptySet(),
            SemanticRole.Group to emptySet(),
            SemanticRole.List to emptySet(),
            SemanticRole.ListItem to emptySet(),
            SemanticRole.Tab to tap,
            SemanticRole.TabList to emptySet(),
            SemanticRole.Dialog to emptySet(),
            SemanticRole.AlertDialog to emptySet(),
            SemanticRole.RadioGroup to emptySet(),
            SemanticRole.RadioButton to tap
        )
        val state = mapSemanticNodeState(traitFlags = 0, stateFlags = 0)

        expected.keys shouldBe SemanticRole.entries.toSet()
        expected.forEach { (semanticRole, semanticActions) ->
            withClue(semanticRole) {
                mapSemanticNodeActions(semanticRole, state).semanticActions shouldBe
                    semanticActions
            }
        }
    }

    test("Explicitly disabled state suppresses role actions") {
        val disabledState = mapSemanticNodeState(
            traitFlags = SemanticTrait.Enablable,
            stateFlags = SemanticState.Disabled
        )

        SemanticRole.entries.forEach { role ->
            withClue(role) {
                mapSemanticNodeActions(role, disabledState).semanticActions shouldBe emptySet()
            }
        }
    }

    test("Focus request capability follows focusable trait independently of disabled state") {
        val notFocusable = mapSemanticNodeState(traitFlags = 0, stateFlags = 0)
        val focusable = mapSemanticNodeState(
            traitFlags = SemanticTrait.Focusable,
            stateFlags = 0
        )
        val focusedAndDisabled = mapSemanticNodeState(
            traitFlags = SemanticTrait.Focusable or SemanticTrait.Enablable,
            stateFlags = SemanticState.Focused or SemanticState.Disabled
        )

        mapSemanticNodeActions(SemanticRole.Button, notFocusable)
            .canRequestFocus shouldBe false
        mapSemanticNodeActions(SemanticRole.Button, focusable)
            .canRequestFocus shouldBe true
        mapSemanticNodeActions(SemanticRole.Button, focusedAndDisabled)
            .canRequestFocus shouldBe true
    }

    test("Static text retains authored content without selecting toolkit properties") {
        val content = mapSemanticNodeContent(
            role = SemanticRole.Text,
            label = "Account balance",
            value = "Ten dollars",
            hint = "Updated today",
            state = mapSemanticNodeState(traitFlags = 0, stateFlags = 0)
        )

        content.kind shouldBe SemanticNodeContentKind.Text
        content.label shouldBe "Account balance"
        content.value shouldBe "Ten dollars"
        content.hint shouldBe "Updated today"
        content.isReadOnly shouldBe false
        content.isObscured shouldBe false
        content.isMultiline shouldBe false
    }

    test("Text field retains separate label value and hint") {
        val content = mapSemanticNodeContent(
            role = SemanticRole.TextField,
            label = "Email",
            value = "person@example.com",
            hint = "Work address",
            state = mapSemanticNodeState(traitFlags = 0, stateFlags = 0)
        )

        content.kind shouldBe SemanticNodeContentKind.TextField
        content.label shouldBe "Email"
        content.value shouldBe "person@example.com"
        content.hint shouldBe "Work address"
        content.isReadOnly shouldBe false
        content.isObscured shouldBe false
        content.isMultiline shouldBe false
    }

    test("Read-only state is classified only for text fields") {
        val state = mapSemanticNodeState(
            traitFlags = 0,
            stateFlags = SemanticState.ReadOnly
        )

        mapSemanticNodeContent(
            role = SemanticRole.TextField,
            label = "Identifier",
            value = "1234",
            hint = "",
            state = state
        ).isReadOnly shouldBe true
        mapSemanticNodeContent(
            role = SemanticRole.Button,
            label = "Submit",
            value = "Ready",
            hint = "",
            state = state
        ).isReadOnly shouldBe false
    }

    test("Multiline state is classified only for text fields") {
        val state = mapSemanticNodeState(
            traitFlags = 0,
            stateFlags = SemanticState.Multiline
        )

        mapSemanticNodeContent(
            role = SemanticRole.TextField,
            label = "Biography",
            value = "First line\nSecond line",
            hint = "",
            state = state
        ).isMultiline shouldBe true
        mapSemanticNodeContent(
            role = SemanticRole.Text,
            label = "First line\nSecond line",
            value = "",
            hint = "",
            state = state
        ).isMultiline shouldBe false
    }

    test("Obscured text field never retains its raw value") {
        val secret = "correct horse battery staple"
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

        content.kind shouldBe SemanticNodeContentKind.TextField
        content.label shouldBe "Password"
        content.value shouldBe null
        content.hint shouldBe "Required"
        content.isObscured shouldBe true
        content.toString().contains(secret) shouldBe false
    }

    test("Text-field-only obscured state does not redact other roles") {
        val value = "Visible button value"
        val content = mapSemanticNodeContent(
            role = SemanticRole.Button,
            label = "Submit",
            value = value,
            hint = "Activates form",
            state = mapSemanticNodeState(
                traitFlags = 0,
                stateFlags = SemanticState.Obscured
            )
        )

        content.kind shouldBe SemanticNodeContentKind.General
        content.value shouldBe value
        content.isObscured shouldBe false
    }
})

package app.rive.semantics

import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AndroidSemanticNodeMappingUnitTest : FunSpec({
    test("Every semantic role has an explicit Android class-name decision") {
        val expected = mapOf(
            SemanticRole.None to null,
            SemanticRole.Button to "android.widget.Button",
            SemanticRole.Link to null,
            SemanticRole.Checkbox to "android.widget.CheckBox",
            SemanticRole.SwitchControl to "android.widget.Switch",
            SemanticRole.Slider to "android.widget.SeekBar",
            SemanticRole.TextField to "android.widget.EditText",
            SemanticRole.Text to "android.widget.TextView",
            SemanticRole.Image to "android.widget.ImageView",
            SemanticRole.Group to null,
            SemanticRole.List to "android.widget.ListView",
            SemanticRole.ListItem to null,
            SemanticRole.Tab to null,
            SemanticRole.TabList to null,
            SemanticRole.Dialog to null,
            SemanticRole.AlertDialog to null,
            SemanticRole.RadioGroup to "android.widget.RadioGroup",
            SemanticRole.RadioButton to "android.widget.RadioButton"
        )

        expected.keys shouldBe SemanticRole.entries.toSet()
        expected.forEach { (semanticRole, androidClassName) ->
            withClue(semanticRole) {
                semanticRole.toAndroidAccessibilityClassName() shouldBe androidClassName
            }
        }
    }

    test("Positive authored levels map to Android heading status") {
        isAndroidAccessibilityHeading(-1) shouldBe false
        isAndroidAccessibilityHeading(0) shouldBe false
        isAndroidAccessibilityHeading(1) shouldBe true
        isAndroidAccessibilityHeading(6) shouldBe true
    }

    test("Expanded state maps applicable values and preserves absence") {
        mapSemanticNodeState(
            traitFlags = 0,
            stateFlags = SemanticState.Expanded
        ).toAndroidAccessibilityExpandedState() shouldBe null
        mapSemanticNodeState(
            traitFlags = SemanticTrait.Expandable,
            stateFlags = 0
        ).toAndroidAccessibilityExpandedState() shouldBe
            AccessibilityNodeInfo.EXPANDED_STATE_COLLAPSED
        mapSemanticNodeState(
            traitFlags = SemanticTrait.Expandable,
            stateFlags = SemanticState.Expanded
        ).toAndroidAccessibilityExpandedState() shouldBe
            AccessibilityNodeInfo.EXPANDED_STATE_FULL
    }

    test("Toggle state maps to Android checked state") {
        SemanticToggleState.Off.toAndroidAccessibilityCheckedState() shouldBe
            AccessibilityNodeInfo.CHECKED_STATE_FALSE
        SemanticToggleState.On.toAndroidAccessibilityCheckedState() shouldBe
            AccessibilityNodeInfo.CHECKED_STATE_TRUE
        SemanticToggleState.Mixed.toAndroidAccessibilityCheckedState() shouldBe
            AccessibilityNodeInfo.CHECKED_STATE_PARTIAL
    }

    test("Live-region state maps to Android live-region mode") {
        mapSemanticNodeState(
            traitFlags = 0,
            stateFlags = 0
        ).toAndroidAccessibilityLiveRegion() shouldBe
            View.ACCESSIBILITY_LIVE_REGION_NONE
        mapSemanticNodeState(
            traitFlags = 0,
            stateFlags = SemanticState.LiveRegion
        ).toAndroidAccessibilityLiveRegion() shouldBe
            View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    test("Every semantic action maps to its standard Android action ID") {
        val expected = mapOf(
            SemanticActionType.Tap to AccessibilityNodeInfoCompat.ACTION_CLICK,
            SemanticActionType.Increase to AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD,
            SemanticActionType.Decrease to AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD
        )

        expected.keys shouldBe SemanticActionType.entries.toSet()
        expected.forEach { (semanticAction, androidActionId) ->
            withClue(semanticAction) {
                semanticAction.toAndroidAccessibilityActionId() shouldBe androidActionId
            }
        }
    }
})

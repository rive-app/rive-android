package app.rive.semantics

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AndroidSemanticNodeBoundsUnitTest : FunSpec({
    test("Root bounds normalize reversed axes and remain host relative") {
        semanticNode(minX = 20.8f, minY = 40.2f, maxX = 10.2f, maxY = 30.8f)
            .toAndroidAccessibilityBounds(parent = null) shouldBe
            AndroidAccessibilityBounds(left = 10, top = 30, right = 21, bottom = 41)
    }

    test("Child bounds are relative to the normalized parent origin") {
        val parent = semanticNode(minX = 210f, minY = 180f, maxX = 10.8f, maxY = 20.1f)
        val child = semanticNode(minX = 40.2f, minY = 60f, maxX = 100.1f, maxY = 120.2f)

        child.toAndroidAccessibilityBounds(parent) shouldBe
            AndroidAccessibilityBounds(left = 30, top = 40, right = 91, bottom = 101)
    }

    test("Positive subpixel bounds retain nonzero integer area") {
        semanticNode(minX = 10.1f, minY = 20.1f, maxX = 10.2f, maxY = 20.2f)
            .toAndroidAccessibilityBounds(parent = null) shouldBe
            AndroidAccessibilityBounds(left = 10, top = 20, right = 11, bottom = 21)
    }
})

/** Creates a semantic node containing only geometry relevant to bounds mapping. */
private fun semanticNode(
    minX: Float,
    minY: Float,
    maxX: Float,
    maxY: Float,
): SemanticNodeData = SemanticNodeData(
    id = 0,
    parentId = -1,
    role = SemanticRole.None.value,
    label = "",
    value = "",
    hint = "",
    stateFlags = 0,
    traitFlags = 0,
    headingLevel = 0,
    minX = minX,
    minY = minY,
    maxX = maxX,
    maxY = maxY
)

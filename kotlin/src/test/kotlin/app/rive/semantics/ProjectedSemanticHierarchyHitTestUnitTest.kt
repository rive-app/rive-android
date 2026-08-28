package app.rive.semantics

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ProjectedSemanticHierarchyHitTestUnitTest : FunSpec({
    test("Deepest descendant wins over its containing parent") {
        val tree = semanticTreeOf(
            node(1, role = SemanticRole.Group, maxX = 100f, maxY = 100f),
            node(
                2,
                parentId = 1,
                role = SemanticRole.Button,
                label = "Child",
                minX = 20f,
                minY = 20f,
                maxX = 80f,
                maxY = 80f,
            ),
        )
        val hierarchy = ProjectedSemanticHierarchy.from(tree)

        hierarchy.hitTest(tree, 50f, 50f) shouldBe 2
        hierarchy.hitTest(tree, 10f, 10f) shouldBe 1
    }

    test("First authored sibling wins overlapping bounds") {
        val tree = semanticTreeOf(
            node(1, role = SemanticRole.Group, maxX = 100f, maxY = 100f),
            node(
                2,
                parentId = 1,
                siblingIndex = 0,
                role = SemanticRole.Button,
                label = "Behind",
                minX = 10f,
                minY = 10f,
                maxX = 70f,
                maxY = 70f,
            ),
            node(
                3,
                parentId = 1,
                siblingIndex = 1,
                role = SemanticRole.Button,
                label = "Front",
                minX = 30f,
                minY = 30f,
                maxX = 90f,
                maxY = 90f,
            ),
        )
        val hierarchy = ProjectedSemanticHierarchy.from(tree)

        hierarchy.hitTest(tree, 50f, 50f) shouldBe 2
        hierarchy.hitTest(tree, 20f, 20f) shouldBe 2
    }

    test("First root subtree wins overlap with a later root") {
        val tree = semanticTreeOf(
            node(1, siblingIndex = 0, role = SemanticRole.Group, maxX = 100f, maxY = 100f),
            node(
                2,
                parentId = 1,
                role = SemanticRole.Button,
                label = "Earlier descendant",
                maxX = 100f,
                maxY = 100f,
            ),
            node(
                3,
                siblingIndex = 1,
                role = SemanticRole.Button,
                label = "Later root",
                maxX = 100f,
                maxY = 100f,
            ),
        )
        val hierarchy = ProjectedSemanticHierarchy.from(tree)

        hierarchy.hitTest(tree, 50f, 50f) shouldBe 2
    }

    test("Descendant can be hit outside non-clipping parent bounds") {
        val tree = semanticTreeOf(
            node(
                1,
                role = SemanticRole.Group,
                minX = 10f,
                minY = 10f,
                maxX = 30f,
                maxY = 30f,
            ),
            node(
                2,
                parentId = 1,
                role = SemanticRole.Button,
                label = "Overflowing child",
                minX = 40f,
                minY = 40f,
                maxX = 60f,
                maxY = 60f,
            ),
        )
        val hierarchy = ProjectedSemanticHierarchy.from(tree)

        hierarchy.hitTest(tree, 50f, 50f) shouldBe 2
    }

    test("Reversed bounds are normalized and use half-open edges") {
        val tree = semanticTreeOf(
            node(
                1,
                role = SemanticRole.Button,
                label = "Reversed",
                minX = 30f,
                minY = 40f,
                maxX = 10f,
                maxY = 20f,
            ),
        )
        val hierarchy = ProjectedSemanticHierarchy.from(tree)

        hierarchy.hitTest(tree, 10f, 20f) shouldBe 1
        hierarchy.hitTest(tree, 29.999f, 39.999f) shouldBe 1
        hierarchy.hitTest(tree, 30f, 30f).shouldBeNull()
        hierarchy.hitTest(tree, 20f, 40f).shouldBeNull()
    }

    test("Pruned and absorbed nodes cannot be hit") {
        val tree = semanticTreeOf(
            node(
                1,
                siblingIndex = 0,
                role = SemanticRole.Group,
                stateFlags = SemanticState.Hidden,
                maxX = 100f,
                maxY = 100f,
            ),
            node(
                2,
                parentId = 1,
                role = SemanticRole.Button,
                label = "Hidden child",
                maxX = 100f,
                maxY = 100f,
            ),
            node(
                3,
                siblingIndex = 1,
                role = SemanticRole.Button,
                label = "Visible leaf",
                maxX = 100f,
                maxY = 100f,
            ),
            node(
                4,
                parentId = 3,
                role = SemanticRole.Text,
                label = "Absorbed child",
                maxX = 20f,
                maxY = 20f,
            ),
        )
        val hierarchy = ProjectedSemanticHierarchy.from(tree)

        hierarchy.hitTest(tree, 10f, 10f) shouldBe 3
    }

    test("Non-finite points and points outside every node miss") {
        val tree = semanticTreeOf(
            node(1, role = SemanticRole.Button, label = "Button", maxX = 10f, maxY = 10f),
        )
        val hierarchy = ProjectedSemanticHierarchy.from(tree)

        hierarchy.hitTest(tree, 11f, 5f).shouldBeNull()
        hierarchy.hitTest(tree, Float.NaN, 5f).shouldBeNull()
        hierarchy.hitTest(tree, 5f, Float.POSITIVE_INFINITY).shouldBeNull()
    }

    test("Stale hierarchy cannot return a node removed from the source tree") {
        val tree = semanticTreeOf(
            node(1, role = SemanticRole.Button, label = "Removed", maxX = 10f, maxY = 10f),
        )
        val hierarchy = ProjectedSemanticHierarchy.from(tree)
        tree.clear()

        hierarchy.hitTest(tree, 5f, 5f).shouldBeNull()
    }
})

/** Builds a semantic tree from one initial addition diff. */
private fun semanticTreeOf(vararg nodes: SemanticsDiffNode): SemanticTreeModel =
    SemanticTreeModel().apply {
        applyDiff(diff(added = arrayOf(*nodes)))
    }

/** Creates a semantic diff node with finite positive-area default bounds. */
private fun node(
    id: Int,
    parentId: Int = -1,
    siblingIndex: Int = 0,
    role: SemanticRole = SemanticRole.None,
    label: String = "",
    stateFlags: Int = 0,
    minX: Float = 0f,
    minY: Float = 0f,
    maxX: Float = 10f,
    maxY: Float = 10f,
): SemanticsDiffNode = SemanticsDiffNode(
    id = id,
    role = role.value,
    label = label,
    value = "",
    hint = "",
    stateFlags = stateFlags,
    traitFlags = 0,
    headingLevel = 0,
    minX = minX,
    minY = minY,
    maxX = maxX,
    maxY = maxY,
    parentId = parentId,
    siblingIndex = siblingIndex,
)

/** Creates a semantic diff containing only the supplied additions. */
private fun diff(added: Array<SemanticsDiffNode>): SemanticsDiff = SemanticsDiff(
    treeVersion = 1,
    frameNumber = 0,
    rootId = 0,
    removed = intArrayOf(),
    added = added,
    moved = emptyArray(),
    childrenUpdated = emptyArray(),
    updatedSemantic = emptyArray(),
    updatedGeometry = emptyArray(),
)

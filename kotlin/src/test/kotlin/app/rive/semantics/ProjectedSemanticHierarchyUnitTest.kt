package app.rive.semantics

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.assertFailsWith

class ProjectedSemanticHierarchyUnitTest : FunSpec({
    test("Projection promotes structural nodes and absorbs leaf descendants") {
        val hierarchy = hierarchyOf(
            node(1, role = SemanticRole.Group),
            node(2, parentId = 1, siblingIndex = 0),
            node(3, parentId = 2, role = SemanticRole.Button, label = "Play"),
            node(4, parentId = 3, role = SemanticRole.Text, label = "Duplicate"),
            node(5, parentId = 1, siblingIndex = 1, label = "Section"),
            node(6, parentId = 5, role = SemanticRole.Text, label = "Description"),
            node(7, parentId = 1, siblingIndex = 2, role = SemanticRole.Group),
            node(8, parentId = 7, role = SemanticRole.Text, label = "Footer"),
        )

        hierarchy.roots shouldContainExactly listOf(1)
        hierarchy.activeNodeIds shouldContainExactly listOf(1, 3, 5, 6, 7, 8)
        hierarchy.childrenOf(1) shouldContainExactly listOf(3, 5, 7)
        hierarchy.childrenOf(3) shouldBe emptyList()
        hierarchy.childrenOf(5) shouldContainExactly listOf(6)
        hierarchy.childrenOf(7) shouldContainExactly listOf(8)
        hierarchy.parentOf(3) shouldBe 1
        hierarchy.parentOf(6) shouldBe 5
        hierarchy.contains(2) shouldBe false
        hierarchy.contains(4) shouldBe false
        hierarchy.size shouldBe 6
    }

    test("Promoted roots retain dense authored order") {
        val hierarchy = hierarchyOf(
            node(1),
            node(2, parentId = 1, role = SemanticRole.Text, label = "First"),
            node(3, siblingIndex = 1, role = SemanticRole.Button, label = "Second"),
        )

        hierarchy.roots shouldContainExactly listOf(2, 3)
        hierarchy.activeNodeIds shouldContainExactly listOf(2, 3)
        hierarchy.parentOf(2) shouldBe null
        hierarchy.parentOf(3) shouldBe null
        hierarchy.contains(2) shouldBe true
        hierarchy.childrenOf(1) shouldBe emptyList()
    }

    test("Rebuilding reflects structural export changes from semantic updates") {
        val tree = semanticTreeOf(
            node(1, role = SemanticRole.Group),
            node(2, parentId = 1),
            node(3, parentId = 2, role = SemanticRole.Button, label = "Action"),
            node(4, parentId = 1, siblingIndex = 1, role = SemanticRole.Text, label = "Footer"),
        )

        val before = ProjectedSemanticHierarchy.from(tree)
        before.activeNodeIds shouldContainExactly listOf(1, 3, 4)
        before.childrenOf(1) shouldContainExactly listOf(3, 4)

        tree.applyDiff(
            diff(
                updatedSemantic = arrayOf(node(2, label = "Section"))
            )
        )
        val after = ProjectedSemanticHierarchy.from(tree)

        after.activeNodeIds shouldContainExactly listOf(1, 2, 3, 4)
        after.childrenOf(1) shouldContainExactly listOf(2, 4)
        after.childrenOf(2) shouldContainExactly listOf(3)
        after.parentOf(3) shouldBe 2
    }

    test("Zero-area and non-finite nodes promote valid descendants") {
        val hierarchy = hierarchyOf(
            node(1, role = SemanticRole.Group, maxX = 0f, maxY = 10f),
            node(2, parentId = 1, role = SemanticRole.Button, label = "Zero parent"),
            node(
                3,
                siblingIndex = 1,
                role = SemanticRole.Group,
                minX = Float.NaN,
            ),
            node(4, parentId = 3, role = SemanticRole.Button, label = "NaN parent"),
        )

        hierarchy.roots shouldContainExactly listOf(2, 4)
        hierarchy.activeNodeIds shouldContainExactly listOf(2, 4)
    }

    test("Hidden nodes defensively prune their complete subtree") {
        val hierarchy = hierarchyOf(
            node(
                1,
                role = SemanticRole.Group,
                stateFlags = SemanticState.Hidden,
            ),
            node(2, parentId = 1, role = SemanticRole.Button, label = "Hidden child"),
            node(3, siblingIndex = 1, role = SemanticRole.Text, label = "Visible sibling"),
        )

        hierarchy.roots shouldContainExactly listOf(3)
        hierarchy.activeNodeIds shouldContainExactly listOf(3)
        hierarchy.contains(1) shouldBe false
        hierarchy.contains(2) shouldBe false
    }

    test("Active modal restricts projection to its exported subtree") {
        val hierarchy = hierarchyOf(
            node(1, role = SemanticRole.Group),
            node(2, parentId = 1, role = SemanticRole.Button, label = "Background"),
            node(
                3,
                parentId = 1,
                siblingIndex = 1,
                role = SemanticRole.Dialog,
                label = "Confirmation",
                stateFlags = SemanticState.Modal,
            ),
            node(4, parentId = 3, role = SemanticRole.Text, label = "Delete item?"),
            node(
                5,
                parentId = 3,
                siblingIndex = 1,
                role = SemanticRole.Button,
                label = "Delete",
            ),
            node(
                6,
                parentId = 1,
                siblingIndex = 2,
                role = SemanticRole.Button,
                label = "Other background",
            ),
        )

        hierarchy.activeModalNodeId shouldBe 3
        hierarchy.hasDisjointActiveModals shouldBe false
        hierarchy.roots shouldContainExactly listOf(3)
        hierarchy.activeNodeIds shouldContainExactly listOf(3, 4, 5)
        hierarchy.parentOf(3) shouldBe null
        hierarchy.childrenOf(3) shouldContainExactly listOf(4, 5)
        hierarchy.contains(2) shouldBe false
        hierarchy.contains(6) shouldBe false
    }

    test("Non-modal dialog and stray modal state preserve ordinary projection") {
        val hierarchy = hierarchyOf(
            node(1, role = SemanticRole.Dialog, label = "Modeless"),
            node(2, parentId = 1, role = SemanticRole.Text, label = "Dialog content"),
            node(
                3,
                siblingIndex = 1,
                role = SemanticRole.Group,
                label = "Not a dialog",
                stateFlags = SemanticState.Modal,
            ),
            node(4, parentId = 3, role = SemanticRole.Text, label = "Group content"),
        )

        hierarchy.activeModalNodeId shouldBe null
        hierarchy.hasDisjointActiveModals shouldBe false
        hierarchy.roots shouldContainExactly listOf(1, 3)
        hierarchy.activeNodeIds shouldContainExactly listOf(1, 2, 3, 4)
    }

    test("Deepest nested modal becomes the active modal root") {
        val hierarchy = hierarchyOf(
            node(
                1,
                role = SemanticRole.Dialog,
                label = "Outer",
                stateFlags = SemanticState.Modal,
            ),
            node(2, parentId = 1, role = SemanticRole.Text, label = "Outer content"),
            node(
                3,
                parentId = 1,
                siblingIndex = 1,
                role = SemanticRole.AlertDialog,
                label = "Inner",
                stateFlags = SemanticState.Modal,
            ),
            node(4, parentId = 3, role = SemanticRole.Button, label = "Acknowledge"),
        )

        hierarchy.activeModalNodeId shouldBe 3
        hierarchy.hasDisjointActiveModals shouldBe false
        hierarchy.roots shouldContainExactly listOf(3)
        hierarchy.activeNodeIds shouldContainExactly listOf(3, 4)
    }

    test("Disjoint modals choose the first deepest authored candidate") {
        val hierarchy = hierarchyOf(
            node(1, role = SemanticRole.Group),
            node(
                2,
                parentId = 1,
                role = SemanticRole.Dialog,
                label = "First",
                stateFlags = SemanticState.Modal,
            ),
            node(3, parentId = 2, role = SemanticRole.Text, label = "First content"),
            node(
                4,
                parentId = 1,
                siblingIndex = 1,
                role = SemanticRole.AlertDialog,
                label = "Second",
                stateFlags = SemanticState.Modal,
            ),
            node(5, parentId = 4, role = SemanticRole.Text, label = "Second content"),
        )

        hierarchy.activeModalNodeId shouldBe 2
        hierarchy.hasDisjointActiveModals shouldBe true
        hierarchy.roots shouldContainExactly listOf(2)
        hierarchy.activeNodeIds shouldContainExactly listOf(2, 3)
    }

    test("Hidden and zero-area modal candidates do not restrict projection") {
        val hierarchy = hierarchyOf(
            node(
                1,
                role = SemanticRole.Dialog,
                label = "Hidden",
                stateFlags = SemanticState.Modal or SemanticState.Hidden,
            ),
            node(2, parentId = 1, role = SemanticRole.Text, label = "Hidden content"),
            node(
                3,
                siblingIndex = 1,
                role = SemanticRole.Dialog,
                label = "Zero area",
                stateFlags = SemanticState.Modal,
                maxX = 0f,
            ),
            node(4, parentId = 3, role = SemanticRole.Text, label = "Promoted content"),
        )

        hierarchy.activeModalNodeId shouldBe null
        hierarchy.roots shouldContainExactly listOf(4)
        hierarchy.activeNodeIds shouldContainExactly listOf(4)
    }

    test("Hierarchy collections are unmodifiable") {
        val hierarchy = hierarchyOf(
            node(1, role = SemanticRole.Group),
            node(2, parentId = 1, role = SemanticRole.Text, label = "Child"),
        )

        assertFailsWith<UnsupportedOperationException> {
            (hierarchy.roots as MutableList<Int>).add(99)
        }
        assertFailsWith<UnsupportedOperationException> {
            (hierarchy.activeNodeIds as MutableList<Int>).add(99)
        }
        assertFailsWith<UnsupportedOperationException> {
            (hierarchy.childrenOf(1) as MutableList<Int>).add(99)
        }
    }

    test("Malformed cycles stop at their first traversal occurrence") {
        val tree = semanticTreeOf(
            node(1, role = SemanticRole.Group),
            node(2, parentId = 1, role = SemanticRole.Group),
        )
        tree.applyDiff(
            diff(
                childrenUpdated = arrayOf(
                    SemanticsChildrenUpdate(parentId = -1, childIds = intArrayOf(1)),
                    SemanticsChildrenUpdate(parentId = 1, childIds = intArrayOf(2)),
                    SemanticsChildrenUpdate(parentId = 2, childIds = intArrayOf(1)),
                )
            )
        )

        val hierarchy = ProjectedSemanticHierarchy.from(tree)

        hierarchy.roots shouldContainExactly listOf(1)
        hierarchy.activeNodeIds shouldContainExactly listOf(1, 2)
        hierarchy.childrenOf(1) shouldContainExactly listOf(2)
        hierarchy.childrenOf(2) shouldBe emptyList()
    }
})

/** Builds and projects a semantic tree from [nodes]. */
private fun hierarchyOf(vararg nodes: SemanticsDiffNode): ProjectedSemanticHierarchy =
    ProjectedSemanticHierarchy.from(semanticTreeOf(*nodes))

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

/** Creates a semantic diff containing only the supplied projection-relevant operations. */
private fun diff(
    added: Array<SemanticsDiffNode> = emptyArray(),
    childrenUpdated: Array<SemanticsChildrenUpdate> = emptyArray(),
    updatedSemantic: Array<SemanticsDiffNode> = emptyArray(),
): SemanticsDiff = SemanticsDiff(
    treeVersion = 1,
    frameNumber = 0,
    rootId = 0,
    removed = intArrayOf(),
    added = added,
    moved = emptyArray(),
    childrenUpdated = childrenUpdated,
    updatedSemantic = updatedSemantic,
    updatedGeometry = emptyArray(),
)

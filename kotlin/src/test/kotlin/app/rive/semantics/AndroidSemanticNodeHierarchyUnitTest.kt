package app.rive.semantics

import android.view.View
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify

class AndroidSemanticNodeHierarchyUnitTest : FunSpec({
    val host = mockk<View>(relaxed = true)
    val nodeInfo = mockk<AccessibilityNodeInfoCompat>(relaxed = true)

    beforeTest {
        clearMocks(host, nodeInfo)
    }

    test("Projected parent and children bind through stable virtual IDs") {
        val hierarchy = androidHierarchyOf(
            androidHierarchyNode(1, role = SemanticRole.Group),
            androidHierarchyNode(2, parentId = 1, role = SemanticRole.Group),
            androidHierarchyNode(
                3,
                parentId = 2,
                role = SemanticRole.Button,
                label = "Action"
            ),
        )
        val registry = AndroidVirtualNodeIdRegistry().apply { reconcile(hierarchy) }
        val parentVirtualId = requireNotNull(registry.virtualIdForRiveNode(1))
        val childVirtualId = requireNotNull(registry.virtualIdForRiveNode(3))

        nodeInfo.applySemanticNodeHierarchy(
            host = host,
            riveNodeId = 2,
            hierarchy = hierarchy,
            registry = registry,
        ) shouldBe true

        verify(exactly = 1) { nodeInfo.setParent(host, parentVirtualId) }
        verify(exactly = 1) { nodeInfo.addChild(host, childVirtualId) }
    }

    test("Projected roots retain the host parent and publish virtual children") {
        val hierarchy = androidHierarchyOf(
            androidHierarchyNode(1, role = SemanticRole.Group),
            androidHierarchyNode(
                2,
                parentId = 1,
                role = SemanticRole.Button,
                label = "Action"
            ),
        )
        val registry = AndroidVirtualNodeIdRegistry().apply { reconcile(hierarchy) }
        val childVirtualId = requireNotNull(registry.virtualIdForRiveNode(2))

        nodeInfo.applySemanticNodeHierarchy(
            host = host,
            riveNodeId = 1,
            hierarchy = hierarchy,
            registry = registry,
        ) shouldBe true

        verify(exactly = 0) { nodeInfo.setParent(host, any<Int>()) }
        verify(exactly = 1) { nodeInfo.addChild(host, childVirtualId) }
    }

    test("Stale requested nodes are rejected without mutating the query result") {
        val hierarchy = androidHierarchyOf(
            androidHierarchyNode(1, role = SemanticRole.Button, label = "Action")
        )
        val registry = AndroidVirtualNodeIdRegistry()

        nodeInfo.applySemanticNodeHierarchy(
            host = host,
            riveNodeId = 1,
            hierarchy = hierarchy,
            registry = registry,
        ) shouldBe false

        verify(exactly = 0) { nodeInfo.setParent(host, any<Int>()) }
        verify(exactly = 0) { nodeInfo.addChild(host, any<Int>()) }
    }

    test("Inconsistent relationships are rejected before partial hierarchy mutation") {
        val hierarchy = androidHierarchyOf(
            androidHierarchyNode(1, role = SemanticRole.Group),
            androidHierarchyNode(
                2,
                parentId = 1,
                role = SemanticRole.Button,
                label = "Action"
            ),
        )
        val registry = AndroidVirtualNodeIdRegistry().apply {
            reconcile(hierarchy)
            reconcile(listOf(1))
        }

        nodeInfo.applySemanticNodeHierarchy(
            host = host,
            riveNodeId = 1,
            hierarchy = hierarchy,
            registry = registry,
        ) shouldBe false

        verify(exactly = 0) { nodeInfo.setParent(host, any<Int>()) }
        verify(exactly = 0) { nodeInfo.addChild(host, any<Int>()) }
    }
})

/** Builds a projected hierarchy for direct Android relationship binding tests. */
private fun androidHierarchyOf(vararg nodes: SemanticsDiffNode): ProjectedSemanticHierarchy {
    val tree = SemanticTreeModel()
    tree.applyDiff(
        SemanticsDiff(
            treeVersion = 1,
            frameNumber = 0,
            rootId = 0,
            removed = intArrayOf(),
            added = arrayOf(*nodes),
            moved = emptyArray(),
            childrenUpdated = emptyArray(),
            updatedSemantic = emptyArray(),
            updatedGeometry = emptyArray(),
        )
    )
    return ProjectedSemanticHierarchy.from(tree)
}

/** Creates a finite positive-area semantic node for Android relationship binding tests. */
private fun androidHierarchyNode(
    id: Int,
    parentId: Int = -1,
    role: SemanticRole,
    label: String = "",
): SemanticsDiffNode = SemanticsDiffNode(
    id = id,
    role = role.value,
    label = label,
    value = "",
    hint = "",
    stateFlags = 0,
    traitFlags = 0,
    headingLevel = 0,
    minX = 0f,
    minY = 0f,
    maxX = 10f,
    maxY = 10f,
    parentId = parentId,
    siblingIndex = 0,
)

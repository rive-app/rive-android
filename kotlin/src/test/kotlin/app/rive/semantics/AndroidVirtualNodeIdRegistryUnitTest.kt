package app.rive.semantics

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe

class AndroidVirtualNodeIdRegistryUnitTest : FunSpec({
    test("Separate registries sharing an allocator cannot observe each other's mappings") {
        val allocator = AndroidVirtualNodeIdAllocator()
        val retiredRegistry = AndroidVirtualNodeIdRegistry(allocator)
        retiredRegistry.reconcile(listOf(10, 20))
        val retiredIds = listOf(10, 20).map { riveId ->
            requireNotNull(retiredRegistry.virtualIdForRiveNode(riveId))
        }
        retiredRegistry.clear()

        val successorRegistry = AndroidVirtualNodeIdRegistry(allocator)
        successorRegistry.reconcile(listOf(30))
        val successorId = requireNotNull(successorRegistry.virtualIdForRiveNode(30))

        (successorId in retiredIds) shouldBe false
        retiredRegistry.riveNodeIdForVirtualNode(successorId) shouldBe null
        successorRegistry.riveNodeIdForVirtualNode(successorId) shouldBe 30
    }

    test("Arbitrary Rive IDs map bidirectionally to nonnegative Android IDs") {
        val registry = AndroidVirtualNodeIdRegistry()
        val riveNodeIds = listOf(-1, Int.MIN_VALUE, 0, Int.MAX_VALUE)

        registry.reconcile(riveNodeIds)

        registry.size shouldBe riveNodeIds.size
        val virtualNodeIds = riveNodeIds.map { riveNodeId ->
            val virtualNodeId = requireNotNull(registry.virtualIdForRiveNode(riveNodeId))
            virtualNodeId shouldBeGreaterThanOrEqual 0
            registry.riveNodeIdForVirtualNode(virtualNodeId) shouldBe riveNodeId
            virtualNodeId
        }
        virtualNodeIds.distinct().size shouldBe riveNodeIds.size
    }

    test("Reconciliation preserves IDs across reorder and duplicate input") {
        val registry = AndroidVirtualNodeIdRegistry()
        registry.reconcile(listOf(10, 20, 30))
        val originalIds = listOf(10, 20, 30).associateWith(registry::virtualIdForRiveNode)

        registry.reconcile(listOf(30, 10, 20, 10))

        registry.size shouldBe 3
        listOf(10, 20, 30).map(registry::virtualIdForRiveNode) shouldContainExactly
            listOf(originalIds[10], originalIds[20], originalIds[30])
    }

    test("Removed IDs are retired and stale reverse lookups are rejected") {
        val registry = AndroidVirtualNodeIdRegistry()
        registry.reconcile(listOf(10, 20))
        val removedVirtualId = requireNotNull(registry.virtualIdForRiveNode(10))
        val retainedVirtualId = requireNotNull(registry.virtualIdForRiveNode(20))

        registry.reconcile(listOf(20))

        registry.virtualIdForRiveNode(10) shouldBe null
        registry.riveNodeIdForVirtualNode(removedVirtualId) shouldBe null
        registry.virtualIdForRiveNode(20) shouldBe retainedVirtualId
    }

    test("Re-added Rive nodes receive new virtual IDs") {
        val registry = AndroidVirtualNodeIdRegistry()
        registry.reconcile(listOf(10))
        val originalVirtualId = requireNotNull(registry.virtualIdForRiveNode(10))

        registry.reconcile(emptyList())
        registry.reconcile(listOf(10))

        val replacementVirtualId = requireNotNull(registry.virtualIdForRiveNode(10))
        replacementVirtualId shouldBeGreaterThanOrEqual 0
        (replacementVirtualId == originalVirtualId) shouldBe false
        registry.riveNodeIdForVirtualNode(originalVirtualId) shouldBe null
        registry.riveNodeIdForVirtualNode(replacementVirtualId) shouldBe 10
    }

    test("Clear retires all active mappings without resetting allocation") {
        val registry = AndroidVirtualNodeIdRegistry()
        registry.reconcile(listOf(10, 20))
        val retiredIds = listOf(10, 20).map { requireNotNull(registry.virtualIdForRiveNode(it)) }

        registry.clear()

        registry.size shouldBe 0
        retiredIds.forEach { registry.riveNodeIdForVirtualNode(it) shouldBe null }

        registry.reconcile(listOf(10))
        val replacementId = requireNotNull(registry.virtualIdForRiveNode(10))
        (replacementId in retiredIds) shouldBe false
    }

    test("Hierarchy reconciliation preserves IDs through reorder and reparent") {
        val tree = registrySemanticTreeOf(
            registryNode(1, role = SemanticRole.Group),
            registryNode(2, siblingIndex = 1, role = SemanticRole.Group),
            registryNode(3, parentId = 1, role = SemanticRole.Button, label = "First"),
            registryNode(
                4,
                parentId = 1,
                siblingIndex = 1,
                role = SemanticRole.Button,
                label = "Second"
            ),
        )
        val registry = AndroidVirtualNodeIdRegistry()
        val initialHierarchy = ProjectedSemanticHierarchy.from(tree)
        registry.reconcile(initialHierarchy)
        val initialVirtualIds = initialHierarchy.activeNodeIds.associateWith { nodeId ->
            requireNotNull(registry.virtualIdForRiveNode(nodeId))
        }

        tree.applyDiff(
            registryDiff(
                moved = arrayOf(
                    registryNode(
                        3,
                        parentId = 2,
                        role = SemanticRole.Button,
                        label = "First"
                    )
                ),
                childrenUpdated = arrayOf(
                    SemanticsChildrenUpdate(parentId = 1, childIds = intArrayOf(4)),
                    SemanticsChildrenUpdate(parentId = 2, childIds = intArrayOf(3)),
                )
            )
        )
        val updatedHierarchy = ProjectedSemanticHierarchy.from(tree)
        registry.reconcile(updatedHierarchy)

        updatedHierarchy.childrenOf(1) shouldContainExactly listOf(4)
        updatedHierarchy.childrenOf(2) shouldContainExactly listOf(3)
        updatedHierarchy.activeNodeIds.forEach { nodeId ->
            registry.virtualIdForRiveNode(nodeId) shouldBe initialVirtualIds[nodeId]
        }
    }

    test("Hierarchy reconciliation allocates promoted containers and retires absorbed nodes") {
        val tree = registrySemanticTreeOf(
            registryNode(1, role = SemanticRole.Group),
            registryNode(2, parentId = 1),
            registryNode(3, parentId = 2, role = SemanticRole.Button, label = "Action"),
        )
        val registry = AndroidVirtualNodeIdRegistry()
        registry.reconcile(ProjectedSemanticHierarchy.from(tree))
        val rootVirtualId = requireNotNull(registry.virtualIdForRiveNode(1))
        val actionVirtualId = requireNotNull(registry.virtualIdForRiveNode(3))
        registry.virtualIdForRiveNode(2) shouldBe null

        tree.applyDiff(
            registryDiff(
                updatedSemantic = arrayOf(registryNode(2, label = "Section"))
            )
        )
        registry.reconcile(ProjectedSemanticHierarchy.from(tree))
        val sectionVirtualId = requireNotNull(registry.virtualIdForRiveNode(2))
        registry.virtualIdForRiveNode(1) shouldBe rootVirtualId
        registry.virtualIdForRiveNode(3) shouldBe actionVirtualId

        tree.applyDiff(
            registryDiff(
                updatedSemantic = arrayOf(
                    registryNode(1, role = SemanticRole.Button, label = "Root action")
                )
            )
        )
        registry.reconcile(ProjectedSemanticHierarchy.from(tree))

        registry.size shouldBe 1
        registry.virtualIdForRiveNode(1) shouldBe rootVirtualId
        registry.virtualIdForRiveNode(2) shouldBe null
        registry.virtualIdForRiveNode(3) shouldBe null
        registry.riveNodeIdForVirtualNode(sectionVirtualId) shouldBe null
        registry.riveNodeIdForVirtualNode(actionVirtualId) shouldBe null
    }
})

/** Builds a semantic tree for virtual-ID hierarchy integration tests. */
private fun registrySemanticTreeOf(vararg nodes: SemanticsDiffNode): SemanticTreeModel =
    SemanticTreeModel().apply {
        applyDiff(registryDiff(added = arrayOf(*nodes)))
    }

/** Creates a semantic node with finite positive-area bounds for registry tests. */
private fun registryNode(
    id: Int,
    parentId: Int = -1,
    siblingIndex: Int = 0,
    role: SemanticRole = SemanticRole.None,
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
    siblingIndex = siblingIndex,
)

/** Creates a semantic diff containing operations needed by registry integration tests. */
private fun registryDiff(
    added: Array<SemanticsDiffNode> = emptyArray(),
    moved: Array<SemanticsDiffNode> = emptyArray(),
    childrenUpdated: Array<SemanticsChildrenUpdate> = emptyArray(),
    updatedSemantic: Array<SemanticsDiffNode> = emptyArray(),
): SemanticsDiff = SemanticsDiff(
    treeVersion = 1,
    frameNumber = 0,
    rootId = 0,
    removed = intArrayOf(),
    added = added,
    moved = moved,
    childrenUpdated = childrenUpdated,
    updatedSemantic = updatedSemantic,
    updatedGeometry = emptyArray(),
)

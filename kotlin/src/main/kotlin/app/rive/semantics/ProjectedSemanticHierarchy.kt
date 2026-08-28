package app.rive.semantics

import androidx.annotation.MainThread
import app.rive.RiveLog
import java.util.Collections

/**
 * Immutable exported topology derived from a [SemanticTreeModel].
 *
 * This hierarchy stores only Rive node IDs and relationships. Semantic payload remains in the
 * source tree and is mapped directly into the target accessibility toolkit when requested.
 * Promotion, pruning, and absorption may make these relationships differ from the authored tree.
 */
@MainThread
internal class ProjectedSemanticHierarchy private constructor(
    /** Root Rive node IDs in exported traversal order. */
    val roots: List<Int>,
    /** Every exported Rive node ID in depth-first traversal order. */
    val activeNodeIds: List<Int>,
    /** Active modal Rive node ID, or `null` when the ordinary hierarchy is exported. */
    val activeModalNodeId: Int?,
    /** Whether active disjoint modal candidates required deterministic conflict resolution. */
    val hasDisjointActiveModals: Boolean,
    private val parentByNodeId: Map<Int, Int?>,
    private val childrenByNodeId: Map<Int, List<Int>>,
) {
    /** Number of nodes in the exported hierarchy. */
    val size: Int get() = activeNodeIds.size

    /**
     * Returns whether [nodeId] is currently exported.
     *
     * @param nodeId Rive semantic node ID.
     * @return `true` when the projected hierarchy contains the node.
     */
    fun contains(nodeId: Int): Boolean = parentByNodeId.containsKey(nodeId)

    /**
     * Returns the exported parent of [nodeId].
     *
     * A `null` result represents either a projected root or an unknown node. Use [contains] when
     * those cases must be distinguished.
     *
     * @param nodeId Rive semantic node ID.
     * @return Parent Rive node ID, or `null` for a root or unknown node.
     */
    fun parentOf(nodeId: Int): Int? = parentByNodeId[nodeId]

    /**
     * Returns the exported children of [nodeId] in traversal order.
     *
     * @param nodeId Rive semantic node ID.
     * @return Unmodifiable child ID list, or an empty list for leaves and unknown nodes.
     */
    fun childrenOf(nodeId: Int): List<Int> = childrenByNodeId[nodeId].orEmpty()

    companion object {
        /**
         * Projects the current version of [tree] into an exported accessibility hierarchy.
         *
         * @param tree Main-thread-confined semantic tree to project.
         * @return Immutable ID-only hierarchy for the tree's current version.
         */
        fun from(tree: SemanticTreeModel): ProjectedSemanticHierarchy {
            val projection = ProjectedSemanticHierarchyBuilder(tree).build()
            return ProjectedSemanticHierarchy(
                roots = projection.roots,
                activeNodeIds = projection.activeNodeIds,
                activeModalNodeId = projection.activeModalNodeId,
                hasDisjointActiveModals = projection.hasDisjointActiveModals,
                parentByNodeId = projection.parentByNodeId,
                childrenByNodeId = projection.childrenByNodeId,
            )
        }
    }
}

/** Private construction data returned to [ProjectedSemanticHierarchy]'s companion factory. */
private data class ProjectedSemanticHierarchyData(
    val roots: List<Int>,
    val activeNodeIds: List<Int>,
    val activeModalNodeId: Int?,
    val hasDisjointActiveModals: Boolean,
    val parentByNodeId: Map<Int, Int?>,
    val childrenByNodeId: Map<Int, List<Int>>,
)

/** One reachable modal candidate and its authored ancestors. */
private data class ModalCandidate(
    val nodeId: Int,
    val depth: Int,
    val ancestorNodeIds: Set<Int>,
)

/** Deterministic active-modal selection for one tree version. */
private data class ModalSelection(
    val nodeId: Int?,
    val hasDisjointCandidates: Boolean,
)

/** Builds one immutable projected hierarchy from the current semantic tree version. */
@MainThread
private class ProjectedSemanticHierarchyBuilder(
    private val tree: SemanticTreeModel,
) {
    private val activeNodeIds = mutableListOf<Int>()
    private val parentByNodeId = linkedMapOf<Int, Int?>()
    private val childrenByNodeId = mutableMapOf<Int, List<Int>>()
    private val visitedNodeIds = mutableSetOf<Int>()

    /** Returns a complete hierarchy rooted at the source tree's authoritative root order. */
    fun build(): ProjectedSemanticHierarchyData {
        val modalSelection = findActiveModal()
        if (modalSelection.hasDisjointCandidates) {
            RiveLog.w(TAG) {
                "Multiple disjoint Rive semantic modals are active; " +
                    "using authored modal ${modalSelection.nodeId}."
            }
        }

        val roots = mutableListOf<Int>()
        val activeModalNodeId = modalSelection.nodeId
        if (activeModalNodeId != null) {
            projectNode(activeModalNodeId, projectedParentId = null, into = roots)
        } else {
            for (rootId in tree.roots) {
                projectNode(rootId, projectedParentId = null, into = roots)
            }
        }

        return ProjectedSemanticHierarchyData(
            roots = roots.toUnmodifiableList(),
            activeNodeIds = activeNodeIds.toUnmodifiableList(),
            activeModalNodeId = activeModalNodeId,
            hasDisjointActiveModals = modalSelection.hasDisjointCandidates,
            parentByNodeId = parentByNodeId.toMap(),
            childrenByNodeId = childrenByNodeId.toMap(),
        )
    }

    /**
     * Selects the deepest reachable active modal in authored depth-first order.
     *
     * Hidden subtrees and absorbed leaf descendants do not contribute candidates. Zero-area
     * containers remain traversable but cannot themselves become the active exported modal.
     * Nested candidates select the innermost modal. Disjoint candidates are reported so the caller
     * can warn while retaining deterministic selection.
     *
     * @return Active modal selection for the source tree's current version.
     */
    private fun findActiveModal(): ModalSelection {
        val candidates = mutableListOf<ModalCandidate>()
        val visited = mutableSetOf<Int>()

        /** Visits one reachable authored node while preserving its ancestor chain. */
        fun visit(nodeId: Int, depth: Int, ancestorNodeIds: Set<Int>) {
            if (!visited.add(nodeId)) {
                return
            }
            val node = tree.nodeById(nodeId) ?: return
            val role = SemanticRole.fromValue(node.role)
            val state = mapSemanticNodeState(node.traitFlags, node.stateFlags)
            val content = mapSemanticNodeContent(
                role = role,
                label = node.label,
                value = node.value,
                hint = node.hint,
                state = state,
            )
            val projection = classifySemanticNodeProjection(
                role = role,
                content = content,
                state = state,
                minX = node.minX,
                minY = node.minY,
                maxX = node.maxX,
                maxY = node.maxY,
            )
            if (projection == SemanticNodeProjection.PruneSubtree) {
                return
            }
            if (projection == SemanticNodeProjection.ExportContainer && role.isActiveModal(state)) {
                candidates += ModalCandidate(nodeId, depth, ancestorNodeIds)
            }
            if (projection == SemanticNodeProjection.ExportLeaf) {
                return
            }

            val childAncestors = ancestorNodeIds + nodeId
            for (childId in node.children) {
                visit(childId, depth + 1, childAncestors)
            }
        }

        for (rootId in tree.roots) {
            visit(rootId, depth = 0, ancestorNodeIds = emptySet())
        }
        if (candidates.isEmpty()) {
            return ModalSelection(nodeId = null, hasDisjointCandidates = false)
        }

        var selected = candidates.first()
        for (candidate in candidates.drop(1)) {
            if (candidate.depth > selected.depth) {
                selected = candidate
            }
        }
        val hasDisjointCandidates = candidates.any { candidate ->
            candidate.nodeId != selected.nodeId &&
                candidate.nodeId !in selected.ancestorNodeIds &&
                selected.nodeId !in candidate.ancestorNodeIds
        }
        return ModalSelection(selected.nodeId, hasDisjointCandidates)
    }

    /**
     * Projects one source node into [into], flattening promoted descendants into the same list.
     *
     * Globally tracking visited IDs makes malformed duplicate references deterministic and prevents
     * cycles from recursing indefinitely. The first authored traversal occurrence wins.
     *
     * @param nodeId Source Rive node ID to visit.
     * @param projectedParentId Exported parent ID, or `null` at the projected root level.
     * @param into Ordered projected sibling list that receives exported nodes.
     */
    private fun projectNode(
        nodeId: Int,
        projectedParentId: Int?,
        into: MutableList<Int>,
    ) {
        if (!visitedNodeIds.add(nodeId)) {
            return
        }
        val node = tree.nodeById(nodeId) ?: return
        val role = SemanticRole.fromValue(node.role)
        val state = mapSemanticNodeState(node.traitFlags, node.stateFlags)
        val content = mapSemanticNodeContent(
            role = role,
            label = node.label,
            value = node.value,
            hint = node.hint,
            state = state,
        )

        when (
            classifySemanticNodeProjection(
                role = role,
                content = content,
                state = state,
                minX = node.minX,
                minY = node.minY,
                maxX = node.maxX,
                maxY = node.maxY,
            )
        ) {
            SemanticNodeProjection.PruneSubtree -> Unit
            SemanticNodeProjection.PromoteChildren -> {
                for (childId in node.children) {
                    projectNode(childId, projectedParentId, into)
                }
            }
            SemanticNodeProjection.ExportContainer -> {
                exportNode(nodeId, projectedParentId, into)
                val projectedChildren = mutableListOf<Int>()
                for (childId in node.children) {
                    projectNode(childId, nodeId, projectedChildren)
                }
                childrenByNodeId[nodeId] = projectedChildren.toUnmodifiableList()
            }
            SemanticNodeProjection.ExportLeaf -> exportNode(nodeId, projectedParentId, into)
        }
    }

    /**
     * Records one exported node in depth-first order and attaches it to [into].
     *
     * @param nodeId Rive node ID to export.
     * @param projectedParentId Exported parent ID, or `null` for a projected root.
     * @param into Ordered projected sibling list that receives the node.
     */
    private fun exportNode(
        nodeId: Int,
        projectedParentId: Int?,
        into: MutableList<Int>,
    ) {
        into += nodeId
        activeNodeIds += nodeId
        parentByNodeId[nodeId] = projectedParentId
    }

    private companion object {
        const val TAG = "Rive/Semantics"
    }
}

/**
 * Returns an unmodifiable ordered copy of this ID collection.
 *
 * @return Independent unmodifiable list preserving iteration order.
 */
private fun Collection<Int>.toUnmodifiableList(): List<Int> =
    if (isEmpty()) emptyList() else Collections.unmodifiableList(ArrayList(this))

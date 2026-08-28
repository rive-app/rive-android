package app.rive.semantics

import androidx.annotation.MainThread

/**
 * Maintains stable IDs between Rive semantic nodes and Android virtual accessibility nodes.
 *
 * Rive node IDs cannot be used directly because their full signed-bit range overlaps Android's
 * reserved host and invalid virtual IDs. This registry allocates nonnegative Android IDs and never
 * reuses an ID during its lifetime, allowing stale framework references to be rejected safely.
 *
 * Like [SemanticTreeModel], this mutable UI state is confined to the Android main thread.
 *
 * @param allocator Monotonic allocator shared by registries created for the same host view.
 */
@MainThread
internal class AndroidVirtualNodeIdRegistry(
    private val allocator: AndroidVirtualNodeIdAllocator = AndroidVirtualNodeIdAllocator(),
) {
    private val virtualIdsByRiveId = mutableMapOf<Int, Int>()
    private val riveIdsByVirtualId = mutableMapOf<Int, Int>()

    /** Number of Rive nodes currently mapped into the Android virtual-node hierarchy. */
    val size: Int get() = virtualIdsByRiveId.size

    /**
     * Reconciles the registry with every node exported by [hierarchy].
     *
     * Existing projected nodes retain their Android identity through reorder and reparent
     * operations. Nodes removed by pruning, promotion, or absorption are retired before newly
     * exported nodes receive IDs.
     *
     * @param hierarchy Current projected semantic hierarchy.
     */
    fun reconcile(hierarchy: ProjectedSemanticHierarchy) {
        reconcile(hierarchy.activeNodeIds)
    }

    /**
     * Reconciles the registry with the complete set of currently projected Rive nodes.
     *
     * Existing nodes retain their virtual IDs regardless of input order. Removed mappings are
     * retired before new nodes are allocated, and duplicate Rive IDs produce only one mapping.
     *
     * @param activeRiveNodeIds Rive node IDs in the current Android projection.
     */
    fun reconcile(activeRiveNodeIds: Collection<Int>) {
        val activeIds = activeRiveNodeIds.toSet()
        val removedRiveIds = virtualIdsByRiveId.keys.filterNot(activeIds::contains)
        for (riveNodeId in removedRiveIds) {
            val virtualNodeId = virtualIdsByRiveId.remove(riveNodeId) ?: continue
            riveIdsByVirtualId.remove(virtualNodeId)
        }

        for (riveNodeId in activeRiveNodeIds) {
            if (virtualIdsByRiveId.containsKey(riveNodeId)) {
                continue
            }
            val virtualNodeId = allocator.allocate()
            virtualIdsByRiveId[riveNodeId] = virtualNodeId
            riveIdsByVirtualId[virtualNodeId] = riveNodeId
        }
    }

    /**
     * Returns the active Android virtual ID for a Rive node without creating a mapping.
     *
     * @param riveNodeId Rive semantic node ID.
     * @return Active Android virtual ID, or `null` when the node is not projected.
     */
    fun virtualIdForRiveNode(riveNodeId: Int): Int? = virtualIdsByRiveId[riveNodeId]

    /**
     * Returns the active Rive node ID for an Android virtual node query.
     *
     * @param virtualNodeId Android virtual accessibility node ID.
     * @return Active Rive semantic node ID, or `null` for reserved, retired, or unknown IDs.
     */
    fun riveNodeIdForVirtualNode(virtualNodeId: Int): Int? =
        riveIdsByVirtualId[virtualNodeId]

    /** Retires every active mapping without making their virtual IDs reusable. */
    fun clear() {
        virtualIdsByRiveId.clear()
        riveIdsByVirtualId.clear()
    }

}

/** Allocates monotonic virtual IDs across every helper generation owned by one host view. */
@MainThread
internal class AndroidVirtualNodeIdAllocator {
    private var nextVirtualId = 0L

    /** Returns the next nonnegative Android virtual ID without reusing an earlier allocation. */
    fun allocate(): Int {
        check(nextVirtualId <= Int.MAX_VALUE) {
            "Android virtual accessibility node ID space exhausted"
        }
        return nextVirtualId++.toInt()
    }
}

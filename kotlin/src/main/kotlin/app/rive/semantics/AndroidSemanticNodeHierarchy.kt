package app.rive.semantics

import android.view.View
import androidx.annotation.MainThread
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * Applies current projected relationships to this ephemeral Android accessibility node result.
 *
 * [SemanticTreeModel], [ProjectedSemanticHierarchy], and [AndroidVirtualNodeIdRegistry] are the
 * persistent accessibility state. Android requests fresh [AccessibilityNodeInfoCompat] snapshots
 * through its node provider; each request resolves stable virtual identity through [registry] and
 * reads current parent/child relationships from [hierarchy]. Hierarchy changes therefore require
 * provider invalidation rather than mutation of previously returned node objects.
 *
 * All relationship IDs are resolved before this node is changed. A stale requested node or an
 * inconsistent registry returns `false` without publishing a partial hierarchy.
 *
 * @param host Android view hosting the virtual accessibility hierarchy.
 * @param riveNodeId Rive semantic node represented by this query result.
 * @param hierarchy Current projected semantic hierarchy.
 * @param registry Current stable mapping between Rive and Android virtual node IDs.
 * @return `true` when the node's complete current relationships were applied, or `false` for
 * stale or inconsistent state.
 */
@MainThread
internal fun AccessibilityNodeInfoCompat.applySemanticNodeHierarchy(
    host: View,
    riveNodeId: Int,
    hierarchy: ProjectedSemanticHierarchy,
    registry: AndroidVirtualNodeIdRegistry,
): Boolean {
    if (!hierarchy.contains(riveNodeId) || registry.virtualIdForRiveNode(riveNodeId) == null) {
        return false
    }

    val parentVirtualId = hierarchy.parentOf(riveNodeId)?.let { parentRiveNodeId ->
        registry.virtualIdForRiveNode(parentRiveNodeId) ?: return false
    }
    val childVirtualIds = hierarchy.childrenOf(riveNodeId).map { childRiveNodeId ->
        registry.virtualIdForRiveNode(childRiveNodeId) ?: return false
    }

    parentVirtualId?.let { setParent(host, it) }
    childVirtualIds.forEach { addChild(host, it) }
    return true
}

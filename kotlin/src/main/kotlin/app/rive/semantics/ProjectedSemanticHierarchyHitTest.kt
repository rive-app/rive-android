package app.rive.semantics

import androidx.annotation.MainThread

/**
 * Returns the deepest exported semantic node containing a host-local point.
 *
 * Nodes are visited in authored traversal order so overlapping siblings resolve deterministically
 * without assuming that accessibility order represents visual z-order. Descendants are considered
 * before their parent. A descendant may be hit outside its parent's bounds because semantic bounds
 * do not describe visual clipping. Bounds are normalized and use Android's half-open rectangle
 * convention: left/top edges are included while right/bottom edges are excluded.
 *
 * [tree] and this hierarchy must represent the same semantic tree version. Missing source nodes
 * are ignored defensively so a stale query cannot return a removed Rive node ID.
 *
 * @param tree Main-thread-confined source tree containing node bounds.
 * @param x Horizontal coordinate in host-view pixels.
 * @param y Vertical coordinate in host-view pixels.
 * @return Matching Rive semantic node ID, or `null` when no exported node contains the point.
 */
@MainThread
internal fun ProjectedSemanticHierarchy.hitTest(
    tree: SemanticTreeModel,
    x: Float,
    y: Float,
): Int? {
    if (!x.isFinite() || !y.isFinite()) {
        return null
    }

    for (rootId in roots) {
        hitTestSubtree(tree, rootId, x, y)?.let { return it }
    }
    return null
}

/** Searches one projected subtree in traversal order, preferring descendants over parents. */
@MainThread
private fun ProjectedSemanticHierarchy.hitTestSubtree(
    tree: SemanticTreeModel,
    nodeId: Int,
    x: Float,
    y: Float,
): Int? {
    val children = childrenOf(nodeId)
    for (childId in children) {
        hitTestSubtree(tree, childId, x, y)?.let { return it }
    }

    val node = tree.nodeById(nodeId) ?: return null
    return nodeId.takeIf { node.containsPoint(x, y) }
}

/** Returns whether normalized node bounds contain a point using half-open edge semantics. */
private fun SemanticNodeData.containsPoint(x: Float, y: Float): Boolean {
    val left = minOf(minX, maxX)
    val top = minOf(minY, maxY)
    val right = maxOf(minX, maxX)
    val bottom = maxOf(minY, maxY)
    return x >= left && x < right && y >= top && y < bottom
}

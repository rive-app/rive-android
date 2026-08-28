package app.rive.semantics

import android.graphics.Rect
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import kotlin.math.ceil
import kotlin.math.floor

/** Integer pixel bounds relative to an Android virtual accessibility node's parent. */
internal data class AndroidAccessibilityBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/**
 * Maps this node's absolute view-space bounds into Android parent-relative integer pixels.
 *
 * Rive bounds are normalized because transformed geometry may reverse either axis. Absolute edges
 * are quantized before subtracting the quantized parent origin so Android's integer parent chain
 * reconstructs the same screen rectangle without accumulating rounding drift. Floor and ceiling
 * preserve the complete visual extent and prevent positive subpixel bounds from collapsing to an
 * empty Android rectangle. A root node remains relative to the host view's origin.
 *
 * @param parent Immediate semantic parent, or `null` when this node is a projected root.
 * @return Normalized integer bounds relative to [parent] or the host view.
 */
internal fun SemanticNodeData.toAndroidAccessibilityBounds(
    parent: SemanticNodeData?
): AndroidAccessibilityBounds {
    val normalizedLeft = minOf(minX, maxX)
    val normalizedTop = minOf(minY, maxY)
    val normalizedRight = maxOf(minX, maxX)
    val normalizedBottom = maxOf(minY, maxY)
    val parentLeft = parent?.let { floor(minOf(it.minX, it.maxX)).toInt() } ?: 0
    val parentTop = parent?.let { floor(minOf(it.minY, it.maxY)).toInt() } ?: 0

    return AndroidAccessibilityBounds(
        left = floor(normalizedLeft).toInt() - parentLeft,
        top = floor(normalizedTop).toInt() - parentTop,
        right = ceil(normalizedRight).toInt() - parentLeft,
        bottom = ceil(normalizedBottom).toInt() - parentTop
    )
}

/**
 * Applies parent-relative integer bounds to this Android virtual accessibility node.
 *
 * @param bounds Bounds produced by [toAndroidAccessibilityBounds].
 */
@Suppress("DEPRECATION") // ExploreByTouchHelper requires parent-local bounds.
internal fun AccessibilityNodeInfoCompat.applySemanticNodeBounds(
    bounds: AndroidAccessibilityBounds
) {
    setBoundsInParent(Rect(bounds.left, bounds.top, bounds.right, bounds.bottom))
}

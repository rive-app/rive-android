package app.rive.semantics

import androidx.annotation.MainThread
import app.rive.ExperimentalRiveSemantics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Collections

/**
 * Immutable semantic tree node stored in the platform model.
 *
 * Node data is UI state and must only be accessed on the Android main thread. When a diff changes
 * this node, the tree replaces it with a new instance rather than mutating an instance a caller
 * may have retained. A retained node therefore remains an immutable snapshot of the tree version
 * from which it was read.
 *
 * Runtime-published bounds are physical pixels in the view-space viewport supplied to the most
 * recent semantic diff drain. For the `Rive` composable that viewport is local to its texture
 * host. For `RiveCanvasSession` it is local to the render region, so a Canvas accessibility host
 * must add the region's left and top origin exactly once.
 *
 * @property id Stable Rive semantic node identifier within this state machine.
 * @property parentId Parent node identifier, or `-1` when this node is a tree root.
 * @property role Raw [SemanticRole.value] produced by core. Use [SemanticRole.fromValue] to decode
 *    known roles while retaining this raw value for forward compatibility.
 * @property label Primary accessible label. An empty string means no authored or derived label.
 * @property value Current accessible value, such as text-field content or a slider display value.
 *    An empty string means no value.
 * @property hint Authored usage hint for accessibility services. An empty string means no hint.
 * @property stateFlags Bitmask containing known [SemanticState] values and potentially unknown
 *    future bits.
 * @property traitFlags Bitmask containing known [SemanticTrait] values and potentially unknown
 *    future bits.
 * @property headingLevel Authored heading level, where zero means the node is not a heading.
 * @property minX Left edge in viewport-local physical pixels.
 * @property minY Top edge in viewport-local physical pixels.
 * @property maxX Right edge in viewport-local physical pixels.
 * @property maxY Bottom edge in viewport-local physical pixels.
 * @property children Unmodifiable child identifiers in authoritative authored traversal order.
 */
@MainThread
@ExperimentalRiveSemantics
data class SemanticNodeData internal constructor(
    val id: Int,
    val parentId: Int,
    val role: Int,
    val label: String,
    val value: String,
    val hint: String,
    val stateFlags: Int,
    val traitFlags: Int,
    val headingLevel: Int,
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
    val children: List<Int> = emptyList(),
)

/**
 * In-memory semantic tree maintained from incremental [SemanticsDiff] updates.
 *
 * Each state machine owns one model exposed through `StateMachine.semanticTree`; callers cannot
 * construct or apply diffs to models themselves. The tree has interior mutability and is confined
 * to the Android main thread. [versionFlow] may be collected from any thread, but collectors must
 * switch to the main thread before reading tree contents.
 *
 * Node bounds use the physical-pixel viewport contract documented by [SemanticNodeData].
 */
@ExperimentalRiveSemantics
class SemanticTreeModel @MainThread internal constructor() {
    private val nodesById = mutableMapOf<Int, SemanticNodeData>()
    private var rootIds: List<Int> = emptyList()
    private val _versionFlow = MutableStateFlow(0)

    /**
     * Emits a monotonically increasing version after a diff changes the tree.
     *
     * This flow may be collected from any thread. Read tree contents on the main thread after
     * observing a new version. The value is local to this Android model and is not the core tree
     * version or frame number.
     */
    val versionFlow: StateFlow<Int> = _versionFlow

    /** Current local model version, incremented only when an applied diff changes tree contents. */
    @get:MainThread
    val version: Int get() = _versionFlow.value

    /** Number of nodes currently retained in the raw semantic tree. */
    @get:MainThread
    val nodeCount: Int get() = nodesById.size

    /**
     * Unmodifiable root node identifiers in authoritative authored order for the current
     * [version]. A previously retained list remains a snapshot and is not mutated by later diffs.
     */
    @get:MainThread
    val roots: List<Int> get() = rootIds

    /**
     * Returns the immutable node with [id] for the current [version], or `null` when that node is
     * not in the tree. A returned node remains an immutable snapshot when later diffs replace or
     * remove that identifier.
     *
     * @param id Semantic node identifier.
     * @return The matching node, or `null`.
     */
    @MainThread
    fun nodeById(id: Int): SemanticNodeData? = nodesById[id]

    /** Clears all nodes and emits a new version when the tree was not already empty. */
    @MainThread
    internal fun clear() {
        if (nodesById.isEmpty() && rootIds.isEmpty()) {
            return
        }
        nodesById.clear()
        rootIds = emptyList()
        _versionFlow.value += 1
    }

    /** Returns an unmodifiable copy suitable for storage in publicly exposed tree state. */
    private fun immutableIds(ids: Collection<Int>): List<Int> =
        if (ids.isEmpty()) emptyList() else Collections.unmodifiableList(ArrayList(ids))

    /** Returns the child IDs for [parentId], where a negative ID represents the tree roots. */
    private fun childIds(parentId: Int): List<Int> =
        if (parentId < 0) rootIds else nodesById[parentId]?.children.orEmpty()

    /** Replaces the child IDs for [parentId] with an unmodifiable list. */
    private fun replaceChildIds(parentId: Int, children: Collection<Int>) {
        val immutableChildren = immutableIds(children)
        if (parentId < 0) {
            rootIds = immutableChildren
        } else {
            val parent = nodesById[parentId] ?: return
            nodesById[parentId] = parent.copy(children = immutableChildren)
        }
    }

    /** Removes [id] from its current parent or the root list. */
    private fun detach(id: Int): Boolean {
        val node = nodesById[id] ?: return false
        val currentChildren = childIds(node.parentId)
        if (id !in currentChildren) {
            return false
        }
        replaceChildIds(node.parentId, currentChildren.filterNot { it == id })
        return true
    }

    /** Moves [id] to [parentId] at [siblingIndex], falling back to a root if needed. */
    private fun relocate(id: Int, parentId: Int, siblingIndex: Int): Boolean {
        val node = nodesById[id] ?: return false
        val targetParentId = if (parentId >= 0 && nodesById.containsKey(parentId)) {
            parentId
        } else {
            -1
        }
        val sourceParentId = node.parentId

        if (sourceParentId != targetParentId) {
            detach(id)
        }

        val currentTargetChildren = childIds(targetParentId)
        val nextTargetChildren = currentTargetChildren.filterNot { it == id }.toMutableList()
        nextTargetChildren.add(siblingIndex.coerceIn(0, nextTargetChildren.size), id)
        val childrenChanged = currentTargetChildren != nextTargetChildren
        if (childrenChanged) {
            replaceChildIds(targetParentId, nextTargetChildren)
        }

        val parentChanged = sourceParentId != targetParentId
        if (parentChanged) {
            nodesById[id] = nodesById.getValue(id).copy(parentId = targetParentId)
        }
        return parentChanged || childrenChanged
    }

    /** Removes [id] and every descendant from the tree. */
    private fun removeSubtree(id: Int) {
        val node = nodesById[id] ?: return
        for (child in node.children) {
            removeSubtree(child)
        }
        detach(id)
        nodesById.remove(id)
    }

    /**
     * Apply one incremental diff in deterministic order.
     *
     * @param diff Incremental semantic changes to merge into this tree.
     */
    @MainThread
    internal fun applyDiff(diff: SemanticsDiff) {
        if (diff.isEmpty) return

        var changed = false

        for (id in diff.removed) {
            if (nodesById.containsKey(id)) {
                removeSubtree(id)
                changed = true
            }
        }

        for (node in diff.added) {
            val existing = nodesById[node.id]
            if (existing == null) {
                nodesById[node.id] = SemanticNodeData(
                    id = node.id,
                    parentId = -1,
                    role = node.role,
                    label = node.label,
                    value = node.value,
                    hint = node.hint,
                    stateFlags = node.stateFlags,
                    traitFlags = node.traitFlags,
                    headingLevel = node.headingLevel,
                    minX = node.minX,
                    minY = node.minY,
                    maxX = node.maxX,
                    maxY = node.maxY
                )
                changed = true
            } else {
                val updated = existing.copy(
                    role = node.role,
                    label = node.label,
                    value = node.value,
                    hint = node.hint,
                    stateFlags = node.stateFlags,
                    traitFlags = node.traitFlags,
                    headingLevel = node.headingLevel,
                    minX = node.minX,
                    minY = node.minY,
                    maxX = node.maxX,
                    maxY = node.maxY
                )
                if (existing != updated) {
                    nodesById[node.id] = updated
                    changed = true
                }
            }

            changed = relocate(node.id, node.parentId, node.siblingIndex) || changed
        }

        for (node in diff.moved) {
            val existing = nodesById[node.id] ?: continue
            val updated = existing.copy(
                minX = node.minX,
                minY = node.minY,
                maxX = node.maxX,
                maxY = node.maxY
            )
            if (existing != updated) {
                nodesById[node.id] = updated
            }
            relocate(node.id, node.parentId, node.siblingIndex)
            changed = true
        }

        for (update in diff.childrenUpdated) {
            if (update.parentId < 0) {
                val next = update.childIds.filter { nodesById.containsKey(it) }
                if (rootIds != next) {
                    replaceChildIds(-1, next)
                    for (id in rootIds) {
                        val child = nodesById[id] ?: continue
                        if (child.parentId >= 0) {
                            nodesById[id] = child.copy(parentId = -1)
                        }
                    }
                    changed = true
                }
                continue
            }

            val parent = nodesById[update.parentId] ?: continue
            val next = update.childIds.filter { nodesById.containsKey(it) }
            if (parent.children != next) {
                replaceChildIds(update.parentId, next)
                for (id in next) {
                    val child = nodesById[id] ?: continue
                    if (child.parentId != update.parentId) {
                        nodesById[id] = child.copy(parentId = update.parentId)
                    }
                }
                changed = true
            }
        }

        for (node in diff.updatedSemantic) {
            val existing = nodesById[node.id] ?: continue
            if (
                existing.role == node.role &&
                existing.label == node.label &&
                existing.value == node.value &&
                existing.hint == node.hint &&
                existing.stateFlags == node.stateFlags &&
                existing.traitFlags == node.traitFlags &&
                existing.headingLevel == node.headingLevel
            ) {
                continue
            }

            nodesById[node.id] = existing.copy(
                role = node.role,
                label = node.label,
                value = node.value,
                hint = node.hint,
                stateFlags = node.stateFlags,
                traitFlags = node.traitFlags,
                headingLevel = node.headingLevel
            )
            changed = true
        }

        for (update in diff.updatedGeometry) {
            val existing = nodesById[update.id] ?: continue
            if (
                existing.minX == update.minX &&
                existing.minY == update.minY &&
                existing.maxX == update.maxX &&
                existing.maxY == update.maxY
            ) {
                continue
            }
            nodesById[update.id] = existing.copy(
                minX = update.minX,
                minY = update.minY,
                maxX = update.maxX,
                maxY = update.maxY
            )
            changed = true
        }

        if (changed) {
            _versionFlow.value += 1
        }
    }
}

package app.rive.semantics.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.unit.Constraints
import app.rive.semantics.SemanticActionType
import app.rive.semantics.SemanticNodeActions
import app.rive.semantics.SemanticNodeContent
import app.rive.semantics.SemanticNodeState
import app.rive.semantics.SemanticRole
import app.rive.semantics.SemanticTreeModel
import app.rive.semantics.mapSemanticNodeActions
import app.rive.semantics.mapSemanticNodeContent
import app.rive.semantics.mapSemanticNodeState
import kotlin.math.max
import kotlin.math.roundToInt

/** Immutable Compose-specific rendering data retained for the fallback overlay. */
private data class RenderSemanticNode(
    val id: Int,
    val role: SemanticRole,
    val container: ComposeSemanticNodeContainer,
    val siblingIndex: Int,
    val content: SemanticNodeContent,
    val state: SemanticNodeState,
    val actions: SemanticNodeActions,
    val headingLevel: Int,
    val rect: Rect,
    val children: List<RenderSemanticNode>,
)

/**
 * Projects [tree] through Compose semantics.
 *
 * This fallback is intentionally separate from the production virtual-view accessibility path.
 *
 * @param tree Maintained Rive semantic tree to project.
 * @param onTap Dispatches an authored tap action.
 * @param onIncrease Dispatches an authored increase action.
 * @param onDecrease Dispatches an authored decrease action.
 * @param onRequestFocus Requests authored semantic focus.
 */
@Composable
internal fun RiveSemanticsOverlay(
    tree: SemanticTreeModel,
    onTap: (Int) -> Unit,
    onIncrease: (Int) -> Unit,
    onDecrease: (Int) -> Unit,
    onRequestFocus: (Int) -> Unit,
) {
    val version by tree.versionFlow.collectAsState()
    val roots = remember(version) {
        tree.roots.mapIndexedNotNull { siblingIndex, nodeId ->
            buildRenderableNode(nodeId, siblingIndex, tree)
        }
    }

    Layout(
        modifier = Modifier.semantics {
            isTraversalGroup = true
        },
        content = {
            roots.forEach { node ->
                RenderSemanticNodeComposable(
                    node = node,
                    onTap = onTap,
                    onIncrease = onIncrease,
                    onDecrease = onDecrease,
                    onRequestFocus = onRequestFocus
                )
            }
        }
    ) { measurables, constraints ->
        val placeables = measurables.mapIndexed { index, measurable ->
            val node = roots[index]
            measurable.measure(
                Constraints.fixed(
                    max(1, node.rect.width.roundToInt()),
                    max(1, node.rect.height.roundToInt())
                )
            )
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                val node = roots[index]
                placeable.place(node.rect.left.roundToInt(), node.rect.top.roundToInt())
            }
        }
    }
}

/** Renders [node] and its descendants as parent-relative Compose semantic nodes. */
@Composable
private fun RenderSemanticNodeComposable(
    node: RenderSemanticNode,
    onTap: (Int) -> Unit,
    onIncrease: (Int) -> Unit,
    onDecrease: (Int) -> Unit,
    onRequestFocus: (Int) -> Unit,
) {
    val actions = remember(
        node.id,
        node.actions,
        onIncrease,
        onDecrease,
        onRequestFocus
    ) {
        buildList {
            if (SemanticActionType.Increase in node.actions.semanticActions) {
                add(
                    CustomAccessibilityAction(label = "Increase") {
                        onIncrease(node.id)
                        true
                    }
                )
            }
            if (SemanticActionType.Decrease in node.actions.semanticActions) {
                add(
                    CustomAccessibilityAction(label = "Decrease") {
                        onDecrease(node.id)
                        true
                    }
                )
            }
            if (node.actions.canRequestFocus) {
                add(
                    CustomAccessibilityAction(label = "Focus") {
                        onRequestFocus(node.id)
                        true
                    }
                )
            }
        }
    }

    Layout(
        modifier = Modifier.semantics {
            applySemanticNodeContainer(node.container)
            applySemanticNodeTraversal(node.siblingIndex)
            applySemanticNodeContent(node.content)
            applySemanticNodeState(node.state)

            if (node.headingLevel > 0) {
                heading()
            }

            val semanticRole = node.role.toComposeRole()
            if (semanticRole != null) {
                role = semanticRole
            }

            if (SemanticActionType.Tap in node.actions.semanticActions) {
                onClick {
                    onTap(node.id)
                    true
                }
            }

            if (actions.isNotEmpty()) {
                customActions = actions
            }
        },
        content = {
            node.children.forEach { child ->
                RenderSemanticNodeComposable(
                    node = child,
                    onTap = onTap,
                    onIncrease = onIncrease,
                    onDecrease = onDecrease,
                    onRequestFocus = onRequestFocus
                )
            }
        }
    ) { measurables, _ ->
        val width = max(1, node.rect.width.roundToInt())
        val height = max(1, node.rect.height.roundToInt())
        val placeables = measurables.mapIndexed { index, measurable ->
            val child = node.children[index]
            measurable.measure(
                Constraints.fixed(
                    max(1, child.rect.width.roundToInt()),
                    max(1, child.rect.height.roundToInt())
                )
            )
        }

        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val child = node.children[index]
                val localLeft = (child.rect.left - node.rect.left).roundToInt()
                val localTop = (child.rect.top - node.rect.top).roundToInt()
                placeable.place(localLeft, localTop)
            }
        }
    }
}

/** Builds one renderable fallback subtree, pruning hidden and zero-area nodes. */
private fun buildRenderableNode(
    nodeId: Int,
    siblingIndex: Int,
    tree: SemanticTreeModel
): RenderSemanticNode? {
    val node = tree.nodeById(nodeId) ?: return null
    val state = mapSemanticNodeState(node.traitFlags, node.stateFlags)
    if (state.hidden) {
        return null
    }

    val mapped = mapRect(node.minX, node.minY, node.maxX, node.maxY)
    if (mapped.width <= 0f || mapped.height <= 0f) {
        return null
    }

    val children = node.children.mapIndexedNotNull { childIndex, childId ->
        buildRenderableNode(childId, childIndex, tree)
    }
    val role = SemanticRole.fromValue(node.role)

    return RenderSemanticNode(
        id = node.id,
        role = role,
        container = role.toComposeSemanticNodeContainer(),
        siblingIndex = siblingIndex,
        content = mapSemanticNodeContent(
            role = role,
            label = node.label,
            value = node.value,
            hint = node.hint,
            state = state
        ),
        state = state,
        actions = mapSemanticNodeActions(role, state),
        headingLevel = node.headingLevel,
        rect = mapped,
        children = children
    )
}

/** Normalizes authored bounds into a Compose [Rect]. */
private fun mapRect(
    minX: Float,
    minY: Float,
    maxX: Float,
    maxY: Float
): Rect {
    val left = minOf(minX, maxX)
    val top = minOf(minY, maxY)
    val right = maxOf(minX, maxX)
    val bottom = maxOf(minY, maxY)
    return Rect(left, top, right, bottom)
}

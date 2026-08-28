package app.rive.semantics.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.semantics.SemanticRole
import app.rive.semantics.SemanticTrait
import app.rive.semantics.SemanticTreeModel
import app.rive.semantics.SemanticsChildrenUpdate
import app.rive.semantics.SemanticsDiff
import app.rive.semantics.SemanticsDiffNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Preserves direct coverage of the parked Compose semantics projection. */
@RunWith(AndroidJUnit4::class)
class RiveSemanticsOverlayComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Verifies the fallback overlay exposes and dispatches a tap action. */
    @Test
    fun semanticsOverlay_exposesNodeAndDispatchesTap() {
        val tree = semanticTreeWith(
            node(
                id = 100,
                label = "Play",
                traitFlags = SemanticTrait.Enablable,
                minX = 10f,
                minY = 10f,
                maxX = 200f,
                maxY = 100f,
            )
        )
        var tappedId: Int? = null

        composeRule.setContent {
            RiveSemanticsOverlay(
                tree = tree,
                onTap = { tappedId = it },
                onIncrease = {},
                onDecrease = {},
                onRequestFocus = {},
            )
        }

        val clickAction = composeRule
            .onNodeWithContentDescription("Play")
            .fetchSemanticsNode()
            .config[SemanticsActions.OnClick]
        assertTrue(clickAction.action?.invoke() == true)
        composeRule.runOnIdle { assertEquals(100, tappedId) }
    }

    /** Verifies the fallback overlay retains its custom semantic-focus action. */
    @Test
    fun semanticsOverlay_dispatchesFocusCustomAction() {
        val tree = semanticTreeWith(
            node(
                id = 102,
                label = "Focusable button",
                traitFlags = SemanticTrait.Focusable,
                maxX = 120f,
                maxY = 60f,
            )
        )
        var focusedId: Int? = null

        composeRule.setContent {
            RiveSemanticsOverlay(
                tree = tree,
                onTap = {},
                onIncrease = {},
                onDecrease = {},
                onRequestFocus = { focusedId = it },
            )
        }

        val actions = composeRule
            .onNodeWithContentDescription("Focusable button")
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
        assertTrue(actions.first { it.label == "Focus" }.action())
        composeRule.runOnIdle { assertEquals(102, focusedId) }
    }

    /** Verifies fallback nodes remain positioned in parent-local view coordinates. */
    @Test
    fun semanticsOverlay_placesNodesInParentLocalCoordinates() {
        val tree = semanticTreeWith(
            node(
                id = 101,
                label = "Offset button",
                minX = 30f,
                minY = 40f,
                maxX = 110f,
                maxY = 90f,
            )
        )

        composeRule.setContent {
            with(LocalDensity.current) {
                Box(
                    modifier = Modifier
                        .padding(start = 13f.toDp(), top = 17f.toDp())
                        .requiredSize(300f.toDp(), 200f.toDp())
                ) {
                    RiveSemanticsOverlay(
                        tree = tree,
                        onTap = {},
                        onIncrease = {},
                        onDecrease = {},
                        onRequestFocus = {},
                    )
                }
            }
        }

        val bounds = composeRule
            .onNodeWithContentDescription("Offset button")
            .fetchSemanticsNode()
            .boundsInRoot
        assertEquals(43f, bounds.left)
        assertEquals(57f, bounds.top)
        assertEquals(123f, bounds.right)
        assertEquals(107f, bounds.bottom)
    }

    /** Builds a one-node semantic tree for direct fallback-overlay tests. */
    private fun semanticTreeWith(node: SemanticsDiffNode): SemanticTreeModel =
        SemanticTreeModel().apply {
            applyDiff(
                SemanticsDiff(
                    treeVersion = 1L,
                    frameNumber = 0L,
                    rootId = 0,
                    removed = intArrayOf(),
                    added = arrayOf(node),
                    moved = emptyArray(),
                    childrenUpdated = arrayOf(
                        SemanticsChildrenUpdate(parentId = -1, childIds = intArrayOf(node.id))
                    ),
                    updatedSemantic = emptyArray(),
                    updatedGeometry = emptyArray(),
                )
            )
        }

    /** Creates one semantic button with configurable geometry and traits. */
    private fun node(
        id: Int,
        label: String,
        traitFlags: Int = 0,
        minX: Float = 0f,
        minY: Float = 0f,
        maxX: Float,
        maxY: Float,
    ): SemanticsDiffNode = SemanticsDiffNode(
        id = id,
        role = SemanticRole.Button.value,
        label = label,
        value = "",
        hint = "",
        stateFlags = 0,
        traitFlags = traitFlags,
        headingLevel = 0,
        minX = minX,
        minY = minY,
        maxX = maxX,
        maxY = maxY,
        parentId = -1,
        siblingIndex = 0,
    )
}

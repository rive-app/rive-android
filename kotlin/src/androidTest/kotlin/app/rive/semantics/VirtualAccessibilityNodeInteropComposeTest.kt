package app.rive.semantics

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.RiveTextureView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Proves Android virtual accessibility nodes remain usable through Compose view interop. */
@RunWith(AndroidJUnit4::class)
class VirtualAccessibilityNodeInteropComposeTest {
    @get:Rule
    val composeRule = androidx.compose.ui.test.junit4.createAndroidComposeRule<ComponentActivity>()

    /** Verifies an embedded texture host publishes its virtual children to `UiAutomation`. */
    @Test
    fun virtualNodes_areDiscoverableThroughAndroidView() = withTouchExplorationEnabled {
        setProductionContent()

        composeRule.waitUntil(timeoutMillis = ACCESSIBILITY_TIMEOUT_MILLIS) {
            val root = rootInActiveWindow ?: return@waitUntil false
            findByContentDescription(root, FIRST_NODE_LABEL) != null &&
                findByContentDescription(root, SECOND_NODE_LABEL) != null
        }

        val root = assertNotNull(rootInActiveWindow)
        val firstNode = assertNotNull(findByContentDescription(root, FIRST_NODE_LABEL))
        assertEquals("android.widget.Button", firstNode.className.toString())
        assertNotNull(findByContentDescription(root, SECOND_NODE_LABEL))
    }

    /** Verifies an authored heading level reaches Android as native heading status. */
    @Test
    fun headingLevel_isPublishedThroughAndroidAccessibilityNode() =
        withTouchExplorationEnabled {
            setProductionContent(firstNodeHeadingLevel = 2)

            val firstNode = AccessibilityNodeInfoCompat.wrap(
                awaitNodeByContentDescription(FIRST_NODE_LABEL)
            )
            val secondNode = AccessibilityNodeInfoCompat.wrap(
                awaitNodeByContentDescription(SECOND_NODE_LABEL)
            )
            assertTrue(firstNode.isHeading)
            assertFalse(secondNode.isHeading)
        }

    /** Verifies nested bounds remain parent-relative and resolve to the expected screen position. */
    @Suppress("DEPRECATION") // ExploreByTouchHelper still consumes parent-local bounds.
    @Test
    fun nestedBounds_arePublishedRelativeToTheirVirtualParent() =
        withTouchExplorationEnabled {
            setProductionContent(
                firstNodeRole = SemanticRole.Group,
                nestedHierarchy = true
            )

            val parentNode = awaitNodeByContentDescription(FIRST_NODE_LABEL)
            val childNode = awaitNodeByContentDescription(SECOND_NODE_LABEL)
            val parentBounds = Rect()
            val childBounds = Rect()
            val parentScreenBounds = Rect()
            val childScreenBounds = Rect()
            parentNode.getBoundsInParent(parentBounds)
            childNode.getBoundsInParent(childBounds)
            parentNode.getBoundsInScreen(parentScreenBounds)
            childNode.getBoundsInScreen(childScreenBounds)

            assertEquals(Rect(10, 20, 190, 180), parentBounds)
            assertEquals(Rect(30, 40, 90, 100), childBounds)
            assertEquals(parentScreenBounds.left + 30, childScreenBounds.left)
            assertEquals(parentScreenBounds.top + 40, childScreenBounds.top)
            assertEquals(60, childScreenBounds.width())
            assertEquals(60, childScreenBounds.height())
        }

    /** Verifies Android clips screen bounds and hover to the host without altering authored bounds. */
    @Suppress("DEPRECATION") // ExploreByTouchHelper still consumes parent-local bounds.
    @Test
    fun overflowingBounds_areClippedToTheVisibleTextureHost() = withTouchExplorationEnabled {
        val treeReference = AtomicReference<SemanticTreeModel>()
        val host = setProductionContent(onTreeCreated = treeReference::set)
        val overflowingBounds = composeRule.runOnIdle {
            Rect(
                -CLIPPING_OVERFLOW_PX,
                CLIPPING_INSET_PX,
                host.width + CLIPPING_OVERFLOW_PX,
                host.height - CLIPPING_INSET_PX,
            ).also { bounds ->
                val tree = assertNotNull(treeReference.get())
                tree.applyDiff(
                    SemanticsDiff(
                        treeVersion = 2,
                        frameNumber = 1,
                        rootId = 0,
                        removed = intArrayOf(),
                        added = emptyArray(),
                        moved = emptyArray(),
                        childrenUpdated = emptyArray(),
                        updatedSemantic = emptyArray(),
                        updatedGeometry = arrayOf(
                            SemanticsBoundsUpdate(
                                id = FIRST_RIVE_NODE_ID,
                                minX = bounds.left.toFloat(),
                                minY = bounds.top.toFloat(),
                                maxX = bounds.right.toFloat(),
                                maxY = bounds.bottom.toFloat(),
                            )
                        ),
                    )
                )
                assertTrue(host.synchronizeSemantics())
            }
        }

        val node = awaitNodeByContentDescription(FIRST_NODE_LABEL)
        val parentBounds = Rect().also(node::getBoundsInParent)
        val screenBounds = Rect().also(node::getBoundsInScreen)
        val hostLocation = IntArray(2)
        composeRule.runOnIdle { host.getLocationOnScreen(hostLocation) }

        assertEquals(overflowingBounds, parentBounds)
        assertEquals(
            Rect(
                hostLocation[0],
                hostLocation[1] + CLIPPING_INSET_PX,
                hostLocation[0] + host.width,
                hostLocation[1] + host.height - CLIPPING_INSET_PX,
            ),
            screenBounds,
        )
        assertTrue(node.isVisibleToUser)

        composeRule.runOnIdle {
            val now = SystemClock.uptimeMillis()
            val outsideHover = MotionEvent.obtain(
                now,
                now,
                MotionEvent.ACTION_HOVER_ENTER,
                host.width + 1f,
                host.height / 2f,
                0,
            )
            try {
                assertFalse(host.dispatchSemanticHoverEvent(outsideHover))
            } finally {
                outsideHover.recycle()
            }
        }
    }

    /** Verifies hover exploration resolves an overlapping parent to its deepest child. */
    @Test
    fun nestedHover_targetsDeepestVirtualNode() = withTouchExplorationEnabled {
        val host = setProductionContent(
            firstNodeRole = SemanticRole.Group,
            nestedHierarchy = true,
        )

        val hoverEvent = executeAndWaitForEvent(
            {
                composeRule.runOnIdle {
                    val now = SystemClock.uptimeMillis()
                    val event = MotionEvent.obtain(
                        now,
                        now,
                        MotionEvent.ACTION_HOVER_ENTER,
                        50f,
                        70f,
                        0,
                    )
                    try {
                        assertTrue(host.dispatchSemanticHoverEvent(event))
                    } finally {
                        event.recycle()
                    }
                }
            },
            { event ->
                event.eventType == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER &&
                    event.source?.contentDescription?.toString() == SECOND_NODE_LABEL
            },
            ACCESSIBILITY_TIMEOUT_MILLIS,
        )

        assertEquals(SECOND_NODE_LABEL, hoverEvent.source?.contentDescription?.toString())
    }

    /** Verifies focus gain, sibling transition, and subtree exit are observed atomically. */
    @Test
    fun wrappedProvider_reportsAccessibilityFocusTransitions() = withTouchExplorationEnabled {
        val transitions = CopyOnWriteArrayList<SemanticAccessibilityFocusTransition>()
        setProductionContent(transitions::add)

        val firstNode = awaitNodeByContentDescription(FIRST_NODE_LABEL)
        val secondNode = awaitNodeByContentDescription(SECOND_NODE_LABEL)
        assertTrue(firstNode.performAction(AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS))
        composeRule.waitUntil(timeoutMillis = ACCESSIBILITY_TIMEOUT_MILLIS) {
            transitions.size == 1
        }
        assertTrue(secondNode.performAction(AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS))
        composeRule.waitUntil(timeoutMillis = ACCESSIBILITY_TIMEOUT_MILLIS) {
            transitions.size == 2
        }
        assertTrue(
            secondNode.performAction(
                AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS
            )
        )
        composeRule.waitUntil(timeoutMillis = ACCESSIBILITY_TIMEOUT_MILLIS) {
            transitions.size == 3
        }

        assertEquals(
            listOf(
                SemanticAccessibilityFocusTransition(null, FIRST_RIVE_NODE_ID),
                SemanticAccessibilityFocusTransition(FIRST_RIVE_NODE_ID, SECOND_RIVE_NODE_ID),
                SemanticAccessibilityFocusTransition(SECOND_RIVE_NODE_ID, null)
            ),
            transitions
        )
    }

    /** Verifies detaching the texture host clears its active accessibility focus exactly once. */
    @Test
    fun detachedTextureHost_clearsAccessibilityFocusOnce() = withTouchExplorationEnabled {
        val transitions = CopyOnWriteArrayList<SemanticAccessibilityFocusTransition>()
        val hostVisible = mutableStateOf(true)
        setProductionContent(
            onFocusTransition = transitions::add,
            isHostVisible = { hostVisible.value },
        )
        val firstNode = awaitNodeByContentDescription(FIRST_NODE_LABEL)
        assertTrue(firstNode.performAction(AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS))
        composeRule.waitUntil(timeoutMillis = ACCESSIBILITY_TIMEOUT_MILLIS) {
            transitions.size == 1
        }

        composeRule.runOnIdle { hostVisible.value = false }
        composeRule.waitUntil(timeoutMillis = ACCESSIBILITY_TIMEOUT_MILLIS) {
            transitions.size == 2
        }

        assertEquals(
            listOf(
                SemanticAccessibilityFocusTransition(null, FIRST_RIVE_NODE_ID),
                SemanticAccessibilityFocusTransition(FIRST_RIVE_NODE_ID, null),
            ),
            transitions,
        )
    }

    /** Verifies temporary host detachment republishes current data in a fresh helper generation. */
    @Test
    fun reattachedTextureHost_reinstallsCurrentSemanticsUntilExplicitlyCleared() =
        withTouchExplorationEnabled {
            val tree = buildProductionTree(
                firstNodeState = null,
                firstNodeRole = SemanticRole.Button,
                firstNodeContent = null,
                firstNodeHeadingLevel = 0,
                nestedHierarchy = false,
            )
            lateinit var container: FrameLayout
            lateinit var host: RiveTextureView
            onMainThread {
                container = FrameLayout(composeRule.activity)
                host = RiveTextureView(composeRule.activity).apply {
                    installSemantics(
                        tree = tree,
                        onSemanticAction = { _, _ -> },
                        onAccessibilityFocusChanged = {},
                    )
                }
                composeRule.activity.setContentView(container)
                container.addView(
                    host,
                    FrameLayout.LayoutParams(PRODUCTION_SIZE_PX, PRODUCTION_SIZE_PX),
                )
            }
            assertNotNull(awaitNodeByContentDescription(FIRST_NODE_LABEL))
            val retiredProvider = onMainThread {
                assertNotNull(ViewCompat.getAccessibilityNodeProvider(host))
            }

            onMainThread {
                container.removeView(host)
                tree.applyDiff(
                    SemanticsDiff(
                        treeVersion = 2,
                        frameNumber = 1,
                        rootId = 0,
                        removed = intArrayOf(),
                        added = emptyArray(),
                        moved = emptyArray(),
                        childrenUpdated = emptyArray(),
                        updatedSemantic = arrayOf(
                            productionNode(
                                id = FIRST_RIVE_NODE_ID,
                                role = SemanticRole.Button,
                                state = DEFAULT_NODE_STATE,
                                content = mapSemanticNodeContent(
                                    role = SemanticRole.Button,
                                    label = UPDATED_FIRST_NODE_LABEL,
                                    value = "",
                                    hint = "",
                                    state = DEFAULT_NODE_STATE,
                                ),
                                headingLevel = 0,
                                bounds = floatArrayOf(0f, 0f, 100f, 200f),
                                parentId = -1,
                                siblingIndex = 0,
                            )
                        ),
                        updatedGeometry = emptyArray(),
                    )
                )
                container.addView(host)
            }

            assertNotNull(awaitNodeByContentDescription(UPDATED_FIRST_NODE_LABEL))
            onMainThread {
                for (virtualNodeId in 0..MAX_TEST_VIRTUAL_NODE_ID) {
                    assertNull(retiredProvider.createAccessibilityNodeInfo(virtualNodeId))
                }
                assertNotNull(ViewCompat.getAccessibilityNodeProvider(host))

                host.clearSemantics()
                container.removeView(host)
                container.addView(host)
                assertNull(ViewCompat.getAccessibilityNodeProvider(host))
            }
        }

    /** Verifies replacing the helper clears old focus and retires stale framework nodes. */
    @Test
    fun replacedSemanticsHelper_clearsFocusAndPublishesSuccessor() =
        withTouchExplorationEnabled {
            val transitions = CopyOnWriteArrayList<SemanticAccessibilityFocusTransition>()
            val host = setProductionContent(transitions::add)
            val retiredProvider = composeRule.runOnIdle {
                assertNotNull(ViewCompat.getAccessibilityNodeProvider(host))
            }
            val staleNode = awaitNodeByContentDescription(FIRST_NODE_LABEL)
            assertTrue(
                staleNode.performAction(AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS)
            )
            composeRule.waitUntil(timeoutMillis = ACCESSIBILITY_TIMEOUT_MILLIS) {
                transitions.size == 1
            }

            composeRule.runOnIdle {
                host.installSemantics(
                    tree = SemanticTreeModel().apply {
                        applyDiff(
                            SemanticsDiff(
                                treeVersion = 1,
                                frameNumber = 0,
                                rootId = 0,
                                removed = intArrayOf(),
                                added = arrayOf(
                                    productionNode(
                                        id = REPLACEMENT_RIVE_NODE_ID,
                                        role = SemanticRole.Button,
                                        state = DEFAULT_NODE_STATE,
                                        content = mapSemanticNodeContent(
                                            role = SemanticRole.Button,
                                            label = REPLACEMENT_NODE_LABEL,
                                            value = "",
                                            hint = "",
                                            state = DEFAULT_NODE_STATE,
                                        ),
                                        headingLevel = 0,
                                        bounds = floatArrayOf(0f, 0f, 200f, 200f),
                                        parentId = -1,
                                        siblingIndex = 0,
                                    )
                                ),
                                moved = emptyArray(),
                                childrenUpdated = emptyArray(),
                                updatedSemantic = emptyArray(),
                                updatedGeometry = emptyArray(),
                            )
                        )
                    },
                    onSemanticAction = { _, _ -> },
                    onAccessibilityFocusChanged = transitions::add,
                )
            }

            assertNotNull(awaitNodeByContentDescription(REPLACEMENT_NODE_LABEL))
            composeRule.runOnIdle {
                for (virtualNodeId in 0..MAX_TEST_VIRTUAL_NODE_ID) {
                    assertNull(retiredProvider.createAccessibilityNodeInfo(virtualNodeId))
                    assertFalse(
                        retiredProvider.performAction(
                            virtualNodeId,
                            AccessibilityNodeInfoCompat.ACTION_CLICK,
                            null,
                        )
                    )
                }
            }
            composeRule.waitUntil(timeoutMillis = ACCESSIBILITY_TIMEOUT_MILLIS) {
                transitions.size == 2
            }
            assertEquals(
                listOf(
                    SemanticAccessibilityFocusTransition(null, FIRST_RIVE_NODE_ID),
                    SemanticAccessibilityFocusTransition(FIRST_RIVE_NODE_ID, null),
                ),
                transitions,
            )
            assertFalse(
                staleNode.performAction(AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS)
            )
        }

    /** Verifies generic Rive state reaches Android without claiming Android input focus. */
    @Test
    fun semanticState_isPublishedThroughAndroidAccessibilityNode() =
        withTouchExplorationEnabled {
            val state = mapSemanticNodeState(
                traitFlags = SemanticTrait.Expandable or
                    SemanticTrait.Selectable or
                    SemanticTrait.Checkable or
                    SemanticTrait.Requirable or
                    SemanticTrait.Enablable or
                    SemanticTrait.Focusable,
                stateFlags = SemanticState.Expanded or
                    SemanticState.Selected or
                    CHECK_STATE_MIXED_FLAGS or
                    SemanticState.Required or
                    SemanticState.Disabled or
                    SemanticState.Focused or
                    SemanticState.LiveRegion
            )
            setProductionContent(firstNodeState = state)

            val firstNode = AccessibilityNodeInfoCompat.wrap(
                awaitNodeByContentDescription(FIRST_NODE_LABEL)
            )
            assertEquals(
                AccessibilityNodeInfo.EXPANDED_STATE_FULL,
                firstNode.expandedState
            )
            assertTrue(firstNode.isSelected)
            assertTrue(firstNode.isCheckable)
            assertEquals(AccessibilityNodeInfo.CHECKED_STATE_PARTIAL, firstNode.checked)
            assertTrue(firstNode.isFieldRequired)
            assertFalse(firstNode.isEnabled)
            assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, firstNode.liveRegion)
            assertFalse(firstNode.isFocused)
        }

    /** Verifies disabled gating and dispatch for standard Android accessibility actions. */
    @Test
    fun semanticActions_areAdvertisedAndOnlyAvailableActionsDispatch() =
        withTouchExplorationEnabled {
            val dispatchedActions = CopyOnWriteArrayList<DispatchedSemanticAction>()
            val disabledState = mapSemanticNodeState(
                traitFlags = SemanticTrait.Enablable,
                stateFlags = SemanticState.Disabled
            )
            setProductionContent(
                firstNodeState = disabledState,
                onSemanticAction = { nodeId, action ->
                    dispatchedActions.add(DispatchedSemanticAction(nodeId, action))
                }
            )

            val disabledNode = awaitNodeByContentDescription(FIRST_NODE_LABEL)
            val enabledNode = awaitNodeByContentDescription(SECOND_NODE_LABEL)
            assertFalse(disabledNode.isClickable)
            assertFalse(disabledNode.hasAction(AccessibilityNodeInfoCompat.ACTION_CLICK))
            assertFalse(disabledNode.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK))
            assertTrue(enabledNode.isClickable)
            assertTrue(enabledNode.hasAction(AccessibilityNodeInfoCompat.ACTION_CLICK))
            assertFalse(enabledNode.performAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK))
            assertTrue(enabledNode.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK))
            composeRule.waitUntil(timeoutMillis = ACCESSIBILITY_TIMEOUT_MILLIS) {
                dispatchedActions.size == 1
            }
            assertEquals(
                listOf(DispatchedSemanticAction(SECOND_RIVE_NODE_ID, SemanticActionType.Tap)),
                dispatchedActions
            )
        }

    /** Verifies slider adjustment actions use Android's standard scrolling actions. */
    @Test
    fun sliderActions_areAdvertisedAndDispatchIncreaseAndDecrease() =
        withTouchExplorationEnabled {
            val dispatchedActions = CopyOnWriteArrayList<DispatchedSemanticAction>()
            setProductionContent(
                firstNodeRole = SemanticRole.Slider,
                onSemanticAction = { nodeId, action ->
                    dispatchedActions.add(DispatchedSemanticAction(nodeId, action))
                }
            )

            val sliderNode = awaitNodeByContentDescription(FIRST_NODE_LABEL)
            val sliderNodeCompat = AccessibilityNodeInfoCompat.wrap(sliderNode)
            assertFalse(sliderNode.isClickable)
            assertFalse(sliderNode.hasAction(AccessibilityNodeInfoCompat.ACTION_CLICK))
            assertFalse(
                sliderNode.hasAction(
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SET_PROGRESS.id
                )
            )
            assertNull(sliderNodeCompat.rangeInfo)
            assertTrue(sliderNode.hasAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD))
            assertTrue(sliderNode.hasAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD))
            assertTrue(
                sliderNode.performAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD)
            )
            assertTrue(
                sliderNode.performAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD)
            )
            composeRule.waitUntil(timeoutMillis = ACCESSIBILITY_TIMEOUT_MILLIS) {
                dispatchedActions.size == 2
            }
            assertEquals(
                listOf(
                    DispatchedSemanticAction(FIRST_RIVE_NODE_ID, SemanticActionType.Increase),
                    DispatchedSemanticAction(FIRST_RIVE_NODE_ID, SemanticActionType.Decrease)
                ),
                dispatchedActions
            )
        }

    /** Verifies control labels, values, and usage hints use distinct Android properties. */
    @Test
    fun controlContent_isPublishedThroughAndroidAccessibilityNode() =
        withTouchExplorationEnabled {
            val content = mapSemanticNodeContent(
                role = SemanticRole.Button,
                label = "Submit",
                value = "Ready",
                hint = "Activates form",
                state = DEFAULT_NODE_STATE
            )
            setProductionContent(firstNodeContent = content)

            val node = AccessibilityNodeInfoCompat.wrap(awaitNodeByContentDescription("Submit"))
            assertEquals("Submit", node.contentDescription.toString())
            assertEquals("Ready", node.stateDescription?.toString())
            assertEquals("Activates form", node.supplementalDescription?.toString())
            assertEquals(null, node.text)
        }

    /** Verifies static text remains text instead of replacing subtree content. */
    @Test
    fun staticTextContent_isPublishedThroughAndroidAccessibilityNode() =
        withTouchExplorationEnabled {
            val content = mapSemanticNodeContent(
                role = SemanticRole.Text,
                label = "Rendered text",
                value = "Current value",
                hint = "Updated today",
                state = DEFAULT_NODE_STATE
            )
            setProductionContent(
                firstNodeRole = SemanticRole.Text,
                firstNodeContent = content
            )

            val node = AccessibilityNodeInfoCompat.wrap(awaitNodeByText("Rendered text"))
            assertEquals("Rendered text", node.text.toString())
            assertEquals("Current value", node.stateDescription?.toString())
            assertEquals("Updated today", node.supplementalDescription?.toString())
            assertEquals(null, node.contentDescription)
        }

    /** Verifies text fields keep their label separate from entered text and editability. */
    @Test
    fun readOnlyTextFieldContent_isPublishedThroughAndroidAccessibilityNode() =
        withTouchExplorationEnabled {
            val state = mapSemanticNodeState(
                traitFlags = 0,
                stateFlags = SemanticState.ReadOnly
            )
            val content = mapSemanticNodeContent(
                role = SemanticRole.TextField,
                label = "Identifier",
                value = "1234",
                hint = "Assigned by the server",
                state = state
            )
            setProductionContent(
                firstNodeState = state,
                firstNodeRole = SemanticRole.TextField,
                firstNodeContent = content
            )

            val node = AccessibilityNodeInfoCompat.wrap(
                awaitNodeByContentDescription("Identifier")
            )
            assertEquals("android.widget.EditText", node.className.toString())
            assertEquals("Identifier", node.contentDescription.toString())
            assertEquals("1234", node.text.toString())
            assertEquals("Assigned by the server", node.supplementalDescription?.toString())
            assertFalse(node.isEditable)
            assertFalse(node.isPassword)
            assertFalse(node.isAccessibilityDataSensitive)
        }

    /** Verifies multiline state is published without claiming unsupported editability. */
    @Test
    fun multilineTextFieldContent_isPublishedThroughAndroidAccessibilityNode() =
        withTouchExplorationEnabled {
            val state = mapSemanticNodeState(
                traitFlags = 0,
                stateFlags = SemanticState.Multiline
            )
            val content = mapSemanticNodeContent(
                role = SemanticRole.TextField,
                label = "Biography",
                value = "First line\nSecond line",
                hint = "",
                state = state
            )
            setProductionContent(
                firstNodeState = state,
                firstNodeRole = SemanticRole.TextField,
                firstNodeContent = content
            )

            val frameworkNode = awaitNodeByContentDescription("Biography")
            val node = AccessibilityNodeInfoCompat.wrap(frameworkNode)
            assertFalse(node.isEditable)
            assertTrue(node.isMultiLine)
            assertFalse(
                frameworkNode.hasAction(
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SET_TEXT.id
                )
            )
        }

    /** Verifies obscured text fields expose native metadata without exposing raw text. */
    @Test
    fun obscuredTextFieldContent_isPublishedWithoutRawValue() =
        withTouchExplorationEnabled {
            val secret = "do not expose"
            val state = mapSemanticNodeState(
                traitFlags = 0,
                stateFlags = SemanticState.Obscured
            )
            val content = mapSemanticNodeContent(
                role = SemanticRole.TextField,
                label = "Password",
                value = secret,
                hint = "Required",
                state = state
            )
            setProductionContent(
                firstNodeState = state,
                firstNodeRole = SemanticRole.TextField,
                firstNodeContent = content
            )

            val node = AccessibilityNodeInfoCompat.wrap(
                awaitNodeByContentDescription("Password")
            )
            assertEquals("android.widget.EditText", node.className.toString())
            assertEquals("Password", node.contentDescription.toString())
            assertEquals(null, node.text)
            assertEquals("Required", node.supplementalDescription?.toString())
            assertFalse(node.isEditable)
            assertTrue(node.isPassword)
            assertTrue(node.isAccessibilityDataSensitive)
            assertFalse(node.toString().contains(secret))
        }

    /** Verifies tree refresh invalidates the attached provider for updates and removal. */
    @Test
    fun synchronizedTreeChanges_arePublishedThroughUiAutomation() =
        withTouchExplorationEnabled {
            val treeReference = AtomicReference<SemanticTreeModel>()
            val host = setProductionContent(onTreeCreated = treeReference::set)
            assertNotNull(awaitNodeByContentDescription(FIRST_NODE_LABEL))

            composeRule.runOnIdle {
                val tree = assertNotNull(treeReference.get())
                tree.applyDiff(
                    SemanticsDiff(
                        treeVersion = 2,
                        frameNumber = 1,
                        rootId = 0,
                        removed = intArrayOf(),
                        added = emptyArray(),
                        moved = emptyArray(),
                        childrenUpdated = emptyArray(),
                        updatedSemantic = arrayOf(
                            productionNode(
                                id = FIRST_RIVE_NODE_ID,
                                role = SemanticRole.Button,
                                state = DEFAULT_NODE_STATE,
                                content = mapSemanticNodeContent(
                                    role = SemanticRole.Button,
                                    label = UPDATED_FIRST_NODE_LABEL,
                                    value = "",
                                    hint = "",
                                    state = DEFAULT_NODE_STATE,
                                ),
                                headingLevel = 0,
                                bounds = floatArrayOf(0f, 0f, 100f, 200f),
                                parentId = -1,
                                siblingIndex = 0,
                            )
                        ),
                        updatedGeometry = emptyArray(),
                    )
                )
                assertTrue(host.synchronizeSemantics())
            }
            assertNotNull(awaitNodeByContentDescription(UPDATED_FIRST_NODE_LABEL))

            composeRule.runOnIdle {
                val tree = assertNotNull(treeReference.get())
                tree.applyDiff(
                    SemanticsDiff(
                        treeVersion = 3,
                        frameNumber = 2,
                        rootId = 0,
                        removed = intArrayOf(FIRST_RIVE_NODE_ID),
                        added = emptyArray(),
                        moved = emptyArray(),
                        childrenUpdated = emptyArray(),
                        updatedSemantic = emptyArray(),
                        updatedGeometry = emptyArray(),
                    )
                )
                assertTrue(host.synchronizeSemantics())
            }
            composeRule.waitUntil(timeoutMillis = ACCESSIBILITY_TIMEOUT_MILLIS) {
                val root = rootInActiveWindow ?: return@waitUntil false
                findByContentDescription(root, UPDATED_FIRST_NODE_LABEL) == null &&
                    findByContentDescription(root, SECOND_NODE_LABEL) != null
            }
        }

    /** Verifies structural refresh emits a subtree event without disturbing surviving focus. */
    @Test
    fun structuralRefresh_emitsSubtreeEventAndRetainsSurvivingFocus() =
        withTouchExplorationEnabled {
            val transitions = CopyOnWriteArrayList<SemanticAccessibilityFocusTransition>()
            val treeReference = AtomicReference<SemanticTreeModel>()
            val host = setProductionContent(
                onFocusTransition = transitions::add,
                onTreeCreated = treeReference::set,
            )
            val firstNode = awaitNodeByContentDescription(FIRST_NODE_LABEL)
            assertTrue(
                firstNode.performAction(AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS)
            )
            composeRule.waitUntil(timeoutMillis = ACCESSIBILITY_TIMEOUT_MILLIS) {
                transitions.size == 1
            }

            val contentChangedEvent = executeAndWaitForEvent(
                {
                    composeRule.runOnIdle {
                        val tree = assertNotNull(treeReference.get())
                        tree.applyDiff(
                            SemanticsDiff(
                                treeVersion = 2,
                                frameNumber = 1,
                                rootId = 0,
                                removed = intArrayOf(),
                                added = arrayOf(
                                    productionNode(
                                        id = STRUCTURAL_RIVE_NODE_ID,
                                        role = SemanticRole.Button,
                                        state = DEFAULT_NODE_STATE,
                                        content = mapSemanticNodeContent(
                                            role = SemanticRole.Button,
                                            label = STRUCTURAL_NODE_LABEL,
                                            value = "",
                                            hint = "",
                                            state = DEFAULT_NODE_STATE,
                                        ),
                                        headingLevel = 0,
                                        bounds = floatArrayOf(0f, 0f, 200f, 200f),
                                        parentId = -1,
                                        siblingIndex = 2,
                                    )
                                ),
                                moved = emptyArray(),
                                childrenUpdated = emptyArray(),
                                updatedSemantic = emptyArray(),
                                updatedGeometry = emptyArray(),
                            )
                        )
                        assertTrue(host.synchronizeSemantics())
                    }
                },
                { event ->
                    event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                        event.contentChangeTypes and
                        AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE != 0
                },
                ACCESSIBILITY_TIMEOUT_MILLIS,
            )

            assertEquals(
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                contentChangedEvent.eventType,
            )
            assertNotNull(awaitNodeByContentDescription(STRUCTURAL_NODE_LABEL))
            val refreshedFirstNode = awaitNodeByContentDescription(FIRST_NODE_LABEL)
            assertTrue(refreshedFirstNode.isAccessibilityFocused)
            assertEquals(
                listOf(SemanticAccessibilityFocusTransition(null, FIRST_RIVE_NODE_ID)),
                transitions,
            )
        }

    /** Waits for and returns one virtual node from the active Android accessibility tree. */
    private fun UiAutomation.awaitNodeByContentDescription(
        contentDescription: String
    ): AccessibilityNodeInfo {
        var result: AccessibilityNodeInfo? = null
        composeRule.waitUntil(timeoutMillis = ACCESSIBILITY_TIMEOUT_MILLIS) {
            val root = rootInActiveWindow ?: return@waitUntil false
            findByContentDescription(root, contentDescription)?.let { result = it }
            result != null
        }
        return assertNotNull(result)
    }

    /** Waits for and returns one virtual node with the requested text. */
    private fun UiAutomation.awaitNodeByText(text: String): AccessibilityNodeInfo {
        var result: AccessibilityNodeInfo? = null
        composeRule.waitUntil(timeoutMillis = ACCESSIBILITY_TIMEOUT_MILLIS) {
            val root = rootInActiveWindow ?: return@waitUntil false
            findByText(root, text)?.let { result = it }
            result != null
        }
        return assertNotNull(result)
    }

    /**
     * Installs the production texture host inside Compose and waits for layout to complete.
     *
     * @param onFocusTransition Receives accessibility-focus changes from the wrapped provider.
     * @param firstNodeState Optional semantic state to publish on the first virtual node.
     * @param firstNodeRole Semantic role to publish on the first virtual node.
     * @param firstNodeContent Optional classified content to publish on the first virtual node.
     * @param firstNodeHeadingLevel Authored heading level for the first virtual node.
     * @param nestedHierarchy Whether the second node should be a child of the first.
     * @param onSemanticAction Receives semantic actions dispatched by the wrapped provider.
     * @param onTreeCreated Receives the main-thread semantic tree installed in the host.
     * @param isHostVisible Returns whether the host should remain in the composition.
     * @return The embedded texture host.
     */
    private fun setProductionContent(
        onFocusTransition: (SemanticAccessibilityFocusTransition) -> Unit = {},
        firstNodeState: SemanticNodeState? = null,
        firstNodeRole: SemanticRole = SemanticRole.Button,
        firstNodeContent: SemanticNodeContent? = null,
        firstNodeHeadingLevel: Int = 0,
        nestedHierarchy: Boolean = false,
        onSemanticAction: (Int, SemanticActionType) -> Unit = { _, _ -> },
        onTreeCreated: (SemanticTreeModel) -> Unit = {},
        isHostVisible: () -> Boolean = { true },
    ): RiveTextureView {
        val hostReference = AtomicReference<RiveTextureView>()
        composeRule.setContent {
            if (isHostVisible()) {
                AndroidView(
                    modifier = Modifier.requiredSize(PRODUCTION_SIZE_DP.dp),
                    factory = { context ->
                        val tree = buildProductionTree(
                            firstNodeState = firstNodeState,
                            firstNodeRole = firstNodeRole,
                            firstNodeContent = firstNodeContent,
                            firstNodeHeadingLevel = firstNodeHeadingLevel,
                            nestedHierarchy = nestedHierarchy,
                        )
                        onTreeCreated(tree)
                        RiveTextureView(context).apply {
                            isOpaque = false
                            installSemantics(
                                tree = tree,
                                onSemanticAction = onSemanticAction,
                                onAccessibilityFocusChanged = onFocusTransition,
                            )
                        }.also(hostReference::set)
                    }
                )
            }
        }
        return composeRule.runOnIdle {
            assertNotNull(hostReference.get())
        }
    }

    /**
     * Runs a test with `UiAutomation` requesting Android touch-exploration mode.
     *
     * `ExploreByTouchHelper` rejects accessibility-focus actions unless touch exploration is
     * active, matching the conditions under which TalkBack drives those actions.
     *
     * @param block Test body with the configured `UiAutomation` receiver.
     */
    private fun withTouchExplorationEnabled(block: UiAutomation.() -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val uiAutomation = instrumentation.uiAutomation
        val serviceInfo = assertNotNull(uiAutomation.serviceInfo)
        val originalFlags = serviceInfo.flags
        val accessibilityManager = instrumentation.targetContext.getSystemService(
            AccessibilityManager::class.java
        )

        try {
            serviceInfo.flags = originalFlags or
                AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            uiAutomation.serviceInfo = serviceInfo
            composeRule.waitUntil(timeoutMillis = ACCESSIBILITY_TIMEOUT_MILLIS) {
                accessibilityManager.isTouchExplorationEnabled
            }
            uiAutomation.block()
        } finally {
            serviceInfo.flags = originalFlags
            uiAutomation.serviceInfo = serviceInfo
        }
    }

    /** Finds a node by content description in an Android accessibility subtree. */
    private fun findByContentDescription(
        root: AccessibilityNodeInfo,
        contentDescription: String
    ): AccessibilityNodeInfo? {
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (node.contentDescription?.toString() == contentDescription) {
                return node
            }
            repeat(node.childCount) { index ->
                node.getChild(index)?.let(pending::add)
            }
        }
        return null
    }

    /** Finds a node by text in an Android accessibility subtree. */
    private fun findByText(
        root: AccessibilityNodeInfo,
        text: String
    ): AccessibilityNodeInfo? {
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (node.text?.toString() == text) {
                return node
            }
            repeat(node.childCount) { index ->
                node.getChild(index)?.let(pending::add)
            }
        }
        return null
    }

    /** Returns whether this framework node advertises the requested action ID. */
    private fun AccessibilityNodeInfo.hasAction(actionId: Int): Boolean =
        actionList.any { action -> action.id == actionId }

    /** Executes [block] synchronously on Android's main thread and returns its result. */
    private fun <T> onMainThread(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = runCatching(block)
        }
        return checkNotNull(result).getOrThrow()
    }
}

/** Rive semantic action dispatched by the production virtual-node provider. */
private data class DispatchedSemanticAction(val nodeId: Int, val action: SemanticActionType)

/** Raw semantic trait and state flags reconstructed for production-provider fixtures. */
private data class RawSemanticFlags(val traitFlags: Int, val stateFlags: Int)

/** Builds the two-node semantic tree used by the production provider parity tests. */
private fun buildProductionTree(
    firstNodeState: SemanticNodeState?,
    firstNodeRole: SemanticRole,
    firstNodeContent: SemanticNodeContent?,
    firstNodeHeadingLevel: Int,
    nestedHierarchy: Boolean,
): SemanticTreeModel {
    val effectiveState = firstNodeState ?: DEFAULT_NODE_STATE
    val effectiveContent = firstNodeContent ?: mapSemanticNodeContent(
        role = firstNodeRole,
        label = FIRST_NODE_LABEL,
        value = "",
        hint = "",
        state = effectiveState,
    )
    val firstBounds = if (nestedHierarchy) {
        floatArrayOf(10f, 20f, 190f, 180f)
    } else {
        floatArrayOf(0f, 0f, 100f, 200f)
    }
    val secondBounds = if (nestedHierarchy) {
        floatArrayOf(40f, 60f, 100f, 120f)
    } else {
        floatArrayOf(100f, 0f, 200f, 200f)
    }

    return SemanticTreeModel().apply {
        applyDiff(
            SemanticsDiff(
                treeVersion = 1,
                frameNumber = 0,
                rootId = 0,
                removed = intArrayOf(),
                added = arrayOf(
                    productionNode(
                        id = FIRST_RIVE_NODE_ID,
                        role = firstNodeRole,
                        state = effectiveState,
                        content = effectiveContent,
                        headingLevel = firstNodeHeadingLevel,
                        bounds = firstBounds,
                        parentId = -1,
                        siblingIndex = 0,
                    ),
                    productionNode(
                        id = SECOND_RIVE_NODE_ID,
                        role = SemanticRole.Button,
                        state = DEFAULT_NODE_STATE,
                        content = mapSemanticNodeContent(
                            role = SemanticRole.Button,
                            label = SECOND_NODE_LABEL,
                            value = "",
                            hint = "",
                            state = DEFAULT_NODE_STATE,
                        ),
                        headingLevel = 0,
                        bounds = secondBounds,
                        parentId = if (nestedHierarchy) FIRST_RIVE_NODE_ID else -1,
                        siblingIndex = if (nestedHierarchy) 0 else 1,
                    ),
                ),
                moved = emptyArray(),
                childrenUpdated = emptyArray(),
                updatedSemantic = emptyArray(),
                updatedGeometry = emptyArray(),
            )
        )
    }
}

/** Creates one full semantic diff node consumed by the production helper. */
private fun productionNode(
    id: Int,
    role: SemanticRole,
    state: SemanticNodeState,
    content: SemanticNodeContent,
    headingLevel: Int,
    bounds: FloatArray,
    parentId: Int,
    siblingIndex: Int,
): SemanticsDiffNode {
    val flags = state.toRawSemanticFlags(role)
    return SemanticsDiffNode(
        id = id,
        role = role.value,
        label = content.label.orEmpty(),
        value = content.value.orEmpty(),
        hint = content.hint.orEmpty(),
        stateFlags = flags.stateFlags,
        traitFlags = flags.traitFlags,
        headingLevel = headingLevel,
        minX = bounds[0],
        minY = bounds[1],
        maxX = bounds[2],
        maxY = bounds[3],
        parentId = parentId,
        siblingIndex = siblingIndex,
    )
}

/** Reconstructs raw Rive flags from classified test state without bypassing production mapping. */
private fun SemanticNodeState.toRawSemanticFlags(role: SemanticRole): RawSemanticFlags {
    var traits = 0
    var states = 0

    expanded?.let {
        traits = traits or SemanticTrait.Expandable
        if (it) states = states or SemanticState.Expanded
    }
    selected?.let {
        traits = traits or SemanticTrait.Selectable
        if (it) states = states or SemanticState.Selected
    }
    toggleState?.let { toggleState ->
        if (role == SemanticRole.SwitchControl) {
            traits = traits or SemanticTrait.Toggleable
            if (toggleState == SemanticToggleState.On) states = states or SemanticState.Toggled
        } else {
            traits = traits or SemanticTrait.Checkable
            when (toggleState) {
                SemanticToggleState.Off -> Unit
                SemanticToggleState.On -> states = states or CHECK_STATE_CHECKED_FLAGS
                SemanticToggleState.Mixed -> states = states or CHECK_STATE_MIXED_FLAGS
            }
        }
    }
    required?.let {
        traits = traits or SemanticTrait.Requirable
        if (it) states = states or SemanticState.Required
    }
    enabled?.let {
        traits = traits or SemanticTrait.Enablable
        if (!it) states = states or SemanticState.Disabled
    }
    focused?.let {
        traits = traits or SemanticTrait.Focusable
        if (it) states = states or SemanticState.Focused
    }
    if (hidden) states = states or SemanticState.Hidden
    if (liveRegion) states = states or SemanticState.LiveRegion
    if (readOnly) states = states or SemanticState.ReadOnly
    if (modal) states = states or SemanticState.Modal
    if (obscured) states = states or SemanticState.Obscured
    if (multiline) states = states or SemanticState.Multiline

    return RawSemanticFlags(traitFlags = traits, stateFlags = states)
}

private const val FIRST_RIVE_NODE_ID = 101
private const val SECOND_RIVE_NODE_ID = 202
private const val REPLACEMENT_RIVE_NODE_ID = 303
private const val STRUCTURAL_RIVE_NODE_ID = 404
private const val FIRST_NODE_LABEL = "Production first virtual button"
private const val SECOND_NODE_LABEL = "Production second virtual button"
private const val UPDATED_FIRST_NODE_LABEL = "Updated production first virtual button"
private const val REPLACEMENT_NODE_LABEL = "Replacement production virtual button"
private const val STRUCTURAL_NODE_LABEL = "Added after structural refresh"
private const val PRODUCTION_SIZE_DP = 200
private const val PRODUCTION_SIZE_PX = 200
private const val CLIPPING_OVERFLOW_PX = 25
private const val CLIPPING_INSET_PX = 10
private const val ACCESSIBILITY_TIMEOUT_MILLIS = 5_000L
private const val MAX_TEST_VIRTUAL_NODE_ID = 10
private const val CHECK_STATE_CHECKED_FLAGS = 1 shl 2
private const val CHECK_STATE_MIXED_FLAGS = 2 shl 2

/** Neutral enabled state used by production test nodes without an injected state. */
private val DEFAULT_NODE_STATE = mapSemanticNodeState(traitFlags = 0, stateFlags = 0)

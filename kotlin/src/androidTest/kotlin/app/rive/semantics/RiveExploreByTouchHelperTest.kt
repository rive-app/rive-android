package app.rive.semantics

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import androidx.core.view.accessibility.AccessibilityEventCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeProviderCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.RiveTextureView
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Exercises the production virtual-node helper against Android framework node objects. */
@RunWith(AndroidJUnit4::class)
class RiveExploreByTouchHelperTest {
    /** Verifies every role reaches the provider with its explicit Android class decision. */
    @Test
    fun semanticRoles_applyCompleteAndroidClassNameMatrix() = onMainThread {
        val roles = SemanticRole.entries
        val tree = semanticTreeOf(
            *roles.mapIndexed { index, role ->
                node(
                    id = ROLE_MATRIX_RIVE_NODE_ID_BASE + index,
                    siblingIndex = index,
                    role = role,
                    label = "Role ${role.name}",
                    minY = index * ROLE_MATRIX_NODE_HEIGHT,
                    maxX = HOST_SIZE.toFloat(),
                    maxY = (index + 1) * ROLE_MATRIX_NODE_HEIGHT,
                )
            }.toTypedArray()
        )
        val host = laidOutHost(height = (roles.size * ROLE_MATRIX_NODE_HEIGHT).toInt())
        val helper = RiveExploreByTouchHelper(host, tree, onSemanticAction = { _, _ -> })
        val provider = helper.getAccessibilityNodeProvider(host)

        roles.forEach { role ->
            val virtualNodeId = assertNotNull(
                provider.virtualIdWithAccessibleLabel("Role ${role.name}")
            )
            val node = assertNotNull(provider.createAccessibilityNodeInfo(virtualNodeId))
            assertEquals(
                role.toAndroidAccessibilityClassName() ?: View::class.java.name,
                node.className.toString(),
                "Unexpected Android class for $role",
            )
        }
    }

    /** Verifies real tree data drives hierarchy, node properties, and semantic actions. */
    @Suppress("DEPRECATION") // ExploreByTouchHelper still consumes parent-local bounds.
    @Test
    fun currentTree_populatesQueryableVirtualHierarchy() = onMainThread {
        val tree = semanticTreeOf(
            node(
                id = PARENT_RIVE_NODE_ID,
                role = SemanticRole.Group,
                label = "Controls",
                maxX = 100f,
                maxY = 100f,
            ),
            node(
                id = CHILD_RIVE_NODE_ID,
                parentId = PARENT_RIVE_NODE_ID,
                role = SemanticRole.Button,
                label = CHILD_LABEL,
                traitFlags = SemanticTrait.Focusable,
                minX = 20f,
                minY = 30f,
                maxX = 80f,
                maxY = 90f,
            ),
        )
        val dispatchedActions = mutableListOf<Pair<Int, SemanticActionType>>()
        val host = laidOutHost()
        val helper = RiveExploreByTouchHelper(
            host = host,
            tree = tree,
            onSemanticAction = { riveNodeId, action ->
                dispatchedActions += riveNodeId to action
            },
        )
        val provider = helper.getAccessibilityNodeProvider(host)

        val hostNode = assertNotNull(
            provider.createAccessibilityNodeInfo(AccessibilityNodeProviderCompat.HOST_VIEW_ID)
        )
        val parentNode = assertNotNull(provider.createAccessibilityNodeInfo(PARENT_VIRTUAL_NODE_ID))
        val childNode = assertNotNull(provider.createAccessibilityNodeInfo(CHILD_VIRTUAL_NODE_ID))
        val childBounds = Rect()
        childNode.getBoundsInParent(childBounds)

        assertEquals(1, hostNode.childCount)
        assertEquals(1, parentNode.childCount)
        assertEquals("android.widget.Button", childNode.className)
        assertEquals(CHILD_LABEL, childNode.contentDescription)
        assertEquals(Rect(20, 30, 80, 90), childBounds)
        assertFalse(childNode.isFocusable)
        assertTrue(
            provider.performAction(
                CHILD_VIRTUAL_NODE_ID,
                AccessibilityNodeInfoCompat.ACTION_CLICK,
                null,
            )
        )
        assertEquals(
            listOf(CHILD_RIVE_NODE_ID to SemanticActionType.Tap),
            dispatchedActions,
        )
    }

    /** Verifies a provider query closes the tree/projection update window before resolving IDs. */
    @Test
    fun removedNode_isRejectedBeforeVersionCollectorSynchronizes() = onMainThread {
        val tree = semanticTreeOf(
            node(
                id = PARENT_RIVE_NODE_ID,
                siblingIndex = 0,
                role = SemanticRole.Button,
                label = "Remove me",
                maxX = 100f,
                maxY = 50f,
            ),
            node(
                id = CHILD_RIVE_NODE_ID,
                siblingIndex = 1,
                role = SemanticRole.Button,
                label = "Keep me",
                minY = 50f,
                maxX = 100f,
                maxY = 100f,
            )
        )
        val dispatchedActions = mutableListOf<SemanticActionType>()
        val host = laidOutHost()
        val helper = RiveExploreByTouchHelper(
            host = host,
            tree = tree,
            onSemanticAction = { _, action -> dispatchedActions += action },
        )
        val provider = helper.getAccessibilityNodeProvider(host)
        assertNotNull(provider.createAccessibilityNodeInfo(PARENT_VIRTUAL_NODE_ID))
        assertNotNull(provider.createAccessibilityNodeInfo(CHILD_VIRTUAL_NODE_ID))

        tree.applyDiff(diff(removed = intArrayOf(PARENT_RIVE_NODE_ID)))

        // This cached-ID query arrives before the normal version collector explicitly synchronizes.
        assertNull(provider.createAccessibilityNodeInfo(PARENT_VIRTUAL_NODE_ID))
        assertNotNull(provider.createAccessibilityNodeInfo(CHILD_VIRTUAL_NODE_ID))
        assertFalse(helper.synchronizeWithTree())
        assertFalse(
            provider.performAction(
                PARENT_VIRTUAL_NODE_ID,
                AccessibilityNodeInfoCompat.ACTION_CLICK,
                null,
            )
        )
        assertTrue(dispatchedActions.isEmpty())
        assertNotNull(
            provider.createAccessibilityNodeInfo(AccessibilityNodeProviderCompat.HOST_VIEW_ID)
        )
    }

    /** Verifies focus gain, direct transition, and exit are reported as atomic Rive-ID changes. */
    @Test
    fun accessibilityFocusActions_reportAtomicRiveNodeTransitions() =
        withTouchExplorationEnabled {
            onMainThread {
                val tree = semanticTreeOf(
                    node(
                        id = PARENT_RIVE_NODE_ID,
                        siblingIndex = 0,
                        role = SemanticRole.Button,
                        label = "First",
                        traitFlags = SemanticTrait.Focusable,
                        maxX = 50f,
                        maxY = 100f,
                    ),
                    node(
                        id = CHILD_RIVE_NODE_ID,
                        siblingIndex = 1,
                        role = SemanticRole.Button,
                        label = "Second",
                        traitFlags = SemanticTrait.Focusable,
                        minX = 50f,
                        maxX = 100f,
                        maxY = 100f,
                    ),
                )
                val transitions = mutableListOf<SemanticAccessibilityFocusTransition>()
                val semanticFocusRequests = mutableListOf<Int>()
                var semanticFocusClearCount = 0
                val host = laidOutHost()
                val helper = RiveExploreByTouchHelper(
                    host = host,
                    tree = tree,
                    onSemanticAction = { _, _ -> },
                    onAccessibilityFocusChanged = transitions::add,
                    onSemanticFocusRequested = semanticFocusRequests::add,
                    onSemanticFocusCleared = { semanticFocusClearCount++ },
                )
                val provider = helper.getAccessibilityNodeProvider(host)

                assertTrue(
                    provider.performAction(
                        PARENT_VIRTUAL_NODE_ID,
                        AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS,
                        null,
                    )
                )
                assertTrue(
                    provider.performAction(
                        PARENT_VIRTUAL_NODE_ID,
                        AccessibilityNodeInfoCompat.ACTION_CLICK,
                        null,
                    )
                )
                assertTrue(
                    provider.performAction(
                        CHILD_VIRTUAL_NODE_ID,
                        AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS,
                        null,
                    )
                )
                assertTrue(
                    provider.performAction(
                        CHILD_VIRTUAL_NODE_ID,
                        AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS,
                        null,
                    )
                )

                assertEquals(
                    listOf(
                        SemanticAccessibilityFocusTransition(null, PARENT_RIVE_NODE_ID),
                        SemanticAccessibilityFocusTransition(
                            PARENT_RIVE_NODE_ID,
                            CHILD_RIVE_NODE_ID,
                        ),
                        SemanticAccessibilityFocusTransition(CHILD_RIVE_NODE_ID, null),
                    ),
                    transitions,
                )
                assertEquals(
                    listOf(PARENT_RIVE_NODE_ID, CHILD_RIVE_NODE_ID),
                    semanticFocusRequests,
                )
                assertEquals(1, semanticFocusClearCount)

                tree.applyDiff(diff(removed = intArrayOf(CHILD_RIVE_NODE_ID)))
                assertTrue(helper.synchronizeWithTree())
                assertFalse(
                    provider.performAction(
                        CHILD_VIRTUAL_NODE_ID,
                        AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS,
                        null,
                    )
                )
                assertEquals(3, transitions.size)
                assertEquals(2, semanticFocusRequests.size)
                assertEquals(1, semanticFocusClearCount)
            }
        }

    /** Verifies accessibility focus on a node without the Focusable trait stays Android-only. */
    @Test
    fun nonFocusableNode_doesNotRequestRiveSemanticFocus() =
        withTouchExplorationEnabled {
            onMainThread {
                val tree = semanticTreeOf(
                    node(
                        id = PARENT_RIVE_NODE_ID,
                        role = SemanticRole.Button,
                        label = "Android-only focus",
                        maxX = 100f,
                        maxY = 100f,
                    )
                )
                val semanticFocusRequests = mutableListOf<Int>()
                var semanticFocusClearCount = 0
                val host = laidOutHost()
                val helper = RiveExploreByTouchHelper(
                    host = host,
                    tree = tree,
                    onSemanticAction = { _, _ -> },
                    onSemanticFocusRequested = semanticFocusRequests::add,
                    onSemanticFocusCleared = { semanticFocusClearCount++ },
                )
                val provider = helper.getAccessibilityNodeProvider(host)

                assertTrue(
                    provider.performAction(
                        PARENT_VIRTUAL_NODE_ID,
                        AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS,
                        null,
                    )
                )
                assertTrue(semanticFocusRequests.isEmpty())
                assertEquals(0, semanticFocusClearCount)

                assertTrue(
                    provider.performAction(
                        PARENT_VIRTUAL_NODE_ID,
                        AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS,
                        null,
                    )
                )
                assertTrue(semanticFocusRequests.isEmpty())
                assertEquals(1, semanticFocusClearCount)
            }
        }

    /** Verifies a parented host can retire a focused node while constructing its clear event. */
    @Test
    fun focusedNodeRetirement_parentedHostClearsFocusOnceAndRejectsStaleId() =
        withTouchExplorationEnabled {
            onMainThread {
                val tree = semanticTreeOf(
                    node(
                        id = PARENT_RIVE_NODE_ID,
                        role = SemanticRole.Group,
                        label = "Retired parent",
                        maxX = 100f,
                        maxY = 100f,
                    ),
                    node(
                        id = CHILD_RIVE_NODE_ID,
                        parentId = PARENT_RIVE_NODE_ID,
                        role = SemanticRole.Button,
                        label = "Retire focused node",
                        maxX = 100f,
                        maxY = 100f,
                    )
                )
                val transitions = mutableListOf<SemanticAccessibilityFocusTransition>()
                val host = laidOutHost()
                val sentEvents = mutableListOf<Pair<Int, String?>>()
                val parent = object : FrameLayout(host.context) {
                    override fun requestSendAccessibilityEvent(
                        child: View,
                        event: AccessibilityEvent,
                    ): Boolean {
                        sentEvents += event.eventType to event.contentDescription?.toString()
                        return true
                    }
                }
                parent.addView(host)
                val helper = RiveExploreByTouchHelper(
                    host = host,
                    tree = tree,
                    onSemanticAction = { _, _ -> },
                    onAccessibilityFocusChanged = transitions::add,
                )
                val provider = helper.getAccessibilityNodeProvider(host)
                assertTrue(
                    provider.performAction(
                        CHILD_VIRTUAL_NODE_ID,
                        AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS,
                        null,
                    )
                )

                tree.applyDiff(diff(removed = intArrayOf(PARENT_RIVE_NODE_ID)))
                assertTrue(helper.synchronizeWithTree())
                assertFalse(helper.synchronizeWithTree())

                assertEquals(
                    listOf(
                        SemanticAccessibilityFocusTransition(null, CHILD_RIVE_NODE_ID),
                        SemanticAccessibilityFocusTransition(CHILD_RIVE_NODE_ID, null),
                    ),
                    transitions,
                )
                assertTrue(
                    sentEvents.any { (eventType, contentDescription) ->
                        eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED &&
                            contentDescription == "Retire focused node"
                    }
                )
                assertFalse(
                    provider.performAction(
                        CHILD_VIRTUAL_NODE_ID,
                        AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS,
                        null,
                    )
                )
            }
        }

    /** Verifies initial host installation focuses the first exported modal descendant. */
    @Test
    fun initiallyActiveModal_focusesFirstDescendantWhenInstalled() =
        withTouchExplorationEnabled {
            onMainThread {
                val tree = semanticTreeOf(
                    node(
                        id = MODAL_RIVE_NODE_ID,
                        role = SemanticRole.Dialog,
                        label = MODAL_LABEL,
                        stateFlags = SemanticState.Modal,
                        maxX = 100f,
                        maxY = 100f,
                    ),
                    node(
                        id = MODAL_CONTENT_RIVE_NODE_ID,
                        parentId = MODAL_RIVE_NODE_ID,
                        role = SemanticRole.Button,
                        label = MODAL_CONTENT_LABEL,
                        traitFlags = SemanticTrait.Focusable,
                        maxX = 100f,
                        maxY = 100f,
                    ),
                )
                val transitions = mutableListOf<SemanticAccessibilityFocusTransition>()
                val semanticFocusRequests = mutableListOf<Int>()
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                val parent = RecordingAccessibilityParent(context)
                val host = RiveTextureView(context)
                parent.addView(host)
                parent.layout(0, 0, HOST_SIZE, HOST_SIZE)
                host.layout(0, 0, HOST_SIZE, HOST_SIZE)

                host.installSemantics(
                    tree = tree,
                    onSemanticAction = { _, _ -> },
                    onAccessibilityFocusChanged = transitions::add,
                    onSemanticFocusRequested = semanticFocusRequests::add,
                )

                assertEquals(
                    listOf(
                        SemanticAccessibilityFocusTransition(
                            previousNodeId = null,
                            currentNodeId = MODAL_CONTENT_RIVE_NODE_ID,
                        )
                    ),
                    transitions,
                )
                assertEquals(listOf(MODAL_CONTENT_RIVE_NODE_ID), semanticFocusRequests)

                host.clearSemantics()
                assertEquals(
                    listOf(
                        AccessibilityEventCompat.CONTENT_CHANGE_TYPE_PANE_APPEARED,
                        AccessibilityEventCompat.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED,
                    ),
                    parent.windowStateContentChanges,
                )
            }
        }

    /** Verifies modal entry traps the provider and dismissal restores prior Rive focus. */
    @Test
    fun dynamicModal_restrictsProviderAndRestoresPreviousFocus() =
        withTouchExplorationEnabled {
            onMainThread {
                val tree = semanticTreeOf(
                    node(
                        id = PARENT_RIVE_NODE_ID,
                        role = SemanticRole.Group,
                        label = "Screen",
                        maxX = 100f,
                        maxY = 100f,
                    ),
                    node(
                        id = CHILD_RIVE_NODE_ID,
                        parentId = PARENT_RIVE_NODE_ID,
                        role = SemanticRole.Button,
                        label = CHILD_LABEL,
                        traitFlags = SemanticTrait.Focusable,
                        maxX = 40f,
                        maxY = 40f,
                    ),
                    node(
                        id = MODAL_RIVE_NODE_ID,
                        parentId = PARENT_RIVE_NODE_ID,
                        siblingIndex = 1,
                        role = SemanticRole.Dialog,
                        label = MODAL_LABEL,
                        minX = 10f,
                        minY = 10f,
                        maxX = 90f,
                        maxY = 90f,
                    ),
                    node(
                        id = MODAL_CONTENT_RIVE_NODE_ID,
                        parentId = MODAL_RIVE_NODE_ID,
                        role = SemanticRole.Button,
                        label = MODAL_CONTENT_LABEL,
                        traitFlags = SemanticTrait.Focusable,
                        minX = 20f,
                        minY = 20f,
                        maxX = 80f,
                        maxY = 80f,
                    ),
                )
                val transitions = mutableListOf<SemanticAccessibilityFocusTransition>()
                val semanticFocusRequests = mutableListOf<Int>()
                var semanticFocusClearCount = 0
                val host = laidOutHost()
                val helper = RiveExploreByTouchHelper(
                    host = host,
                    tree = tree,
                    onSemanticAction = { _, _ -> },
                    onAccessibilityFocusChanged = transitions::add,
                    onSemanticFocusRequested = semanticFocusRequests::add,
                    onSemanticFocusCleared = { semanticFocusClearCount++ },
                )
                val provider = helper.getAccessibilityNodeProvider(host)
                val backgroundVirtualNodeId = assertNotNull(
                    provider.virtualIdWithAccessibleLabel(CHILD_LABEL)
                )
                assertTrue(
                    provider.performAction(
                        backgroundVirtualNodeId,
                        AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS,
                        null,
                    )
                )

                tree.applyDiff(
                    diff(
                        updatedSemantic = arrayOf(
                            node(
                                id = MODAL_RIVE_NODE_ID,
                                parentId = PARENT_RIVE_NODE_ID,
                                siblingIndex = 1,
                                role = SemanticRole.Dialog,
                                label = MODAL_LABEL,
                                stateFlags = SemanticState.Modal,
                                minX = 10f,
                                minY = 10f,
                                maxX = 90f,
                                maxY = 90f,
                            )
                        )
                    )
                )
                assertTrue(helper.synchronizeWithTree())

                assertNull(provider.virtualIdWithAccessibleLabel(CHILD_LABEL))
                val modalVirtualNodeId = assertNotNull(
                    provider.virtualIdWithAccessibleLabel(MODAL_LABEL)
                )
                val modalNode = assertNotNull(
                    provider.createAccessibilityNodeInfo(modalVirtualNodeId)
                )
                assertEquals(MODAL_LABEL, modalNode.paneTitle)
                val modalFocus = assertNotNull(
                    provider.findFocus(AccessibilityNodeInfoCompat.FOCUS_ACCESSIBILITY)
                )
                assertEquals(MODAL_CONTENT_LABEL, modalFocus.contentDescription)

                tree.applyDiff(
                    diff(
                        updatedSemantic = arrayOf(
                            node(
                                id = MODAL_RIVE_NODE_ID,
                                parentId = PARENT_RIVE_NODE_ID,
                                siblingIndex = 1,
                                role = SemanticRole.Dialog,
                                label = MODAL_LABEL,
                                minX = 10f,
                                minY = 10f,
                                maxX = 90f,
                                maxY = 90f,
                            )
                        )
                    )
                )
                assertTrue(helper.synchronizeWithTree())

                val restoredBackgroundId = assertNotNull(
                    provider.virtualIdWithAccessibleLabel(CHILD_LABEL)
                )
                val restoredFocus = assertNotNull(
                    provider.findFocus(AccessibilityNodeInfoCompat.FOCUS_ACCESSIBILITY)
                )
                assertEquals(CHILD_LABEL, restoredFocus.contentDescription)
                assertNotNull(provider.createAccessibilityNodeInfo(restoredBackgroundId))
                assertEquals(
                    listOf(
                        SemanticAccessibilityFocusTransition(null, CHILD_RIVE_NODE_ID),
                        SemanticAccessibilityFocusTransition(
                            CHILD_RIVE_NODE_ID,
                            MODAL_CONTENT_RIVE_NODE_ID,
                        ),
                        SemanticAccessibilityFocusTransition(
                            MODAL_CONTENT_RIVE_NODE_ID,
                            CHILD_RIVE_NODE_ID,
                        ),
                    ),
                    transitions,
                )
                assertEquals(
                    listOf(
                        CHILD_RIVE_NODE_ID,
                        MODAL_CONTENT_RIVE_NODE_ID,
                        CHILD_RIVE_NODE_ID,
                    ),
                    semanticFocusRequests,
                )
                assertEquals(0, semanticFocusClearCount)
            }
        }

    /** Verifies modal entry, title changes, and dismissal emit window-like pane events. */
    @Test
    fun dynamicModal_emitsAccessibilityPaneEvents() =
        withTouchExplorationEnabled {
            onMainThread {
                val tree = semanticTreeOf(
                    node(
                        id = MODAL_RIVE_NODE_ID,
                        role = SemanticRole.Dialog,
                        label = MODAL_LABEL,
                        maxX = 100f,
                        maxY = 100f,
                    ),
                    node(
                        id = MODAL_CONTENT_RIVE_NODE_ID,
                        parentId = MODAL_RIVE_NODE_ID,
                        role = SemanticRole.Text,
                        label = MODAL_CONTENT_LABEL,
                        maxX = 100f,
                        maxY = 100f,
                    ),
                )
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                val parent = RecordingAccessibilityParent(context)
                val host = laidOutHost()
                parent.addView(host)
                parent.layout(0, 0, HOST_SIZE, HOST_SIZE)
                host.layout(0, 0, HOST_SIZE, HOST_SIZE)
                val helper = RiveExploreByTouchHelper(
                    host = host,
                    tree = tree,
                    onSemanticAction = { _, _ -> },
                )

                tree.applyDiff(
                    diff(
                        updatedSemantic = arrayOf(
                            node(
                                id = MODAL_RIVE_NODE_ID,
                                role = SemanticRole.Dialog,
                                label = MODAL_LABEL,
                                stateFlags = SemanticState.Modal,
                                maxX = 100f,
                                maxY = 100f,
                            )
                        )
                    )
                )
                assertTrue(helper.synchronizeWithTree())

                tree.applyDiff(
                    diff(
                        updatedSemantic = arrayOf(
                            node(
                                id = MODAL_RIVE_NODE_ID,
                                role = SemanticRole.Dialog,
                                label = UPDATED_MODAL_LABEL,
                                stateFlags = SemanticState.Modal,
                                maxX = 100f,
                                maxY = 100f,
                            )
                        )
                    )
                )
                assertTrue(helper.synchronizeWithTree())

                tree.applyDiff(
                    diff(
                        updatedSemantic = arrayOf(
                            node(
                                id = MODAL_RIVE_NODE_ID,
                                role = SemanticRole.Dialog,
                                label = UPDATED_MODAL_LABEL,
                                maxX = 100f,
                                maxY = 100f,
                            )
                        )
                    )
                )
                assertTrue(helper.synchronizeWithTree())

                assertEquals(
                    listOf(
                        AccessibilityEventCompat.CONTENT_CHANGE_TYPE_PANE_APPEARED,
                        AccessibilityEventCompat.CONTENT_CHANGE_TYPE_PANE_TITLE,
                        AccessibilityEventCompat.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED,
                    ),
                    parent.windowStateContentChanges,
                )
                assertEquals(
                    listOf<String?>(MODAL_LABEL, UPDATED_MODAL_LABEL, UPDATED_MODAL_LABEL),
                    parent.windowStateDescriptions,
                )
            }
        }

    /** Verifies disposal clears focus once and makes a retained provider reject every node. */
    @Test
    fun dispose_clearsFocusOnceAndRetiresProvider() =
        withTouchExplorationEnabled {
            onMainThread {
                val tree = semanticTreeOf(
                    node(
                        id = PARENT_RIVE_NODE_ID,
                        role = SemanticRole.Button,
                        label = "Dispose focused provider",
                        maxX = 100f,
                        maxY = 100f,
                    )
                )
                val transitions = mutableListOf<SemanticAccessibilityFocusTransition>()
                val host = laidOutHost()
                val helper = RiveExploreByTouchHelper(
                    host = host,
                    tree = tree,
                    onSemanticAction = { _, _ -> },
                    onAccessibilityFocusChanged = transitions::add,
                )
                val provider = helper.getAccessibilityNodeProvider(host)
                assertTrue(
                    provider.performAction(
                        PARENT_VIRTUAL_NODE_ID,
                        AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS,
                        null,
                    )
                )

                helper.dispose()
                helper.dispose()

                assertEquals(
                    listOf(
                        SemanticAccessibilityFocusTransition(null, PARENT_RIVE_NODE_ID),
                        SemanticAccessibilityFocusTransition(PARENT_RIVE_NODE_ID, null),
                    ),
                    transitions,
                )
                assertFalse(
                    provider.performAction(
                        PARENT_VIRTUAL_NODE_ID,
                        AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS,
                        null,
                    )
                )
                assertNull(provider.createAccessibilityNodeInfo(PARENT_VIRTUAL_NODE_ID))
            }
        }

    /** Verifies the texture host installs the provider and forwards touch-exploration hover. */
    @Test
    fun textureHost_installsProviderAndForwardsSemanticHover() =
        withTouchExplorationEnabled {
            onMainThread {
                val tree = semanticTreeOf(
                    node(
                        id = PARENT_RIVE_NODE_ID,
                        role = SemanticRole.Button,
                        label = "Hovered",
                        maxX = 100f,
                        maxY = 100f,
                    )
                )
                val host = RiveTextureView(
                    InstrumentationRegistry.getInstrumentation().targetContext
                ).apply {
                    layout(0, 0, HOST_SIZE, HOST_SIZE)
                    installSemantics(
                        tree = tree,
                        onSemanticAction = { _, _ -> },
                        onAccessibilityFocusChanged = {},
                    )
                }
                val now = SystemClock.uptimeMillis()
                val hoverEvent = MotionEvent.obtain(
                    now,
                    now,
                    MotionEvent.ACTION_HOVER_ENTER,
                    50f,
                    50f,
                    0,
                )
                try {
                    assertTrue(host.dispatchSemanticHoverEvent(hoverEvent))
                } finally {
                    hoverEvent.recycle()
                }
            }
        }

    /** Verifies refresh preserves surviving IDs while publishing semantic and structural changes. */
    @Suppress("DEPRECATION") // ExploreByTouchHelper still consumes parent-local bounds.
    @Test
    fun synchronizeWithTree_preservesSurvivingIdsAndRejectsRemovedNodes() = onMainThread {
        val tree = semanticTreeOf(
            node(
                id = PARENT_RIVE_NODE_ID,
                role = SemanticRole.Group,
                label = "First group",
                maxX = 100f,
                maxY = 100f,
            ),
            node(
                id = CHILD_RIVE_NODE_ID,
                parentId = PARENT_RIVE_NODE_ID,
                siblingIndex = 0,
                role = SemanticRole.Button,
                label = CHILD_LABEL,
                maxX = 40f,
                maxY = 40f,
            ),
            node(
                id = SECOND_CHILD_RIVE_NODE_ID,
                parentId = PARENT_RIVE_NODE_ID,
                siblingIndex = 1,
                role = SemanticRole.Button,
                label = "Move me",
                minX = 50f,
                maxX = 90f,
                maxY = 40f,
            ),
            node(
                id = SECOND_PARENT_RIVE_NODE_ID,
                siblingIndex = 1,
                role = SemanticRole.Group,
                label = "Second group",
                minY = 100f,
                maxX = 100f,
                maxY = 200f,
            ),
        )
        val host = laidOutHost(height = 200)
        val helper = RiveExploreByTouchHelper(host, tree, onSemanticAction = { _, _ -> })
        val provider = helper.getAccessibilityNodeProvider(host)
        val retainedVirtualNodeId = CHILD_VIRTUAL_NODE_ID
        val movedVirtualNodeId = SECOND_CHILD_VIRTUAL_NODE_ID

        tree.applyDiff(
            diff(
                moved = arrayOf(
                    node(
                        id = SECOND_CHILD_RIVE_NODE_ID,
                        parentId = SECOND_PARENT_RIVE_NODE_ID,
                        role = SemanticRole.Button,
                        label = "Move me",
                        minX = 10f,
                        minY = 120f,
                        maxX = 50f,
                        maxY = 160f,
                    )
                ),
                childrenUpdated = arrayOf(
                    SemanticsChildrenUpdate(
                        parentId = PARENT_RIVE_NODE_ID,
                        childIds = intArrayOf(CHILD_RIVE_NODE_ID),
                    ),
                    SemanticsChildrenUpdate(
                        parentId = SECOND_PARENT_RIVE_NODE_ID,
                        childIds = intArrayOf(SECOND_CHILD_RIVE_NODE_ID),
                    ),
                ),
                updatedSemantic = arrayOf(
                    node(
                        id = CHILD_RIVE_NODE_ID,
                        parentId = PARENT_RIVE_NODE_ID,
                        role = SemanticRole.Button,
                        label = UPDATED_CHILD_LABEL,
                        maxX = 40f,
                        maxY = 40f,
                    )
                ),
                updatedGeometry = arrayOf(
                    SemanticsBoundsUpdate(
                        id = CHILD_RIVE_NODE_ID,
                        minX = 20f,
                        minY = 30f,
                        maxX = 70f,
                        maxY = 80f,
                    )
                ),
            )
        )
        assertTrue(helper.synchronizeWithTree())

        val retainedNode = assertNotNull(provider.createAccessibilityNodeInfo(retainedVirtualNodeId))
        val movedNode = assertNotNull(provider.createAccessibilityNodeInfo(movedVirtualNodeId))
        val retainedBounds = Rect()
        val movedBounds = Rect()
        retainedNode.getBoundsInParent(retainedBounds)
        movedNode.getBoundsInParent(movedBounds)
        assertEquals(UPDATED_CHILD_LABEL, retainedNode.contentDescription)
        assertEquals(Rect(20, 30, 70, 80), retainedBounds)
        assertEquals(Rect(10, 20, 50, 60), movedBounds)
        assertEquals(1, provider.createAccessibilityNodeInfo(PARENT_VIRTUAL_NODE_ID)?.childCount)
        assertEquals(1, provider.createAccessibilityNodeInfo(SECOND_PARENT_VIRTUAL_NODE_ID)?.childCount)

        tree.applyDiff(diff(removed = intArrayOf(CHILD_RIVE_NODE_ID)))
        assertTrue(helper.synchronizeWithTree())
        assertNull(provider.createAccessibilityNodeInfo(retainedVirtualNodeId))
        assertNotNull(provider.createAccessibilityNodeInfo(movedVirtualNodeId))
    }

    /** Verifies dynamic projection changes preserve survivors and reject every retired ID. */
    @Test
    fun synchronizeWithTree_reconcilesDynamicProjectionTopology() = onMainThread {
        val tree = semanticTreeOf(
            node(
                id = TOPOLOGY_WRAPPER_RIVE_NODE_ID,
                role = SemanticRole.None,
                label = "",
                maxX = 100f,
                maxY = 100f,
            ),
            node(
                id = TOPOLOGY_ACTION_RIVE_NODE_ID,
                parentId = TOPOLOGY_WRAPPER_RIVE_NODE_ID,
                role = SemanticRole.Button,
                label = TOPOLOGY_ACTION_LABEL,
                maxX = 40f,
                maxY = 40f,
            ),
            node(
                id = TOPOLOGY_GROUP_RIVE_NODE_ID,
                parentId = TOPOLOGY_WRAPPER_RIVE_NODE_ID,
                siblingIndex = 1,
                role = SemanticRole.Group,
                label = TOPOLOGY_GROUP_LABEL,
                minX = 50f,
                maxX = 100f,
                maxY = 100f,
            ),
            node(
                id = TOPOLOGY_NESTED_RIVE_NODE_ID,
                parentId = TOPOLOGY_GROUP_RIVE_NODE_ID,
                role = SemanticRole.Button,
                label = TOPOLOGY_NESTED_LABEL,
                minX = 60f,
                minY = 10f,
                maxX = 90f,
                maxY = 40f,
            ),
        )
        val dispatchedActions = mutableListOf<Int>()
        val host = laidOutHost()
        val helper = RiveExploreByTouchHelper(
            host = host,
            tree = tree,
            onSemanticAction = { nodeId, _ -> dispatchedActions += nodeId },
        )
        val provider = helper.getAccessibilityNodeProvider(host)

        val initialActionId = assertNotNull(
            provider.virtualIdWithContentDescription(TOPOLOGY_ACTION_LABEL)
        )
        val initialGroupId = assertNotNull(
            provider.virtualIdWithContentDescription(TOPOLOGY_GROUP_LABEL)
        )
        val initialNestedId = assertNotNull(
            provider.virtualIdWithContentDescription(TOPOLOGY_NESTED_LABEL)
        )
        assertNull(provider.virtualIdWithContentDescription(TOPOLOGY_WRAPPER_LABEL))

        tree.applyDiff(
            diff(
                updatedSemantic = arrayOf(
                    node(
                        id = TOPOLOGY_WRAPPER_RIVE_NODE_ID,
                        role = SemanticRole.None,
                        label = TOPOLOGY_WRAPPER_LABEL,
                        maxX = 100f,
                        maxY = 100f,
                    )
                )
            )
        )
        assertTrue(helper.synchronizeWithTree())
        val wrapperId = assertNotNull(
            provider.virtualIdWithContentDescription(TOPOLOGY_WRAPPER_LABEL)
        )
        assertEquals(
            initialActionId,
            provider.virtualIdWithContentDescription(TOPOLOGY_ACTION_LABEL),
        )
        assertEquals(
            initialGroupId,
            provider.virtualIdWithContentDescription(TOPOLOGY_GROUP_LABEL),
        )
        assertEquals(
            initialNestedId,
            provider.virtualIdWithContentDescription(TOPOLOGY_NESTED_LABEL),
        )

        tree.applyDiff(
            diff(
                updatedSemantic = arrayOf(
                    node(
                        id = TOPOLOGY_WRAPPER_RIVE_NODE_ID,
                        role = SemanticRole.Button,
                        label = TOPOLOGY_WRAPPER_LABEL,
                        maxX = 100f,
                        maxY = 100f,
                    )
                )
            )
        )
        assertTrue(helper.synchronizeWithTree())
        assertEquals(
            wrapperId,
            provider.virtualIdWithContentDescription(TOPOLOGY_WRAPPER_LABEL),
        )
        assertNull(provider.createAccessibilityNodeInfo(initialActionId))
        assertNull(provider.createAccessibilityNodeInfo(initialGroupId))
        assertNull(provider.createAccessibilityNodeInfo(initialNestedId))
        assertFalse(
            provider.performAction(
                initialActionId,
                AccessibilityNodeInfoCompat.ACTION_CLICK,
                null,
            )
        )
        assertTrue(dispatchedActions.isEmpty())

        tree.applyDiff(
            diff(
                updatedGeometry = arrayOf(
                    SemanticsBoundsUpdate(
                        id = TOPOLOGY_WRAPPER_RIVE_NODE_ID,
                        minX = 0f,
                        minY = 0f,
                        maxX = 0f,
                        maxY = 100f,
                    )
                )
            )
        )
        assertTrue(helper.synchronizeWithTree())
        assertNull(provider.createAccessibilityNodeInfo(wrapperId))
        val restoredActionId = assertNotNull(
            provider.virtualIdWithContentDescription(TOPOLOGY_ACTION_LABEL)
        )
        assertTrue(restoredActionId != initialActionId)

        tree.applyDiff(
            diff(
                updatedSemantic = arrayOf(
                    node(
                        id = TOPOLOGY_WRAPPER_RIVE_NODE_ID,
                        role = SemanticRole.None,
                        label = TOPOLOGY_WRAPPER_LABEL,
                        stateFlags = SemanticState.Hidden,
                        maxX = 100f,
                        maxY = 100f,
                    )
                ),
                updatedGeometry = arrayOf(
                    SemanticsBoundsUpdate(
                        id = TOPOLOGY_WRAPPER_RIVE_NODE_ID,
                        minX = 0f,
                        minY = 0f,
                        maxX = 100f,
                        maxY = 100f,
                    )
                ),
            )
        )
        assertTrue(helper.synchronizeWithTree())
        assertNull(provider.virtualIdWithContentDescription(TOPOLOGY_WRAPPER_LABEL))
        assertNull(provider.virtualIdWithContentDescription(TOPOLOGY_ACTION_LABEL))
        assertNull(provider.virtualIdWithContentDescription(TOPOLOGY_GROUP_LABEL))
        assertNull(provider.virtualIdWithContentDescription(TOPOLOGY_NESTED_LABEL))
        assertNull(provider.createAccessibilityNodeInfo(restoredActionId))

        tree.applyDiff(
            diff(
                updatedSemantic = arrayOf(
                    node(
                        id = TOPOLOGY_WRAPPER_RIVE_NODE_ID,
                        role = SemanticRole.None,
                        label = TOPOLOGY_WRAPPER_LABEL,
                        stateFlags = 0,
                        maxX = 100f,
                        maxY = 100f,
                    )
                )
            )
        )
        assertTrue(helper.synchronizeWithTree())
        assertNotNull(provider.virtualIdWithContentDescription(TOPOLOGY_WRAPPER_LABEL))
        assertNotNull(provider.virtualIdWithContentDescription(TOPOLOGY_ACTION_LABEL))
        assertNotNull(provider.virtualIdWithContentDescription(TOPOLOGY_GROUP_LABEL))
        assertNotNull(provider.virtualIdWithContentDescription(TOPOLOGY_NESTED_LABEL))
    }

    /** Executes [block] synchronously on Android's main thread. */
    private fun onMainThread(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    /** Creates a host with deterministic local bounds for provider queries. */
    private fun laidOutHost(height: Int = HOST_SIZE): View {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return View(context).apply { layout(0, 0, HOST_SIZE, height) }
    }

    /** Runs [block] while instrumentation requests Android touch-exploration mode. */
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
            val deadline = System.nanoTime() + ACCESSIBILITY_TIMEOUT_MILLIS * 1_000_000
            while (!accessibilityManager.isTouchExplorationEnabled && System.nanoTime() < deadline) {
                Thread.sleep(10)
            }
            assertTrue(accessibilityManager.isTouchExplorationEnabled)
            uiAutomation.block()
        } finally {
            serviceInfo.flags = originalFlags
            uiAutomation.serviceInfo = serviceInfo
        }
    }
}

/** Builds a semantic tree from one initial addition diff. */
private fun semanticTreeOf(vararg nodes: SemanticsDiffNode): SemanticTreeModel =
    SemanticTreeModel().apply {
        applyDiff(diff(added = arrayOf(*nodes)))
    }

/** Creates a semantic diff node with configurable hierarchy and view-space bounds. */
private fun node(
    id: Int,
    parentId: Int = -1,
    siblingIndex: Int = 0,
    role: SemanticRole,
    label: String,
    stateFlags: Int = 0,
    traitFlags: Int = 0,
    minX: Float = 0f,
    minY: Float = 0f,
    maxX: Float,
    maxY: Float,
): SemanticsDiffNode = SemanticsDiffNode(
    id = id,
    role = role.value,
    label = label,
    value = "",
    hint = "",
    stateFlags = stateFlags,
    traitFlags = traitFlags,
    headingLevel = 0,
    minX = minX,
    minY = minY,
    maxX = maxX,
    maxY = maxY,
    parentId = parentId,
    siblingIndex = siblingIndex,
)

/** Creates a semantic diff containing only additions or removals. */
private fun diff(
    added: Array<SemanticsDiffNode> = emptyArray(),
    removed: IntArray = intArrayOf(),
    moved: Array<SemanticsDiffNode> = emptyArray(),
    childrenUpdated: Array<SemanticsChildrenUpdate> = emptyArray(),
    updatedSemantic: Array<SemanticsDiffNode> = emptyArray(),
    updatedGeometry: Array<SemanticsBoundsUpdate> = emptyArray(),
): SemanticsDiff = SemanticsDiff(
    treeVersion = 1,
    frameNumber = 0,
    rootId = 0,
    removed = removed,
    added = added,
    moved = moved,
    childrenUpdated = childrenUpdated,
    updatedSemantic = updatedSemantic,
    updatedGeometry = updatedGeometry,
)

private const val HOST_SIZE = 100
private const val PARENT_RIVE_NODE_ID = 10
private const val CHILD_RIVE_NODE_ID = 20
private const val SECOND_CHILD_RIVE_NODE_ID = 21
private const val SECOND_PARENT_RIVE_NODE_ID = 30
private const val MODAL_RIVE_NODE_ID = 40
private const val MODAL_CONTENT_RIVE_NODE_ID = 41
private const val PARENT_VIRTUAL_NODE_ID = 0
private const val CHILD_VIRTUAL_NODE_ID = 1
private const val SECOND_CHILD_VIRTUAL_NODE_ID = 2
private const val SECOND_PARENT_VIRTUAL_NODE_ID = 3
private const val CHILD_LABEL = "Play"
private const val UPDATED_CHILD_LABEL = "Updated play"
private const val MODAL_LABEL = "Confirmation"
private const val UPDATED_MODAL_LABEL = "Updated confirmation"
private const val MODAL_CONTENT_LABEL = "Confirm"
private const val TOPOLOGY_WRAPPER_RIVE_NODE_ID = 100
private const val TOPOLOGY_ACTION_RIVE_NODE_ID = 101
private const val TOPOLOGY_GROUP_RIVE_NODE_ID = 102
private const val TOPOLOGY_NESTED_RIVE_NODE_ID = 103
private const val TOPOLOGY_WRAPPER_LABEL = "Dynamic wrapper"
private const val TOPOLOGY_ACTION_LABEL = "Dynamic action"
private const val TOPOLOGY_GROUP_LABEL = "Dynamic group"
private const val TOPOLOGY_NESTED_LABEL = "Dynamic nested action"
private const val ROLE_MATRIX_RIVE_NODE_ID_BASE = 1_000
private const val ROLE_MATRIX_NODE_HEIGHT = 10f
private const val MAX_DYNAMIC_VIRTUAL_NODE_ID = 30
private const val ACCESSIBILITY_TIMEOUT_MILLIS = 5_000L

/** Captures accessibility window-state events requested by its child host. */
private class RecordingAccessibilityParent(context: Context) : ViewGroup(context) {
    val windowStateContentChanges = mutableListOf<Int>()
    val windowStateDescriptions = mutableListOf<String?>()

    /** Records modal window-state events and accepts every child accessibility event. */
    override fun requestSendAccessibilityEvent(
        child: View,
        event: AccessibilityEvent,
    ): Boolean {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            windowStateContentChanges += event.contentChangeTypes
            windowStateDescriptions += event.contentDescription?.toString()
        }
        return true
    }

    /** Lays out each test child to fill this deterministic host parent. */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        for (index in 0 until childCount) {
            getChildAt(index).layout(0, 0, right - left, bottom - top)
        }
    }
}

/** Returns the unique active virtual ID carrying [contentDescription]. */
private fun AccessibilityNodeProviderCompat.virtualIdWithContentDescription(
    contentDescription: String,
): Int? {
    val matches = (0..MAX_DYNAMIC_VIRTUAL_NODE_ID).filter { virtualNodeId ->
        createAccessibilityNodeInfo(virtualNodeId)?.contentDescription?.toString() ==
            contentDescription
    }
    check(matches.size <= 1) { "Expected at most one virtual node named $contentDescription" }
    return matches.singleOrNull()
}

/** Returns the unique active virtual ID carrying [label] as text or content description. */
private fun AccessibilityNodeProviderCompat.virtualIdWithAccessibleLabel(label: String): Int? {
    val matches = (0..MAX_DYNAMIC_VIRTUAL_NODE_ID).filter { virtualNodeId ->
        createAccessibilityNodeInfo(virtualNodeId)?.let { node ->
            node.contentDescription?.toString() == label || node.text?.toString() == label
        } == true
    }
    check(matches.size <= 1) { "Expected at most one virtual node named $label" }
    return matches.singleOrNull()
}

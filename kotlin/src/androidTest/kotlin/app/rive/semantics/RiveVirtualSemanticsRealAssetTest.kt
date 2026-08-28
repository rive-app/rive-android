package app.rive.semantics

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.graphics.Rect
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.Fit
import app.rive.RiveAndroidTest
import app.rive.RiveTextureView
import app.rive.StateMachine
import app.rive.ViewModelInstance
import app.rive.ViewModelSource
import app.rive.compose.awaitWithWallClock
import app.rive.runtime.kotlin.test.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Duration.Companion.milliseconds

/** Exercises the production virtual-node provider with semantic trees produced by real files. */
@RunWith(AndroidJUnit4::class)
class RiveVirtualSemanticsRealAssetTest : RiveAndroidTest() {
    @get:Rule
    val composeRule =
        androidx.compose.ui.test.junit4.createAndroidComposeRule<ComponentActivity>()

    /** Verifies initial tab publication, authored order, state, action dispatch, and refresh. */
    @Test
    fun tabAsset_publishesAndRefreshesVirtualSemantics() = withTouchExplorationEnabled {
        prepareFixture(R.raw.tabtest).use { fixture ->
            setVirtualSemanticsContent(fixture)

            val root = awaitRootContaining(TAB_LABELS)
            val authoredTabOrder = onMainThread {
                val tabList = fixture.tree.roots
                    .mapNotNull(fixture.tree::nodeById)
                    .single { node -> node.role == SemanticRole.TabList.value }
                tabList.children.map { childId ->
                    assertNotNull(fixture.tree.nodeById(childId)).label
                }
            }
            assertEquals(
                authoredTabOrder,
                findDirectChildLabels(root, TAB_LABELS.toSet()),
            )
            assertTrue(assertNotNull(findByLabel(root, ALL_TAB_LABEL)).isSelected)
            assertFalse(assertNotNull(findByLabel(root, PARENT_TAB_LABEL)).isSelected)

            val parentTab = awaitNodeByLabel(PARENT_TAB_LABEL)
            assertTrue(parentTab.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK))

            composeRule.awaitWithWallClock(
                timeoutMessage = { "Parent tab did not become selected through virtual semantics" }
            ) {
                rootInActiveWindow?.let { activeRoot ->
                    findByLabel(activeRoot, PARENT_TAB_LABEL)?.isSelected == true &&
                        findByLabel(activeRoot, ALL_TAB_LABEL)?.isSelected == false
                } == true
            }
        }
    }

    /** Verifies a real semantic action removes collapsed list descendants from the provider. */
    @Test
    fun dropdownAsset_removesCollapsedVirtualDescendants() = withTouchExplorationEnabled {
        prepareFixture(R.raw.data_binding_lists).use { fixture ->
            setVirtualSemanticsContent(fixture)

            val root = awaitRootContaining(listOf(DROPDOWN_LABEL) + FANDOM_LABELS)
            assertEquals(
                listOf(DROPDOWN_LABEL) + FANDOM_LABELS,
                depthFirstLabels(root, setOf(DROPDOWN_LABEL) + FANDOM_LABELS),
            )
            val dropdown = assertNotNull(findByLabel(root, DROPDOWN_LABEL))
            assertEquals(
                AccessibilityNodeInfo.EXPANDED_STATE_FULL,
                AccessibilityNodeInfoCompat.wrap(dropdown).expandedState,
            )

            assertTrue(dropdown.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK))

            composeRule.awaitWithWallClock(
                timeoutMessage = { "Collapsed dropdown retained virtual fandom descendants" }
            ) {
                val activeRoot = rootInActiveWindow ?: return@awaitWithWallClock false
                findByLabel(activeRoot, DROPDOWN_LABEL) != null &&
                    FANDOM_LABELS.none { label -> findByLabel(activeRoot, label) != null }
            }
            val collapsedDropdown = assertNotNull(
                findByLabel(assertNotNull(rootInActiveWindow), DROPDOWN_LABEL)
            )
            assertEquals(
                AccessibilityNodeInfo.EXPANDED_STATE_COLLAPSED,
                AccessibilityNodeInfoCompat.wrap(collapsedDropdown).expandedState,
            )
        }
    }

    /** Verifies Android accessibility focus round-trips through Rive focus and geometry. */
    @Test
    fun focusAsset_synchronizesFocusTransitionsAndScrollGeometry() =
        withTouchExplorationEnabled {
            prepareFixture(R.raw.semantic_list_scroll_focus_fixed).use { fixture ->
                setVirtualSemanticsContent(fixture)

                val root = awaitRootContaining(FOCUS_ITEM_LABELS)
                val initialItems = onMainThread { fixture.tree.focusItemsByLabel() }
                assertEquals(FOCUS_ITEM_LABELS.toSet(), initialItems.keys)
                initialItems.values.forEach { item ->
                    assertTrue(SemanticTrait.has(item.traitFlags, SemanticTrait.Focusable))
                    assertFalse(SemanticState.has(item.stateFlags, SemanticState.Focused))
                }
                val initialMinY = initialItems.mapValues { (_, item) -> item.minY }

                val firstNode = assertNotNull(findByLabel(root, FIRST_FOCUS_ITEM_LABEL))
                assertTrue(
                    firstNode.performAction(
                        AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS
                    )
                )
                awaitFocusedItem(fixture, FIRST_FOCUS_ITEM_LABEL)

                val lastNode = awaitNodeByLabel(LAST_FOCUS_ITEM_LABEL)
                assertTrue(
                    lastNode.performAction(
                        AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS
                    )
                )
                composeRule.awaitWithWallClock(
                    timeoutMessage = { "Rive focus did not move and scroll to the last item" }
                ) {
                    onMainThread {
                        val items = fixture.tree.focusItemsByLabel()
                        items.focusedLabels() == setOf(LAST_FOCUS_ITEM_LABEL) &&
                            FOCUS_ITEM_LABELS.all { label ->
                                val item = items[label] ?: return@onMainThread false
                                item.minY < assertNotNull(initialMinY[label])
                            }
                    }
                }

                val refreshedLastNode = awaitNodeByLabel(LAST_FOCUS_ITEM_LABEL)
                assertTrue(
                    refreshedLastNode.performAction(
                        AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS
                    )
                )
                composeRule.awaitWithWallClock(
                    timeoutMessage = { "Clearing Android focus did not clear Rive semantic focus" }
                ) {
                    onMainThread {
                        fixture.tree.focusItemsByLabel().focusedLabels().isEmpty()
                    }
                }
            }
        }

    /** Verifies real mapped bounds reach Android exactly once across fit and Compose placement. */
    @Test
    fun realBounds_matchViewSpaceTreeAcrossFitsDensityAndHostOffset() =
        withTouchExplorationEnabled {
            val cases = listOf(
                BoundsCase("contain", Fit.Contain()),
                BoundsCase("cover", Fit.Cover()),
                BoundsCase("fill", Fit.Fill),
            ).map { boundsCase ->
                boundsCase to prepareFixture(
                    resourceId = R.raw.tabtest,
                    fit = boundsCase.fit,
                    surfaceWidth = BOUNDS_HOST_WIDTH_PX.toFloat(),
                    surfaceHeight = BOUNDS_HOST_HEIGHT_PX.toFloat(),
                )
            }
            val boundsByFit = linkedMapOf<String, Rect>()
            val hostsByFit = ConcurrentHashMap<String, RiveTextureView>()
            try {
                composeRule.setContent {
                    CompositionLocalProvider(LocalDensity provides Density(TEST_DENSITY)) {
                        Column {
                            cases.forEach { (boundsCase, fixture) ->
                                VirtualSemanticsHost(
                                    fixture = fixture,
                                    hostWidthPx = BOUNDS_HOST_WIDTH_PX,
                                    hostHeightPx = BOUNDS_HOST_HEIGHT_PX,
                                    hostOffsetXPx = BOUNDS_HOST_OFFSET_X_PX,
                                    hostOffsetYPx = BOUNDS_HOST_OFFSET_Y_PX,
                                    onHostInstalled = { host ->
                                        hostsByFit[boundsCase.name] = host
                                    },
                                )
                            }
                        }
                    }
                }
                composeRule.waitUntil(timeoutMillis = SEMANTICS_TIMEOUT_MILLIS) {
                    hostsByFit.size == cases.size
                }
                val frameworkNodes = awaitNodesByLabel(ALL_TAB_LABEL, cases.size)

                cases.forEach { (boundsCase, fixture) ->
                    val host = assertNotNull(hostsByFit[boundsCase.name])
                    val treeNode = onMainThread {
                        assertNotNull(fixture.tree.nodeByLabel(ALL_TAB_LABEL))
                    }
                    val hostLocation = IntArray(2)
                    onMainThread {
                        assertEquals(BOUNDS_HOST_WIDTH_PX, host.width)
                        assertEquals(BOUNDS_HOST_HEIGHT_PX, host.height)
                        host.getLocationOnScreen(hostLocation)
                    }
                    val hostScreenBounds = Rect(
                        hostLocation[0],
                        hostLocation[1],
                        hostLocation[0] + host.width,
                        hostLocation[1] + host.height,
                    )
                    val frameworkScreenBounds = frameworkNodes.single { frameworkNode ->
                        val candidateBounds = Rect().also(frameworkNode::getBoundsInScreen)
                        hostScreenBounds.contains(
                            candidateBounds.centerX(),
                            candidateBounds.centerY(),
                        )
                    }.let { frameworkNode ->
                        Rect().also(frameworkNode::getBoundsInScreen)
                    }
                    val frameworkViewBounds = Rect(frameworkScreenBounds).apply {
                        offset(-hostLocation[0], -hostLocation[1])
                    }

                    assertTrue(hostLocation[0] >= BOUNDS_HOST_OFFSET_X_PX)
                    assertTrue(hostLocation[1] >= BOUNDS_HOST_OFFSET_Y_PX)
                    assertEquals(treeNode.toViewRect(), frameworkViewBounds)
                    boundsByFit[boundsCase.name] = frameworkViewBounds
                }

                assertEquals(3, boundsByFit.values.toSet().size)
            } finally {
                cases.forEach { (_, fixture) -> fixture.close() }
            }
        }

    /** Loads, binds, enables, advances, and drains one real semantic fixture. */
    private fun prepareFixture(
        @RawRes resourceId: Int,
        fit: Fit = Fit.Contain(),
        surfaceWidth: Float = DEFAULT_HOST_SIZE_PX.toFloat(),
        surfaceHeight: Float = DEFAULT_HOST_SIZE_PX.toFloat(),
    ): RealAssetFixture = runBlocking {
        val resources = loadDefaultRiveResources(resourceId)
        val viewModelInstance = ViewModelInstance.create(
            resources.file,
            ViewModelSource.DefaultForArtboard(resources.artboard).defaultInstance(),
        )
        riveWorker.bindViewModelInstance(
            resources.stateMachine.stateMachineHandle,
            viewModelInstance.instanceHandle,
        )
        val tree = onMainThread { resources.stateMachine.semanticTree }
        val initialVersion = tree.versionFlow.value

        resources.stateMachine.enableSemantics()
        advanceAndDrain(
            stateMachine = resources.stateMachine,
            advanceCount = INITIAL_ADVANCE_COUNT,
            fit = fit,
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight,
        )
        withTimeout(SEMANTICS_TIMEOUT_MILLIS) {
            tree.versionFlow.first { version -> version > initialVersion }
        }

        RealAssetFixture(
            stateMachine = resources.stateMachine,
            tree = tree,
            viewModelInstance = viewModelInstance,
            fit = fit,
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight,
        )
    }

    /** Installs the production helper without also publishing the fallback Compose overlay. */
    private fun setVirtualSemanticsContent(
        fixture: RealAssetFixture,
        hostWidthPx: Int = DEFAULT_HOST_SIZE_PX,
        hostHeightPx: Int = DEFAULT_HOST_SIZE_PX,
        hostOffsetXPx: Int = 0,
        hostOffsetYPx: Int = 0,
    ): RiveTextureView {
        var installedHost: RiveTextureView? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(TEST_DENSITY)) {
                VirtualSemanticsHost(
                    fixture = fixture,
                    hostWidthPx = hostWidthPx,
                    hostHeightPx = hostHeightPx,
                    hostOffsetXPx = hostOffsetXPx,
                    hostOffsetYPx = hostOffsetYPx,
                    onHostInstalled = { host -> installedHost = host },
                )
            }
        }
        return composeRule.runOnIdle { assertNotNull(installedHost) }
    }

    /** Publishes one fixture through a production texture host inside the current composition. */
    @Composable
    private fun VirtualSemanticsHost(
        fixture: RealAssetFixture,
        hostWidthPx: Int,
        hostHeightPx: Int,
        hostOffsetXPx: Int,
        hostOffsetYPx: Int,
        onHostInstalled: (RiveTextureView) -> Unit,
    ) {
        val treeVersion by fixture.tree.versionFlow.collectAsState()
        with(LocalDensity.current) {
            Box(
                modifier = Modifier.padding(
                    start = hostOffsetXPx.toFloat().toDp(),
                    top = hostOffsetYPx.toFloat().toDp(),
                )
            ) {
                AndroidView(
                    modifier = Modifier.requiredSize(
                        hostWidthPx.toFloat().toDp(),
                        hostHeightPx.toFloat().toDp(),
                    ),
                    factory = { context ->
                        RiveTextureView(context).apply {
                            isOpaque = false
                            installSemantics(
                                tree = fixture.tree,
                                onSemanticAction = { nodeId, action ->
                                    fixture.stateMachine.fireSemanticAction(nodeId, action)
                                    advanceAndDrain(fixture, ACTION_ADVANCE_COUNT)
                                },
                                onAccessibilityFocusChanged = {},
                                onSemanticFocusRequested = { nodeId ->
                                    fixture.stateMachine.requestSemanticFocus(nodeId)
                                    advanceAndDrain(fixture, ACTION_ADVANCE_COUNT)
                                },
                                onSemanticFocusCleared = {
                                    fixture.stateMachine.clearSemanticFocus()
                                    advanceAndDrain(fixture, ACTION_ADVANCE_COUNT)
                                },
                            )
                        }.also(onHostInstalled)
                    },
                    update = { host -> synchronizeHostAtVersion(host, treeVersion) },
                )
            }
        }
    }

    /** Advances with nonzero elapsed time and drains the resulting view-space semantic diff. */
    private fun advanceAndDrain(
        stateMachine: StateMachine,
        advanceCount: Int,
        fit: Fit,
        surfaceWidth: Float,
        surfaceHeight: Float,
    ) {
        repeat(advanceCount) {
            stateMachine.advance(ADVANCE_DURATION)
        }
        stateMachine.drainSemanticsDiff(
            fit = fit,
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight,
        )
    }

    /** Advances and drains using the view-space parameters owned by [fixture]. */
    private fun advanceAndDrain(fixture: RealAssetFixture, advanceCount: Int) {
        advanceAndDrain(
            stateMachine = fixture.stateMachine,
            advanceCount = advanceCount,
            fit = fixture.fit,
            surfaceWidth = fixture.surfaceWidth,
            surfaceHeight = fixture.surfaceHeight,
        )
    }

    /** Synchronizes the host when Compose observes [treeVersion]. */
    private fun synchronizeHostAtVersion(host: RiveTextureView, treeVersion: Int) {
        require(treeVersion >= 0)
        host.synchronizeSemantics()
    }

    /** Runs [block] synchronously on Android's main thread and returns its result. */
    private fun <T> onMainThread(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = runCatching(block)
        }
        return checkNotNull(result).getOrThrow()
    }

    /** Waits for an active root containing every requested accessible label. */
    private fun UiAutomation.awaitRootContaining(labels: Collection<String>): AccessibilityNodeInfo {
        var result: AccessibilityNodeInfo? = null
        composeRule.awaitWithWallClock(
            timeoutMillis = SEMANTICS_TIMEOUT_MILLIS,
            timeoutMessage = { "Virtual semantics did not publish labels $labels" },
        ) {
            val root = rootInActiveWindow ?: return@awaitWithWallClock false
            if (labels.all { label -> findByLabel(root, label) != null }) {
                result = root
                true
            } else {
                false
            }
        }
        return assertNotNull(result)
    }

    /** Waits for one active virtual node with [label]. */
    private fun UiAutomation.awaitNodeByLabel(label: String): AccessibilityNodeInfo =
        assertNotNull(findByLabel(awaitRootContaining(listOf(label)), label))

    /** Waits for exactly [count] framework nodes carrying [label]. */
    private fun UiAutomation.awaitNodesByLabel(
        label: String,
        count: Int,
    ): List<AccessibilityNodeInfo> {
        var result = emptyList<AccessibilityNodeInfo>()
        composeRule.awaitWithWallClock(
            timeoutMessage = { "Virtual providers did not publish $count nodes named $label" }
        ) {
            val root = rootInActiveWindow ?: return@awaitWithWallClock false
            result = findAllByLabel(root, label)
            result.size == count
        }
        return result
    }

    /** Waits until [fixture] reports [label] as its only Rive-focused list item. */
    private fun awaitFocusedItem(fixture: RealAssetFixture, label: String) {
        composeRule.awaitWithWallClock(
            timeoutMessage = { "Rive semantic focus did not move to $label" }
        ) {
            onMainThread {
                fixture.tree.focusItemsByLabel().focusedLabels() == setOf(label)
            }
        }
    }

    /** Finds the first node whose text or content description equals [label]. */
    private fun findByLabel(root: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (node.accessibleLabel() == label) {
                return node
            }
            repeat(node.childCount) { index -> node.getChild(index)?.let(pending::add) }
        }
        return null
    }

    /** Finds every node whose text or content description equals [label]. */
    private fun findAllByLabel(
        root: AccessibilityNodeInfo,
        label: String,
    ): List<AccessibilityNodeInfo> = buildList {
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (node.accessibleLabel() == label) {
                add(node)
            }
            repeat(node.childCount) { index -> node.getChild(index)?.let(pending::add) }
        }
    }

    /** Finds authored direct-child label order on the container that owns [labels]. */
    private fun findDirectChildLabels(
        root: AccessibilityNodeInfo,
        labels: Set<String>,
    ): List<String> {
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            val childLabels = buildList {
                repeat(node.childCount) { index ->
                    node.getChild(index)?.let { child ->
                        child.accessibleLabel()?.let(::add)
                        pending.add(child)
                    }
                }
            }
            if (childLabels.toSet() == labels) {
                return childLabels
            }
        }
        return emptyList()
    }

    /** Returns matching labels in the provider's depth-first traversal order. */
    private fun depthFirstLabels(
        root: AccessibilityNodeInfo,
        labels: Set<String>,
    ): List<String> = buildList {
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            node.accessibleLabel()?.takeIf(labels::contains)?.let(::add)
            for (index in node.childCount - 1 downTo 0) {
                node.getChild(index)?.let(pending::addFirst)
            }
        }
    }

    /** Returns the user-facing text used to identify this framework node. */
    private fun AccessibilityNodeInfo.accessibleLabel(): String? =
        contentDescription?.toString() ?: text?.toString()

    /** Returns current list-item nodes keyed by their authored labels. */
    private fun SemanticTreeModel.focusItemsByLabel(): Map<String, SemanticNodeData> = buildMap {
        val pending = ArrayDeque<Int>()
        roots.forEach(pending::addLast)
        while (pending.isNotEmpty()) {
            val node = nodeById(pending.removeFirst()) ?: continue
            if (node.role == SemanticRole.ListItem.value) {
                put(node.label, node)
            }
            node.children.forEach(pending::addLast)
        }
    }

    /** Returns labels carrying Rive's current semantic-focused state. */
    private fun Map<String, SemanticNodeData>.focusedLabels(): Set<String> =
        filterValues { node ->
            SemanticState.has(node.stateFlags, SemanticState.Focused)
        }.keys

    /** Finds the current semantic node carrying [label]. */
    private fun SemanticTreeModel.nodeByLabel(label: String): SemanticNodeData? {
        val pending = ArrayDeque<Int>()
        roots.forEach(pending::addLast)
        while (pending.isNotEmpty()) {
            val node = nodeById(pending.removeFirst()) ?: continue
            if (node.label == label) {
                return node
            }
            node.children.forEach(pending::addLast)
        }
        return null
    }

    /** Quantizes view-space semantic bounds as Android does for a root virtual node. */
    private fun SemanticNodeData.toViewRect(): Rect = Rect(
        floor(minOf(minX, maxX)).toInt(),
        floor(minOf(minY, maxY)).toInt(),
        ceil(maxOf(minX, maxX)).toInt(),
        ceil(maxOf(minY, maxY)).toInt(),
    )

    /** Runs a test while `UiAutomation` requests Android touch-exploration mode. */
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
            composeRule.awaitWithWallClock(
                timeoutMessage = { "Touch exploration did not become active" }
            ) {
                accessibilityManager.isTouchExplorationEnabled
            }
            uiAutomation.block()
        } finally {
            serviceInfo.flags = originalFlags
            uiAutomation.serviceInfo = serviceInfo
        }
    }

    /** State-machine resources owned by one real-asset provider test. */
    private data class RealAssetFixture(
        val stateMachine: StateMachine,
        val tree: SemanticTreeModel,
        private val viewModelInstance: ViewModelInstance,
        val fit: Fit,
        val surfaceWidth: Float,
        val surfaceHeight: Float,
    ) : AutoCloseable {
        /** Releases the test-owned default view model instance. */
        override fun close() {
            viewModelInstance.close()
        }
    }

    /** Named fit configuration used by the real view-space bounds matrix. */
    private data class BoundsCase(val name: String, val fit: Fit)

    private companion object {
        const val DEFAULT_HOST_SIZE_PX = 500
        const val BOUNDS_HOST_WIDTH_PX = 640
        const val BOUNDS_HOST_HEIGHT_PX = 360
        const val BOUNDS_HOST_OFFSET_X_PX = 37
        const val BOUNDS_HOST_OFFSET_Y_PX = 53
        const val TEST_DENSITY = 2f
        const val INITIAL_ADVANCE_COUNT = 10
        const val ACTION_ADVANCE_COUNT = 30
        const val SEMANTICS_TIMEOUT_MILLIS = 10_000L
        val ADVANCE_DURATION = 50.milliseconds
        const val ALL_TAB_LABEL = "All"
        const val PARENT_TAB_LABEL = "Parent"
        val TAB_LABELS = listOf(ALL_TAB_LABEL, PARENT_TAB_LABEL, "Child")
        const val DROPDOWN_LABEL = "Select a fandom"
        const val FIRST_FOCUS_ITEM_LABEL = "Element 1"
        const val LAST_FOCUS_ITEM_LABEL = "Element 5"
        val FOCUS_ITEM_LABELS = (1..5).map { index -> "Element $index" }
        val FANDOM_LABELS = listOf(
            "War of the Stars",
            "Scufflestar Galactica",
            "Galaxy Hike",
            "Dino Planet",
        )
    }
}

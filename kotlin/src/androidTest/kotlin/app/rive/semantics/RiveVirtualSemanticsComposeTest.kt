package app.rive.semantics

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.Fit
import app.rive.Result
import app.rive.Rive
import app.rive.RiveAndroidTest
import app.rive.RiveFileSource
import app.rive.RiveSemanticsMode
import app.rive.StateMachine
import app.rive.ViewModelSource
import app.rive.compose.awaitWithWallClock
import app.rive.compose.rememberTestRiveResources
import app.rive.rememberViewModelInstanceResult
import app.rive.runtime.kotlin.test.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/** Exercises the production virtual accessibility projection installed by [Rive]. */
@RunWith(AndroidJUnit4::class)
class RiveVirtualSemanticsComposeTest : RiveAndroidTest() {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Verifies paused initial publication uses Android virtual nodes, not duplicate Compose nodes. */
    @Test
    fun semanticsEnabled_publishesOnlyVirtualAccessibilityNodes() = withTouchExplorationEnabled {
        setPausedTabContent()

        val root = awaitRootContaining(TAB_LABELS)
        assertTrue(assertNotNull(root.findByLabel(ALL_TAB_LABEL)).isSelected)
        assertFalse(assertNotNull(root.findByLabel(PARENT_TAB_LABEL)).isSelected)
        composeRule.onAllNodesWithContentDescription(ALL_TAB_LABEL)
            .fetchSemanticsNodes()
            .let { composeNodes -> assertTrue(composeNodes.isEmpty()) }
    }

    /** Verifies a framework click reaches Rive and refreshes the production virtual hierarchy. */
    @Test
    fun virtualSemanticAction_refreshesPausedRiveContent() = withTouchExplorationEnabled {
        setPausedTabContent()

        val parentTab = assertNotNull(
            awaitRootContaining(TAB_LABELS).findByLabel(PARENT_TAB_LABEL)
        )
        assertTrue(parentTab.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK))

        composeRule.awaitWithWallClock(
            timeoutMessage = { "Virtual semantics did not publish the selected Parent tab" }
        ) {
            rootInActiveWindow?.findByLabel(PARENT_TAB_LABEL)?.isSelected == true
        }
    }

    /** Verifies disabling removes virtual descendants and re-enabling republishes the current tree. */
    @Test
    fun semanticsEnabled_changeRemovesAndRestoresVirtualAccessibilityNodes() =
        withTouchExplorationEnabled {
            val enabled = setPausedTabContent().semanticsEnabled
            awaitRootContaining(TAB_LABELS)

            composeRule.runOnIdle { enabled.value = false }
            composeRule.awaitWithWallClock(
                timeoutMessage = { "Disabling semantics left Rive virtual nodes active" }
            ) {
                rootInActiveWindow?.findByLabel(ALL_TAB_LABEL) == null
            }

            composeRule.runOnIdle { enabled.value = true }
            awaitRootContaining(TAB_LABELS)
        }

    /** Verifies the active frame loop removes and restores the current virtual hierarchy. */
    @Test
    fun semanticsEnabled_changeWhilePlayingRemovesAndRestoresVirtualAccessibilityNodes() =
        withTouchExplorationEnabled {
            val enabled = setRiveContent(resourceId = R.raw.tabtest, playing = true).semanticsEnabled
            assertTrue(
                assertNotNull(
                    awaitRootContaining(TAB_LABELS).findByLabel(ALL_TAB_LABEL)
                ).isSelected
            )

            composeRule.runOnIdle { enabled.value = false }
            composeRule.awaitWithWallClock(
                timeoutMessage = { "Disabling playing semantics left Rive virtual nodes active" }
            ) {
                rootInActiveWindow?.findByLabel(ALL_TAB_LABEL) == null
            }

            composeRule.runOnIdle { enabled.value = true }
            val restoredRoot = awaitRootContaining(TAB_LABELS)
            assertTrue(assertNotNull(restoredRoot.findByLabel(ALL_TAB_LABEL)).isSelected)
        }

    /** Verifies Android accessibility focus requests and clears Rive semantic focus. */
    @Test
    fun accessibilityFocus_synchronizesRiveFocusThroughComposable() =
        withTouchExplorationEnabled {
            val content = setRiveContent(
                resourceId = R.raw.semantic_list_scroll_focus_fixed,
                playing = false,
            )
            val firstItem = assertNotNull(
                awaitRootContaining(FOCUS_ITEM_LABELS).findByLabel(FIRST_FOCUS_ITEM_LABEL)
            )

            assertTrue(
                firstItem.performAction(
                    AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS
                )
            )
            content.advanceAndDrain()
            composeRule.awaitWithWallClock(
                timeoutMessage = { "Android accessibility focus did not request Rive focus" }
            ) {
                content.focusedLabels() == setOf(FIRST_FOCUS_ITEM_LABEL)
            }

            val refreshedItem = assertNotNull(
                assertNotNull(rootInActiveWindow).findByLabel(FIRST_FOCUS_ITEM_LABEL)
            )
            assertTrue(
                refreshedItem.performAction(
                    AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS
                )
            )
            content.advanceAndDrain()
            composeRule.awaitWithWallClock(
                timeoutMessage = { "Clearing Android accessibility focus did not clear Rive focus" }
            ) {
                content.focusedLabels().isEmpty()
            }
        }

    /** Verifies a fixed-size texture resize updates both tree and framework bounds. */
    @Test
    fun textureResize_publishesUpdatedVirtualBounds() = withTouchExplorationEnabled {
        val sizeDp = mutableStateOf(INITIAL_LAYOUT_SIZE_DP)
        val content = RiveContent(mutableStateOf(true))
        composeRule.setContent {
            val size = sizeDp.value
            RiveTestContent(
                content = content,
                resourceId = R.raw.data_binding_lists,
                playing = false,
                fit = Fit.Contain(),
                modifier = Modifier.requiredSize(size.width.dp, size.height.dp),
            )
        }

        val initialHost = awaitHostSize(INITIAL_LAYOUT_SIZE_DP)
        val initialTreeBounds = awaitTreeBounds(content, RESPONSIVE_NODE_LABEL)
        assertEquals(
            initialTreeBounds,
            awaitNodeBoundsInHost(RESPONSIVE_NODE_LABEL, initialHost, initialTreeBounds),
        )

        onMainThread { sizeDp.value = RESIZED_LAYOUT_SIZE_DP }
        val resizedHost = awaitHostSize(RESIZED_LAYOUT_SIZE_DP)
        dispatchSurfaceTextureSizeChanged(resizedHost)
        composeRule.awaitWithWallClock(
            timeoutMessage = { "Texture resize did not update semantic geometry" }
        ) {
            content.treeBounds(RESPONSIVE_NODE_LABEL)?.let { bounds ->
                bounds != initialTreeBounds
            } == true
        }
        val resizedTreeBounds = assertNotNull(content.treeBounds(RESPONSIVE_NODE_LABEL))
        assertNotEquals(initialTreeBounds, resizedTreeBounds)
        assertEquals(
            resizedTreeBounds,
            awaitNodeBoundsInHost(RESPONSIVE_NODE_LABEL, resizedHost, resizedTreeBounds),
        )
    }

    /** Verifies weighted measurement publishes semantics against the final texture dimensions. */
    @Test
    fun weightedHost_publishesVirtualBoundsForFinalMeasuredSize() = withTouchExplorationEnabled {
        val content = RiveContent(mutableStateOf(true))
        composeRule.setContent {
            Column(modifier = Modifier.requiredSize(WEIGHTED_CONTAINER_SIZE_DP.dp)) {
                Spacer(modifier = Modifier.requiredSize(WEIGHTED_HEADER_HEIGHT_DP.dp))
                RiveTestContent(
                    content = content,
                    resourceId = R.raw.data_binding_lists,
                    playing = false,
                    fit = Fit.Layout(),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }

        val expectedSize = IntSize(
            WEIGHTED_CONTAINER_SIZE_DP,
            WEIGHTED_CONTAINER_SIZE_DP - WEIGHTED_HEADER_HEIGHT_DP,
        )
        val host = awaitHostSize(expectedSize)
        val treeBounds = awaitTreeBounds(content, RESPONSIVE_NODE_LABEL)
        assertEquals(
            treeBounds,
            awaitNodeBoundsInHost(RESPONSIVE_NODE_LABEL, host, treeBounds),
        )
    }

    /** Displays the paused tab fixture with default data binding and mutable activation. */
    private fun setPausedTabContent(): RiveContent = setRiveContent(
        resourceId = R.raw.tabtest,
        playing = false,
    )

    /** Displays one real fixture through the production composable. */
    private fun setRiveContent(
        @RawRes resourceId: Int,
        playing: Boolean,
    ): RiveContent {
        val content = RiveContent(mutableStateOf(true))
        composeRule.setContent {
            RiveTestContent(
                content = content,
                resourceId = resourceId,
                playing = playing,
                fit = Fit.Contain(),
                modifier = Modifier.requiredSize(HOST_SIZE_DP.dp),
            )
        }
        return content
    }

    /** Loads and displays one real fixture while publishing its handles to [content]. */
    @Composable
    private fun RiveTestContent(
        content: RiveContent,
        @RawRes resourceId: Int,
        playing: Boolean,
        fit: Fit,
        modifier: Modifier,
    ) {
        val contentResult = rememberTestRiveResources(
            source = RiveFileSource.RawRes.from(resourceId),
            riveWorker = riveWorker,
        ).andThen { resources ->
            rememberViewModelInstanceResult(
                resources.file,
                ViewModelSource.DefaultForArtboard(resources.artboard).defaultInstance(),
            ).map { viewModelInstance -> resources to viewModelInstance }
        }
        if (contentResult !is Result.Success) {
            return
        }

        val (resources, viewModelInstance) = contentResult.value
        val density = LocalDensity.current
        SideEffect {
            content.stateMachine = resources.stateMachine
            content.surfaceSizePx = HOST_SIZE_DP * density.density
        }
        Rive(
            file = resources.file,
            modifier = modifier,
            playing = playing,
            artboard = resources.artboard,
            stateMachine = resources.stateMachine,
            viewModelInstance = viewModelInstance,
            fit = fit,
            semantics = if (content.semanticsEnabled.value) {
                RiveSemanticsMode.On
            } else {
                RiveSemanticsMode.Off
            },
        )
    }

    /** Waits for an active window containing every requested label. */
    private fun UiAutomation.awaitRootContaining(labels: Collection<String>): AccessibilityNodeInfo {
        var result: AccessibilityNodeInfo? = null
        composeRule.awaitWithWallClock(
            timeoutMessage = { "Virtual semantics did not publish labels $labels" }
        ) {
            val root = rootInActiveWindow ?: return@awaitWithWallClock false
            if (labels.all { label -> root.findByLabel(label) != null }) {
                result = root
                true
            } else {
                false
            }
        }
        return assertNotNull(result)
    }

    /** Waits for the production texture host to reach [expectedDp] in physical pixels. */
    private fun awaitHostSize(expectedDp: IntSize): app.rive.RiveTextureView {
        var result: app.rive.RiveTextureView? = null
        var lastSize = IntSize.Zero
        var expectedPx = IntSize.Zero
        composeRule.awaitWithWallClock(
            timeoutMessage = {
                "Rive texture host was $lastSize px; expected $expectedPx px ($expectedDp dp)"
            }
        ) {
            onMainThread {
                val host = composeRule.activity.window.decorView.findRiveTextureView()
                    ?: return@onMainThread false
                val density = composeRule.activity.resources.displayMetrics.density
                expectedPx = IntSize(
                    (expectedDp.width * density).roundToInt(),
                    (expectedDp.height * density).roundToInt(),
                )
                lastSize = IntSize(host.width, host.height)
                if (host.width == expectedPx.width && host.height == expectedPx.height) {
                    result = host
                    true
                } else {
                    false
                }
            }
        }
        return assertNotNull(result)
    }

    /** Delivers the measured host dimensions through the platform surface-size callback. */
    private fun dispatchSurfaceTextureSizeChanged(host: app.rive.RiveTextureView) {
        onMainThread {
            val surfaceTexture = assertNotNull(host.surfaceTexture)
            assertNotNull(host.surfaceTextureListener).onSurfaceTextureSizeChanged(
                surfaceTexture,
                host.width,
                host.height,
            )
        }
    }

    /** Waits until [content]'s maintained tree contains view-space bounds for [label]. */
    private fun awaitTreeBounds(content: RiveContent, label: String): Rect {
        var result: Rect? = null
        composeRule.awaitWithWallClock(
            timeoutMessage = { "Semantic tree did not publish bounds for $label" }
        ) {
            content.treeBounds(label)?.let { bounds ->
                result = bounds
                true
            } == true
        }
        return assertNotNull(result)
    }

    /** Waits until Android publishes [expected] for [label], relative to [host]. */
    private fun UiAutomation.awaitNodeBoundsInHost(
        label: String,
        host: app.rive.RiveTextureView,
        expected: Rect,
    ): Rect {
        var result: Rect? = null
        composeRule.awaitWithWallClock(
            timeoutMessage = { "Android did not publish $expected for $label" }
        ) {
            val node = rootInActiveWindow?.findByLabel(label) ?: return@awaitWithWallClock false
            val screenBounds = Rect().also(node::getBoundsInScreen)
            val hostLocation = IntArray(2)
            onMainThread { host.getLocationOnScreen(hostLocation) }
            val localBounds = Rect(screenBounds).apply {
                offset(-hostLocation[0], -hostLocation[1])
            }
            if (localBounds == expected) {
                result = localBounds
                true
            } else {
                false
            }
        }
        return assertNotNull(result)
    }

    /** Finds the first descendant whose text or content description equals [label]. */
    private fun AccessibilityNodeInfo.findByLabel(label: String): AccessibilityNodeInfo? {
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(this)
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            val accessibleLabel = node.contentDescription?.toString() ?: node.text?.toString()
            if (accessibleLabel == label) {
                return node
            }
            repeat(node.childCount) { index -> node.getChild(index)?.let(pending::addLast) }
        }
        return null
    }

    /** Finds the Rive texture host in this view subtree. */
    private fun View.findRiveTextureView(): app.rive.RiveTextureView? {
        if (this is app.rive.RiveTextureView) {
            return this
        }
        if (this !is ViewGroup) {
            return null
        }
        repeat(childCount) { index ->
            getChildAt(index).findRiveTextureView()?.let { return it }
        }
        return null
    }

    /** Runs [block] while `UiAutomation` requests Android touch-exploration mode. */
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

    /** Runs [block] synchronously on Android's main thread without waiting for Compose idleness. */
    private fun <T> onMainThread(block: () -> T): T {
        var result: kotlin.Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = runCatching(block)
        }
        return assertNotNull(result).getOrThrow()
    }

    private companion object {
        const val HOST_SIZE_DP = 500
        const val ALL_TAB_LABEL = "All"
        const val PARENT_TAB_LABEL = "Parent"
        val TAB_LABELS = listOf(ALL_TAB_LABEL, PARENT_TAB_LABEL, "Child")
        const val RESPONSIVE_NODE_LABEL = "Select a fandom"
        const val FIRST_FOCUS_ITEM_LABEL = "Element 1"
        val FOCUS_ITEM_LABELS = (1..5).map { index -> "Element $index" }
        const val ACTION_ADVANCE_COUNT = 30
        val ACTION_ADVANCE_DURATION = 50.milliseconds
        val INITIAL_LAYOUT_SIZE_DP = IntSize(220, 220)
        val RESIZED_LAYOUT_SIZE_DP = IntSize(320, 180)
        const val WEIGHTED_CONTAINER_SIZE_DP = 300
        const val WEIGHTED_HEADER_HEIGHT_DP = 40
    }

    /** Mutable test handle for one production Rive composition. */
    private inner class RiveContent(val semanticsEnabled: MutableState<Boolean>) {
        var stateMachine: StateMachine? = null
        var surfaceSizePx = 0f

        /** Advances explicitly so this test does not choose the unresolved paused-action policy. */
        fun advanceAndDrain() = onMainThread {
            val activeStateMachine = assertNotNull(stateMachine)
            repeat(ACTION_ADVANCE_COUNT) {
                activeStateMachine.advance(ACTION_ADVANCE_DURATION)
            }
            activeStateMachine.drainSemanticsDiff(
                fit = Fit.Contain(),
                surfaceWidth = surfaceSizePx,
                surfaceHeight = surfaceSizePx,
            )
        }

        /** Returns labels currently carrying the authored focused state. */
        fun focusedLabels(): Set<String> = onMainThread {
            val tree = assertNotNull(stateMachine).semanticTree
            val pending = ArrayDeque<Int>().apply { tree.roots.forEach(::addLast) }
            buildSet {
                while (pending.isNotEmpty()) {
                    val node = tree.nodeById(pending.removeFirst()) ?: continue
                    if (SemanticState.has(node.stateFlags, SemanticState.Focused)) {
                        add(node.label)
                    }
                    node.children.forEach(pending::addLast)
                }
            }
        }

        /** Returns [label]'s normalized view-space bounds from the maintained semantic tree. */
        fun treeBounds(label: String): Rect? = onMainThread {
            val tree = stateMachine?.semanticTree ?: return@onMainThread null
            val pending = ArrayDeque<Int>().apply { tree.roots.forEach(::addLast) }
            while (pending.isNotEmpty()) {
                val node = tree.nodeById(pending.removeFirst()) ?: continue
                if (node.label == label) {
                    return@onMainThread Rect(
                        kotlin.math.floor(minOf(node.minX, node.maxX)).toInt(),
                        kotlin.math.floor(minOf(node.minY, node.maxY)).toInt(),
                        kotlin.math.ceil(maxOf(node.minX, node.maxX)).toInt(),
                        kotlin.math.ceil(maxOf(node.minY, node.maxY)).toInt(),
                    )
                }
                node.children.forEach(pending::addLast)
            }
            null
        }
    }
}

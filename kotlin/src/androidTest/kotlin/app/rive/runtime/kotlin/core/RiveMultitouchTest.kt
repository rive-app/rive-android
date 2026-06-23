package app.rive.runtime.kotlin.core

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.test.R
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MultiTouchDataBindingComposeTest {
    companion object {
        private const val RIVE_TAG = "Rive"
        private const val TIMEOUT = 5_000L
        private lateinit var riveFile: File
        private val OUT_OF_BOUNDS = Offset(-100f, -100f)

        /** Shared by all tests; load once. */
        @JvmStatic
        @BeforeClass
        fun beforeAll() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            Rive.init(context)
            // Load the file once; reused by all tests in this class.
            val fileBytes =
                context.resources.openRawResource(R.raw.multitouch).use { it.readBytes() }
            riveFile = File(fileBytes)
        }
    }

    @get:Rule
    val composeRule = createComposeRule()

    // A reference to the RiveAnimationView if a test needs to toggle properties
    private lateinit var riveViewRef: RiveAnimationView

    @Composable
    private fun MultiTouchDataBindingDemo(
        multiTouchEnabled: Boolean,
        onBindingsReady: (List<TouchTargetBindings>) -> Unit,
    ) {
        AndroidView(
            factory = { context ->
                RiveAnimationView(context).apply {
                    this.multiTouchEnabled = multiTouchEnabled
                    // Cache the view reference
                    riveViewRef = this
                    setRiveFile(
                        riveFile,
                        autoBind = true,
                        // Stretch to fill the view bounds. Useful during corner calculations.
                        fit = Fit.FILL
                    )

                    val vmi = controller.stateMachines.first().viewModelInstance!!
                    val bindings = (1..5).map { n ->
                        val down = vmi.getBooleanProperty("Target $n/Down")
                        val downCount = vmi.getNumberProperty("Target $n/DownCount")
                        TouchTargetBindings(
                            isDown = { down.value },
                            downCount = { downCount.value },
                        )
                    }
                    onBindingsReady(bindings)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag(RIVE_TAG)
        )
    }

    /** Stores the data-binding accessors for a touch target. */
    private data class TouchTargetBindings(
        val isDown: () -> Boolean,
        val downCount: () -> Float,
    )

    /** Stores a touch position and data-binding accessors for a touch target. */
    class TouchTarget(
        val touchPosition: Offset,
        val isDown: () -> Boolean,
        val downCount: () -> Float,
    ) {
        /**
         * Checks how many pointers currently occupy this target.
         *
         * @param expected The expected number of pointers over this target.
         * @return true when the target's down count equals [expected].
         */
        fun hasDownCount(expected: Int): Boolean = downCount() == expected.toFloat()
    }

    /**
     * Wrapper around a list of TouchTarget to provide extra verification helpers while delegating
     * normal list operations.
     */
    class TouchTargets(private val inner: List<TouchTarget>) : AbstractList<TouchTarget>() {
        override val size: Int
            get() = inner.size

        override fun get(index: Int): TouchTarget = inner[index]

        /**
         * @return true if and only if exactly the targets at [indices] are down.
         * @throws IndexOutOfBoundsException if any index is invalid.
         */
        fun isOnlyDown(vararg indices: Int): Boolean {
            if (indices.any { it < 0 || it >= inner.size }) {
                throw IndexOutOfBoundsException("One or more indices out of range: ${indices.joinToString()}")
            }
            val idxSet = indices.toSet()
            return inner.withIndex().all { (i, t) ->
                if (i in idxSet) t.isDown() else !t.isDown()
            }
        }

        /** @return true if the number of targets currently down equals [expected]. */
        fun downCount(expected: Int): Boolean = inner.count { it.isDown() } == expected

        /**
         * Checks whether every target has the same pointer occupancy.
         *
         * @param expected The expected number of pointers over each target.
         * @return true when every target's down count equals [expected].
         */
        fun allHaveDownCount(expected: Int): Boolean = inner.all { it.hasDownCount(expected) }

        /**
         * Checks the pointer occupancy of every target.
         *
         * @param expected The expected pointer count for each target, in target order.
         * @return true when every target's down count equals its corresponding expected count.
         * @throws IllegalArgumentException if [expected] does not contain one count per target.
         */
        fun haveDownCounts(vararg expected: Int): Boolean {
            require(expected.size == size) {
                "Expected ${size} down counts, received ${expected.size}"
            }
            return inner.indices.all { inner[it].hasDownCount(expected[it]) }
        }
    }

    /**
     * DSL wrapper that captures the TouchInjectionScope so we can write e.g. `targets[0].down(0)`.
     */
    class TouchTargetsScope(private val scope: TouchInjectionScope) {
        fun TouchTarget.down(id: Int = 0) = scope.down(id, this.touchPosition)
        fun up(id: Int = 0) = scope.up(id)
        fun moveTo(id: Int = 0, target: TouchTarget) = scope.moveTo(id, target.touchPosition)
    }

    /** Pairs with the above scope DSL, allowing it to run similar to `performTouchInput`. */
    fun SemanticsNodeInteraction.performTouchTargets(
        block: TouchTargetsScope.() -> Unit
    ) = performTouchInput { TouchTargetsScope(this).block() }

    /**
     * Creates the touch targets and returns them along with the Rive node.
     *
     * @param multiTouchEnabled Whether to enable multi-touch on the RiveAnimationView.
     */
    fun createTouchTargets(
        multiTouchEnabled: Boolean = false,
    ): Pair<SemanticsNodeInteraction, TouchTargets> {
        var targetBindings = listOf<TouchTargetBindings>()
        composeRule.setContent {
            MultiTouchDataBindingDemo(
                multiTouchEnabled = multiTouchEnabled,
                onBindingsReady = { targetBindings = it },
            )
        }
        composeRule.waitUntil(TIMEOUT) { targetBindings.size == 5 }
        val node = composeRule.onNodeWithTag(RIVE_TAG)
        val bounds = node.fetchSemanticsNode().boundsInRoot

        /**
         * The Rive file is 5 equally spaced columns. We want the touch locations to hit the center
         * of each. This converts the Rive bounding box and index (0–4) to an Offset for the the
         * touch region within that box.
         */
        fun boundsToTouchOffset(bounds: Rect, index: Int): Offset {
            val width = bounds.width
            val height = bounds.height
            val sliceWidth = width / 5f
            val y = height / 2f // Center of the slice vertically
            val x = sliceWidth * (index + 0.5f) // Center of the slice horizontally
            return Offset(x, y)
        }

        val targets = targetBindings.mapIndexed { index, bindings ->
            val pos = boundsToTouchOffset(bounds, index)
            TouchTarget(
                touchPosition = pos,
                isDown = bindings.isDown,
                downCount = bindings.downCount,
            )
        }

        return node to TouchTargets(targets)
    }

    /**
     * Waits for a Rive-backed condition while synchronously flushing pending state machine work.
     *
     * Compose test synchronization does not know about the legacy Rive render worker. Advancing
     * explicitly makes these assertions independent of Choreographer and device scheduling while
     * retaining a timeout for genuine failures.
     *
     * @param condition The Rive-backed condition to wait for.
     */
    fun ComposeContentTestRule.waitFor(condition: () -> Boolean) =
        waitUntil(TIMEOUT) {
            val renderer = riveViewRef.artboardRenderer ?: return@waitUntil false
            renderer.advance(0f)
            condition()
        }

    /**
     * Moves each pointer directly into the next occupied target and verifies every intermediate
     * occupancy state.
     *
     * @param riveNode The Compose node receiving the injected pointer events.
     * @param targets The touch targets, initially occupied by the pointer with the same index.
     */
    private fun cycleTouchTargets(
        riveNode: SemanticsNodeInteraction,
        targets: TouchTargets,
    ) {
        val expectedCounts = MutableList(targets.size) { 1 }
        targets.indices.forEach { pointerId ->
            val nextIndex = (pointerId + 1) % targets.size
            riveNode.performTouchTargets { moveTo(pointerId, targets[nextIndex]) }
            expectedCounts[pointerId]--
            expectedCounts[nextIndex]++
            composeRule.waitFor {
                targets.haveDownCounts(*expectedCounts.toIntArray())
            }
        }
    }

    @Test
    fun singleTouch_multiTouchDisabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = false)
        riveNode.performTouchTargets { targets[0].down() }
        composeRule.waitFor { targets.isOnlyDown(0) }

        riveNode.performTouchTargets { up() }
        composeRule.waitFor { targets.downCount(0) }
    }

    @Test
    fun singleTouch_multiTouchEnabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = true)
        riveNode.performTouchTargets { targets[0].down() }
        composeRule.waitFor { targets.isOnlyDown(0) }

        riveNode.performTouchTargets { up() }
        composeRule.waitFor { targets.downCount(0) }
    }

    /**
     * Arguably preserves a bug. Since we're listening to ACTION_UP, i.e. the last pointer to lift,
     * but not ACTION_POINTER_UP, lifting the first finger preserves the "down" state of the first
     * target.
     */
    @Test
    fun twoTouches_sameOrder_multiTouchDisabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = false)
        riveNode.performTouchTargets {
            targets[0].down(0)
            targets[1].down(1)
        }
        composeRule.waitFor { targets.isOnlyDown(0) }

        riveNode.performTouchTargets { up(0) }
        // This is the arguably buggy state
        composeRule.waitFor { targets.isOnlyDown(0) }

        riveNode.performTouchTargets { up(1) }
        composeRule.waitFor { targets.downCount(0) }
    }

    /**
     * Similar to the previous test, except this time the user "wiggles" their second finger. This
     * triggers ACTION_MOVE on the second target (without ever having received a down event).
     *
     * @see twoTouches_sameOrder_multiTouchDisabled
     */
    @Test
    fun twoTouches_sameOrder_withMove_multiTouchDisabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = false)
        riveNode.performTouchTargets {
            targets[0].down(0)
            targets[1].down(1)
        }
        composeRule.waitFor { targets.isOnlyDown(0) }

        riveNode.performTouchTargets { up(0) }
        composeRule.waitFor { targets.isOnlyDown(0) }

        // "Wiggle" by moving to itself
        riveNode.performTouchTargets { moveTo(1, targets[1]) }
        composeRule.waitFor { targets.isOnlyDown(1) }

        riveNode.performTouchTargets { up(1) }
        composeRule.waitFor { targets.downCount(0) }
    }

    @Test
    fun fiveSimultaneousTouches_multiTouchDisabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = false)
        riveNode.performTouchTargets {
            (0..4).forEach { targets[it].down(it) }
        }
        composeRule.waitFor { targets.downCount(1) }

        riveNode.performTouchTargets {
            (0..4).forEach { up(it) }
        }
        composeRule.waitFor { targets.downCount(0) }
    }

    @Test
    fun fiveSimultaneousTouches_multiTouchEnabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = true)
        riveNode.performTouchTargets {
            (0..4).forEach { targets[it].down(it) }
        }
        composeRule.waitFor { targets.downCount(5) }

        riveNode.performTouchTargets {
            (0..4).forEach { up(it) }
        }
        composeRule.waitFor { targets.downCount(0) }
    }

    /** Where "tap" means touch down and then up before the next iteration. */
    @Test
    fun fiveSequentialTaps_multiTouchDisabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = false)
        for (i in 0..4) {
            riveNode.performTouchTargets { targets[i].down() }
            composeRule.waitFor { targets.downCount(1) }

            riveNode.performTouchTargets { up() }
            composeRule.waitFor { targets.downCount(0) }
        }
    }

    @Test
    fun fiveSequentialTaps_multiTouchEnabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = true)
        for (i in 0..4) {
            riveNode.performTouchTargets { targets[i].down() }
            composeRule.waitFor { targets.downCount(1) }

            riveNode.performTouchTargets { up() }
            composeRule.waitFor { targets.downCount(0) }
        }
    }

    @Test
    fun fiveSequentialTouches_reverseOrder_multiTouchDisabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = false)
        for (i in 0..4) {
            riveNode.performTouchTargets { targets[i].down(i) }
            composeRule.waitFor { targets.downCount(1) }
        }
        // Lifting in reverse order, the first four non-primary ups should still leave one down
        for (i in 4 downTo 1) {
            riveNode.performTouchTargets { up(i) }
            composeRule.waitFor { targets.downCount(1) }
        }
        riveNode.performTouchTargets { up(0) }
        composeRule.waitFor { targets.downCount(0) }
    }

    /** @see twoTouches_sameOrder_multiTouchDisabled */
    @Test
    fun fiveSequentialTouches_sameOrder_multiTouchDisabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = false)
        for (i in 0..4) {
            riveNode.performTouchTargets { targets[i].down(i) }
            composeRule.waitFor { targets.downCount(1) }
        }
        // Because we listen to ACTION_UP when not multitouch, lifting non-final pointers does not
        // change the state
        for (i in 0..3) {
            riveNode.performTouchTargets { up(i) }
            composeRule.waitFor { targets.downCount(1) }
        }
        riveNode.performTouchTargets { up(4) }
        composeRule.waitFor { targets.downCount(0) }
    }

    @Test
    fun fiveSequentialTouches_multiTouchEnabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = true)
        for (i in 0..4) {
            riveNode.performTouchTargets { targets[i].down(i) }
            composeRule.waitFor { targets.downCount(i + 1) }
        }
        for (i in 4 downTo 0) {
            riveNode.performTouchTargets { up(i) }
            composeRule.waitFor { targets.downCount(i) }
        }
    }

    @Test
    fun twoOverlappingTouches_multiTouchDisabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = false)
        riveNode.performTouchTargets { targets[0].down(0) }
        composeRule.waitFor { targets.downCount(1) }

        // Touch down again on target 0 with a different pointer
        riveNode.performTouchTargets { targets[0].down(1) }
        composeRule.waitFor { targets.downCount(1) }

        riveNode.performTouchTargets { up(1) }
        composeRule.waitFor { targets.downCount(1) }

        riveNode.performTouchTargets { up(0) }
        composeRule.waitFor { targets.downCount(0) }
    }

    @Test
    fun twoOverlappingTouches_multiTouchEnabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = true)
        (0..10).forEach { _ ->
            riveNode.performTouchTargets { targets[0].down(0) }
            composeRule.waitFor { targets[0].hasDownCount(1) }

            // Touch down again on target 0 with a different pointer
            riveNode.performTouchTargets { targets[0].down(1) }
            composeRule.waitFor { targets[0].hasDownCount(2) }

            riveNode.performTouchTargets { up(1) }
            composeRule.waitFor { targets[0].hasDownCount(1) }

            riveNode.performTouchTargets { up(0) }
            composeRule.waitFor { targets[0].hasDownCount(0) }
        }
    }

    @Test
    fun moveTwoTouches_multiTouchDisabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = false)
        riveNode.performTouchTargets {
            targets[0].down(0)
            targets[1].down(1)
        }
        composeRule.waitFor { targets.downCount(1) }

        // Move touches to target 2 and 3
        riveNode.performTouchTargets {
            moveTo(0, targets[2])
            moveTo(1, targets[3])
        }
        // Only target 2 (pointer ID 0) should be down
        composeRule.waitFor { targets.isOnlyDown(2) }
    }

    @Test
    fun moveTwoTouches_multiTouchEnabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = true)
        riveNode.performTouchTargets {
            targets[0].down(0)
            targets[1].down(1)
        }
        composeRule.waitFor { targets.downCount(2) }

        // Move touches to target 2 and 3
        riveNode.performTouchTargets {
            moveTo(0, targets[2])
            moveTo(1, targets[3])
        }
        composeRule.waitFor { targets.isOnlyDown(2, 3) }
    }

    @Test
    fun slide_multiTouchDisabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = false)
        riveNode.performTouchTargets { targets[0].down(0) }
        composeRule.waitFor { targets.isOnlyDown(0) }

        // Slide to last target
        riveNode.performTouchTargets { moveTo(0, targets[4]) }
        composeRule.waitFor { targets.isOnlyDown(4) }

        riveNode.performTouchTargets { up(0) }
        composeRule.waitFor { targets.downCount(0) }

        // Reverse direction
        riveNode.performTouchTargets { targets[4].down(1) }
        composeRule.waitFor { targets.isOnlyDown(4) }

        // Slide to target 0 with pointer ID 1
        riveNode.performTouchTargets { moveTo(1, targets[0]) }
        composeRule.waitFor { targets.isOnlyDown(0) }
    }

    @Test
    fun slide_multiTouchEnabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = true)
        riveNode.performTouchTargets { targets[0].down(0) }
        composeRule.waitFor { targets.isOnlyDown(0) }

        // Slide to last target
        riveNode.performTouchTargets { moveTo(0, targets[4]) }
        composeRule.waitFor { targets.isOnlyDown(4) }

        riveNode.performTouchTargets { up(0) }
        composeRule.waitFor { targets.downCount(0) }

        // Reverse direction
        riveNode.performTouchTargets { targets[4].down(1) }
        composeRule.waitFor { targets.isOnlyDown(4) }

        // Slide to target 0 with pointer ID 1
        riveNode.performTouchTargets { moveTo(1, targets[0]) }
        composeRule.waitFor { targets.isOnlyDown(0) }
    }

    @Test
    fun slideWithMiddleDown_multiTouchEnabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = true)
        // Touch down on target 0 and 2
        riveNode.performTouchTargets {
            targets[0].down(0)
            targets[2].down(1)
        }
        composeRule.waitFor { targets.isOnlyDown(0, 2) }

        // Slide ID 0 to last target
        riveNode.performTouchTargets { moveTo(0, targets[4]) }
        composeRule.waitFor { targets.isOnlyDown(2, 4) }

        riveNode.performTouchTargets { up(0) }
        composeRule.waitFor { targets.downCount(1) }

        // Reverse direction
        riveNode.performTouchTargets { targets[4].down(0) }
        composeRule.waitFor { targets.isOnlyDown(2, 4) }

        // Slide ID 0 to first target
        riveNode.performTouchTargets { moveTo(0, targets[0]) }
        composeRule.waitFor { targets.isOnlyDown(0, 2) }
    }

    @Test
    fun moveTouchExit_multiTouchDisabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = false)
        riveNode.performTouchTargets {
            targets[0].down(0)
            targets[1].down(1)
        }
        composeRule.waitFor { targets.downCount(1) }

        // Move second touch outside bounds
        riveNode.performTouchInput { moveTo(1, OUT_OF_BOUNDS) }
        composeRule.waitFor { targets.downCount(1) }

        // Move first touch outside bounds
        riveNode.performTouchInput { moveTo(0, OUT_OF_BOUNDS) }
        composeRule.waitFor { targets.downCount(0) }
    }

    @Test
    fun moveTouchExit_multiTouchEnabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = true)
        riveNode.performTouchTargets {
            targets[0].down(0)
            targets[1].down(1)
        }
        composeRule.waitFor { targets.downCount(2) }

        // Move first touch outside bounds
        riveNode.performTouchInput { moveTo(0, OUT_OF_BOUNDS) }
        composeRule.waitFor { targets.isOnlyDown(1) }

        // Move second touch outside bounds
        riveNode.performTouchInput { moveTo(1, OUT_OF_BOUNDS) }
        composeRule.waitFor { targets.downCount(0) }
    }

    @Test
    fun moveTouchReEnter_multiTouchDisabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = false)
        riveNode.performTouchTargets {
            targets[0].down(0)
            targets[1].down(1)
        }
        composeRule.waitFor { targets.downCount(1) }

        // Move second touch outside bounds
        riveNode.performTouchInput { moveTo(1, OUT_OF_BOUNDS) }
        composeRule.waitFor { targets.isOnlyDown(0) }

        // Move first touch outside bounds
        riveNode.performTouchInput { moveTo(0, OUT_OF_BOUNDS) }
        composeRule.waitFor { targets.downCount(0) }

        // Move first touch to target 1 (different from original location)
        riveNode.performTouchTargets { moveTo(0, targets[1]) }
        composeRule.waitFor { targets.isOnlyDown(1) }

        // Move second touch back to target 0 (different from original location)
        riveNode.performTouchTargets { moveTo(1, targets[0]) }
        composeRule.waitFor { targets.isOnlyDown(1) }

        riveNode.performTouchTargets {
            up(0)
            up(1)
        }
        composeRule.waitFor { targets.downCount(0) }
    }

    @Test
    fun moveTouchReEnter_multiTouchEnabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = true)
        riveNode.performTouchTargets {
            targets[0].down(0)
            targets[1].down(1)
        }
        composeRule.waitFor { targets.downCount(2) }

        // Move first touch outside bounds
        riveNode.performTouchInput { moveTo(0, OUT_OF_BOUNDS) }
        composeRule.waitFor { targets.isOnlyDown(1) }

        // Move second touch outside bounds
        riveNode.performTouchInput { moveTo(1, OUT_OF_BOUNDS) }
        composeRule.waitFor { targets.downCount(0) }

        // Move first touch to target 1 (different from original location)
        riveNode.performTouchTargets { moveTo(0, targets[1]) }
        composeRule.waitFor { targets.isOnlyDown(1) }

        // Move second touch back to target 0 (different from original location)
        riveNode.performTouchTargets { moveTo(1, targets[0]) }
        composeRule.waitFor { targets.downCount(2) }

        riveNode.performTouchTargets {
            up(0)
            up(1)
        }
        composeRule.waitFor { targets.downCount(0) }
    }

    @Test
    fun reusePointerID_multiTouchDisabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = false)
        riveNode.performTouchTargets { targets[0].down(0) }
        composeRule.waitFor { targets.downCount(1) }

        riveNode.performTouchTargets { up(0) }
        composeRule.waitFor { targets.downCount(0) }

        // Reuse pointer ID 0 on target 1
        riveNode.performTouchTargets { targets[1].down(0) }
        composeRule.waitFor { targets.downCount(1) }

        riveNode.performTouchTargets { up(0) }
        composeRule.waitFor { targets.downCount(0) }
    }

    @Test
    fun reusePointerID_multiTouchEnabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = true)
        riveNode.performTouchTargets { targets[0].down(0) }
        composeRule.waitFor { targets.downCount(1) }

        riveNode.performTouchTargets { up(0) }
        composeRule.waitFor { targets.downCount(0) }

        // Reuse pointer ID 0 on target 1
        riveNode.performTouchTargets { targets[1].down(0) }
        composeRule.waitFor { targets.downCount(1) }

        riveNode.performTouchTargets { up(0) }
        composeRule.waitFor { targets.downCount(0) }
    }

    @Test
    fun non01PointerIDs_multiTouchDisabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = false)
        // Touch down on target 0 with pointer ID 5
        riveNode.performTouchTargets {
            targets[0].down(5)
            targets[1].down(10)
        }
        composeRule.waitFor { targets.downCount(1) }

        riveNode.performTouchTargets {
            up(5)
            up(10)
        }
        composeRule.waitFor { targets.downCount(0) }
    }

    @Test
    fun non01PointerIDs_multiTouchEnabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = true)
        // Touch down on target 0 with pointer ID 5
        riveNode.performTouchTargets {
            targets[0].down(5)
            targets[1].down(10)
        }
        composeRule.waitFor { targets.downCount(2) }

        riveNode.performTouchTargets {
            up(5)
            up(10)
        }
        composeRule.waitFor { targets.downCount(0) }
    }

    @Test
    fun touchCorners_multiTouchDisabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = false)
        val bounds = riveNode.fetchSemanticsNode().boundsInRoot
        riveNode.performTouchInput {
            down(0, bounds.topLeft)
            down(1, bounds.topRight)
            down(2, bounds.bottomLeft)
            down(3, bounds.bottomRight)
        }
        composeRule.waitFor { targets.downCount(1) }
    }

    @Test
    fun touchCorners_multiTouchEnabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = true)
        val bounds = riveNode.fetchSemanticsNode().boundsInRoot
        riveNode.performTouchInput {
            down(0, bounds.topLeft)
            // Touch just inside the bottom right corner
            down(1, bounds.bottomRight - Offset(1f, 1f))
        }
        composeRule.waitFor { targets.isOnlyDown(0, 4) }

        riveNode.performTouchInput {
            up(0)
            up(1)
        }
        composeRule.waitFor { targets.downCount(0) }

        riveNode.performTouchInput {
            // Touch just inside the top right and bottom left corners
            down(0, bounds.topRight - Offset(1f, 0f))
            down(1, bounds.bottomLeft - Offset(0f, 1f))
        }
        composeRule.waitFor { targets.isOnlyDown(0, 4) }
    }

    @Test
    fun cancel_multiTouchDisabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = false)
        riveNode.performTouchTargets {
            targets[0].down(0)
            targets[1].down(1)
        }
        composeRule.waitFor { targets.downCount(1) }

        riveNode.performTouchInput { cancel() }
        composeRule.waitFor { targets.downCount(0) }
    }

    @Test
    fun cancel_multiTouchEnabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = true)
        riveNode.performTouchTargets {
            targets[0].down(0)
            targets[1].down(1)
            targets[2].down(2)
            targets[3].down(3)
            targets[4].down(4)
        }
        composeRule.waitFor { targets.downCount(5) }

        riveNode.performTouchInput { cancel() }
        composeRule.waitFor { targets.downCount(0) }
    }

    @Test
    fun moreThan5Touches_multiTouchEnabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = true)
        riveNode.performTouchTargets {
            // All stacked on target 0
            (0..4).forEach { i -> targets[0].down(i) }
        }
        composeRule.waitFor { targets.downCount(1) }

        // Pointer ID 5 (sixth) on target 1
        riveNode.performTouchTargets { targets[1].down(5) }
        composeRule.waitFor { targets.downCount(2) }

        riveNode.performTouchTargets {
            (0..5).forEach { up(it) }
        }
        composeRule.waitFor { targets.downCount(0) }
    }

    @Test
    fun cycle_multiTouchEnabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = true)
        // Touch down on all targets
        riveNode.performTouchTargets {
            (0..4).forEach { targets[it].down(it) }
        }
        composeRule.waitFor { targets.allHaveDownCount(1) }

        cycleTouchTargets(riveNode, targets)
    }

    @Test
    fun stressTap_multiTouchEnabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = true)
        (1..10).forEach { _ ->
            // Touch down on all targets
            riveNode.performTouchTargets {
                (0..4).forEach { targets[it].down(it) }
            }
            composeRule.waitFor { targets.allHaveDownCount(1) }

            // Lift all touches
            riveNode.performTouchTargets {
                (0..4).forEach { up(it) }
            }
            composeRule.waitFor { targets.allHaveDownCount(0) }
        }
    }

    @Test
    fun stressCycle_multiTouchEnabled() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = true)
        (1..10).forEach { i ->
            Log.i("RiveMultiTouchTest", "Stress iteration $i")
            // Touch down on all targets
            riveNode.performTouchTargets {
                (0..4).forEach { targets[it].down(it) }
            }
            composeRule.waitFor { targets.allHaveDownCount(1) }

            cycleTouchTargets(riveNode, targets)

            // Lift all touches
            riveNode.performTouchTargets {
                (0..4).forEach { up(it) }
            }
            composeRule.waitFor { targets.allHaveDownCount(0) }
        }
    }

    @Test
    fun toggleMultiTouchOff_keepsPrimaryTouchDown() {
        val (riveNode, targets) = createTouchTargets(multiTouchEnabled = true)
        riveNode.performTouchTargets {
            targets[0].down(0)
            targets[1].down(1)
        }
        composeRule.waitFor { targets.downCount(2) }

        // Toggle multi-touch off
        composeRule.runOnUiThread {
            riveViewRef.multiTouchEnabled = false
        }
        // Expect only the primary (pointer ID 0) to remain down
        composeRule.waitFor { targets.isOnlyDown(0) }

        // Clean up: lift both remaining touches in reverse order
        riveNode.performTouchTargets {
            up(1)
            up(0)
        }
        composeRule.waitFor { targets.downCount(0) }
    }
}

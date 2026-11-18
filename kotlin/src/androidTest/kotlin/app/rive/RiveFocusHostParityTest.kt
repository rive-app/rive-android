package app.rive

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.core.CloseableRiveResources
import app.rive.runtime.kotlin.test.R
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Verifies focus behavior parity between [RiveCanvasSession] and the [Rive] composable.
 *
 * Each host sits between two focus stops, focusable elements from its own UI toolkit: views for
 * the canvas host and composables for [Rive]. A scenario only one host supports is skipped for
 * the other.
 */
@RunWith(Parameterized::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
@OptIn(ExperimentalHardwareBitmapRendering::class, ExperimentalRiveFocus::class)
class RiveFocusHostParityTest(private val kind: HostKind) : RiveAndroidTest() {
    /** The host implementations under comparison. */
    enum class HostKind { Canvas, Compose }

    /** The focus stops before and after the host. */
    enum class Stop { Before, After }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private var scenario: ActivityScenario<ComponentActivity>? = null
    private var host: Host? = null

    private lateinit var stateMachine: StateMachine

    private val activeHost: Host
        get() = checkNotNull(host)

    @After
    fun tearDown() {
        // The activity goes first, so its views stop forwarding before the session closes.
        scenario?.close()
        host?.let { createdHost -> instrumentation.runOnMainSync { createdHost.close() } }
    }

    @Test
    fun tabFromThePrecedingStopEntersAtTheFirstNode() {
        show(RiveFocusMode.On)
        focus(Stop.Before)

        pressTab()
        assertTrue(awaitRiveFocus(expected = true), "Tab onto the host did not enter")

        pressTab(shift = true)
        assertTrue(awaitStopFocused(Stop.Before), "Tab entry did not land on the first node")
    }

    @Test
    fun shiftTabFromTheFollowingStopEntersAtTheLastNode() {
        show(RiveFocusMode.On)
        focus(Stop.After)

        pressTab(shift = true)
        assertTrue(awaitRiveFocus(expected = true), "Shift+Tab onto the host did not enter")

        pressTab()
        assertTrue(awaitStopFocused(Stop.After), "Shift+Tab entry did not land on the last node")
    }

    @Test
    fun heldTabAtTheEndLeavesWithoutReentering() {
        show(RiveFocusMode.On)
        focus(Stop.After)
        pressTab(shift = true)
        assertTrue(awaitRiveFocus(expected = true), "Shift+Tab onto the host did not enter")

        // Every repeat lands while the first traversal awaits its result.
        withRiveWorkerDeliveryPaused {
            val downAt = SystemClock.uptimeMillis()
            repeat(HELD_KEY_REPEATS) { repeatCount ->
                instrumentation.sendKeySync(tabEvent(downAt, KeyEvent.ACTION_DOWN, repeatCount))
            }
            instrumentation.sendKeySync(tabEvent(downAt, KeyEvent.ACTION_UP, repeatCount = 0))
        }

        assertTrue(awaitStopFocused(Stop.After), "held Tab did not leave")
        assertTrue(awaitRiveFocus(expected = false), "held Tab re-entered")
    }

    @Test
    fun tapFocusesTheHostWithoutEnteringThenTabEnters() {
        show(RiveFocusMode.On)

        tapHost()
        assertTrue(awaitCondition { onMain { activeHost.hostHasFocus() } }, "tapping did not focus")
        assertEquals(RiveFocusState.Unfocused, stateMachine.focusState.value, "tapping entered")

        pressTab()
        assertTrue(awaitRiveFocus(expected = true), "Tab after a tap did not enter")
    }

    @Test
    fun offSkipsTheHost() {
        show(RiveFocusMode.Off)
        focus(Stop.Before)

        pressTab()

        assertTrue(awaitStopFocused(Stop.After), "Tab stopped on the host")
        assertEquals(RiveFocusState.Unfocused, stateMachine.focusState.value, "Off entered")
    }

    @Test
    fun aFileWithoutFocusNodesIsSkipped() {
        show(RiveFocusMode.On, fixture = R.raw.empty)
        focus(Stop.Before)

        pressTab()

        assertTrue(awaitStopFocused(Stop.After), "Tab stopped on a host without focus nodes")
    }

    @Test
    fun automaticIgnoresATapInTouchMode() {
        show(RiveFocusMode.Automatic)

        tapHost()

        assertTrue(onMain { activeHost.content.isInTouchMode }, "tapping did not enter touch mode")
        assertFalse(onMain { activeHost.hostHasFocus() }, "the host took focus in touch mode")
    }

    @Test
    fun switchingFromOnToAutomaticInTouchModeReleasesFocus() {
        show(RiveFocusMode.On)
        tapHost()
        assertTrue(awaitCondition { onMain { activeHost.hostHasFocus() } }, "tapping did not focus")

        instrumentation.runOnMainSync { activeHost.setFocus(RiveFocusMode.Automatic) }

        assertTrue(onMain { activeHost.content.isInTouchMode }, "the window left touch mode")
        assertTrue(
            awaitCondition { onMain { !activeHost.hostHasFocus() } },
            "the host kept focus in touch mode after switching to Automatic",
        )
    }

    @Test
    fun turningFocusOffWhileFocusedClearsRiveFocus() {
        show(RiveFocusMode.On)
        focus(Stop.Before)
        pressTab()
        assertTrue(awaitRiveFocus(expected = true), "Tab did not enter")

        instrumentation.runOnMainSync { activeHost.setFocus(RiveFocusMode.Off) }

        assertTrue(awaitRiveFocus(expected = false), "Rive kept focus after focus was turned off")
        assertTrue(
            awaitCondition { onMain { !activeHost.hostHasFocus() } },
            "the host kept Android focus after focus was turned off",
        )
    }

    @Test
    fun aDialogOverTheWindowKeepsRiveFocus() {
        show(RiveFocusMode.On)
        focus(Stop.Before)
        pressTab()
        assertTrue(awaitRiveFocus(expected = true), "Tab did not enter")

        var dialog: Dialog? = null
        scenario?.onActivity { activity -> dialog = Dialog(activity).apply { show() } }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync { checkNotNull(dialog).dismiss() }
        instrumentation.waitForIdleSync()

        // The window losing focus is not the user leaving, so Rive keeps its node.
        Thread.sleep(SETTLE_MS)
        assertTrue(stateMachine.focusState.value is RiveFocusState.Focused, "Rive focus cleared")
        assertTrue(onMain { activeHost.hostHasFocus() }, "the host lost Android focus")
    }

    @Test
    fun removingTheHostWhileFocusedClearsRiveFocus() {
        show(RiveFocusMode.On)
        focus(Stop.Before)
        pressTab()
        assertTrue(awaitRiveFocus(expected = true), "Tab did not enter")

        instrumentation.runOnMainSync { activeHost.hide() }

        assertTrue(awaitRiveFocus(expected = false), "Rive kept focus after the host was removed")
    }

    @Test
    fun closingTheCanvasSessionReleasesRiveAndAndroidFocus() {
        // The composable has no close; leaving composition is covered by removing the host.
        assumeTrue(kind == HostKind.Canvas)
        show(RiveFocusMode.On)
        focus(Stop.Before)
        pressTab()
        assertTrue(awaitRiveFocus(expected = true), "Tab did not enter")

        instrumentation.runOnMainSync { activeHost.close() }

        assertTrue(awaitRiveFocus(expected = false), "closing the session left Rive focused")
        assertTrue(
            awaitCondition { onMain { !activeHost.hostHasFocus() } },
            "the host kept Android focus after the session closed",
        )
    }

    @Test
    fun anArrowFromTheStopAboveEntersAtTheFirstNode() {
        show(RiveFocusMode.On, fixture = R.raw.focus_buttons_row)
        focus(Stop.Before)

        pressKey(KeyEvent.KEYCODE_DPAD_DOWN)

        assertTrue(awaitRiveFocus(expected = true), "Down onto the host did not enter")
        pressTab(shift = true)
        assertTrue(awaitStopFocused(Stop.Before), "the arrow entry did not land on the first node")
    }

    @Test
    fun anArrowFromTheStopBelowEntersAtTheLastNode() {
        show(RiveFocusMode.On, fixture = R.raw.focus_buttons_row)
        focus(Stop.After)

        pressKey(KeyEvent.KEYCODE_DPAD_UP)

        assertTrue(awaitRiveFocus(expected = true), "Up onto the host did not enter")
        pressTab()
        assertTrue(awaitStopFocused(Stop.After), "the arrow entry did not land on the last node")
    }

    @Test
    fun anArrowMovesBetweenNodesInsideTheInstance() {
        enterRow()

        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        // Keys pressed while focus is moving never navigate, so wait until Right has landed or
        // been given up on.
        Thread.sleep(NAVIGATION_SETTLE_MS)
        // From the second button, Shift+Tab stays inside the row.
        pressTab(shift = true)

        Thread.sleep(SETTLE_MS)
        assertTrue(stateMachine.focusState.value is RiveFocusState.Focused, "focus left the row")
        assertFalse(onMain { activeHost.isStopFocused(Stop.Before) }, "Right did not move focus")
    }

    @Test
    fun anArrowDownWithNoNodeBelowHandsOffToTheStopBelow() {
        enterRow()

        pressKey(KeyEvent.KEYCODE_DPAD_DOWN)

        assertTrue(awaitStopFocused(Stop.After), "Down did not hand off to the stop below")
        assertTrue(awaitRiveFocus(expected = false), "Rive kept focus after the arrow left")
        // Android would move focus the same way, so check that Rive, not the host, handled Down.
        assertFalse(
            onMain { KeyEvent.KEYCODE_DPAD_DOWN in activeHost.keysLeftToHost },
            "Down was left to the host",
        )
    }

    @Test
    fun anArrowUpWithNoNodeAboveHandsOffToTheStopAbove() {
        enterRow()

        pressKey(KeyEvent.KEYCODE_DPAD_UP)

        assertTrue(awaitStopFocused(Stop.Before), "Up did not hand off to the stop above")
        assertTrue(awaitRiveFocus(expected = false), "Rive kept focus after the arrow left")
        // Android would move focus the same way, so check that Rive, not the host, handled Up.
        assertFalse(
            onMain { KeyEvent.KEYCODE_DPAD_UP in activeHost.keysLeftToHost },
            "Up was left to the host",
        )
    }

    @Test
    fun anArrowAfterATapDoesNotEnter() {
        show(RiveFocusMode.On, fixture = R.raw.focus_buttons_row)
        tapHost()
        assertTrue(awaitCondition { onMain { activeHost.hostHasFocus() } }, "tapping did not focus")

        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)

        assertTrue(
            awaitCondition {
                onMain { KeyEvent.KEYCODE_DPAD_RIGHT in activeHost.keysLeftToHost }
            },
            "the arrow was consumed instead of left to the host",
        )
        Thread.sleep(SETTLE_MS)
        assertEquals(RiveFocusState.Unfocused, stateMachine.focusState.value, "the arrow entered")
    }

    @Test
    fun ctrlTabIsLeftToTheHost() {
        enterRow()
        // Compose also records the Tab that entered the row, which a stop above received.
        val earlierKeys = onMain { activeHost.keysLeftToHost.size }

        pressKey(KeyEvent.KEYCODE_TAB, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)

        assertTrue(
            awaitCondition {
                onMain { KeyEvent.KEYCODE_TAB in activeHost.keysLeftToHost.drop(earlierKeys) }
            },
            "Ctrl+Tab was consumed instead of left to the host",
        )
    }

    @Test
    fun shiftArrowDoesNotMoveRiveFocus() {
        enterRow()

        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON)
        // Still on the first button, so Shift+Tab leaves the row.
        pressTab(shift = true)

        assertTrue(awaitStopFocused(Stop.Before), "Shift+Right moved Rive focus")
    }

    /**
     * Shows [fixture] in the host under test between two focus stops, and plays it.
     *
     * @param focus The focus mode the host starts with.
     * @param fixture The Rive file to show.
     */
    private fun show(focus: RiveFocusMode, @RawRes fixture: Int = R.raw.focus_nodes_list_order) {
        val resources = runBlocking { loadRiveResources(fixture) }
        stateMachine = resources.stateMachine
        val activeScenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario = activeScenario
        activeScenario.onActivity { activity ->
            val createdHost = when (kind) {
                HostKind.Canvas -> CanvasHost(activity, resources, focus)
                HostKind.Compose -> ComposeHost(activity, resources, focus)
            }
            host = createdHost
            activity.setContentView(createdHost.content)
        }
        instrumentation.waitForIdleSync()
        // Before the first advance there is nothing to focus, so wait for the poll that follows it.
        // Off never polls.
        if (focus != RiveFocusMode.Off) {
            val hasFocusNodes = riveWorker.reportedHasFocusNodes(stateMachine.stateMachineHandle)
            assertTrue(
                awaitCondition {
                    hasFocusNodes.value != null
                },
                "the first poll never answered"
            )
        }
    }

    /** Gives [stop] focus in keyboard mode, as if the user had navigated to it. */
    private fun focus(stop: Stop) {
        // Any key leaves touch mode; one nothing handles keeps focus where it is.
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_SHIFT_LEFT)
        instrumentation.runOnMainSync { activeHost.focusStop(stop) }
        assertTrue(awaitStopFocused(stop), "could not focus $stop")
    }

    private fun pressKey(keyCode: Int, metaState: Int = 0) {
        val downAt = SystemClock.uptimeMillis()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            instrumentation.sendKeySync(
                KeyEvent(downAt, SystemClock.uptimeMillis(), action, keyCode, 0, metaState)
            )
        }
        instrumentation.waitForIdleSync()
    }

    /** Shows the button row and enters it at its first button by Tab from the stop above. */
    private fun enterRow() {
        show(RiveFocusMode.On, fixture = R.raw.focus_buttons_row)
        focus(Stop.Before)
        pressTab()
        assertTrue(awaitRiveFocus(expected = true), "Tab did not enter")
    }

    private fun pressTab(shift: Boolean = false) {
        val downAt = SystemClock.uptimeMillis()
        val metaState = if (shift) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
        instrumentation.sendKeySync(tabEvent(downAt, KeyEvent.ACTION_DOWN, 0, metaState))
        instrumentation.sendKeySync(tabEvent(downAt, KeyEvent.ACTION_UP, 0, metaState))
        instrumentation.waitForIdleSync()
    }

    private fun tabEvent(
        downAt: Long,
        action: Int,
        repeatCount: Int,
        metaState: Int = 0,
    ): KeyEvent = KeyEvent(
        downAt,
        SystemClock.uptimeMillis(),
        action,
        KeyEvent.KEYCODE_TAB,
        repeatCount,
        metaState,
    )

    private fun tapHost() {
        val (x, y) = onMain { activeHost.hostCenterOnScreen() }
        val downAt = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action, x, y, 0)
            try {
                instrumentation.sendPointerSync(event)
            } finally {
                event.recycle()
            }
        }
        instrumentation.waitForIdleSync()
    }

    private fun awaitRiveFocus(expected: Boolean): Boolean =
        awaitCondition { (stateMachine.focusState.value is RiveFocusState.Focused) == expected }

    private fun awaitStopFocused(stop: Stop): Boolean =
        awaitCondition { onMain { activeHost.isStopFocused(stop) } }

    private fun <T> onMain(block: () -> T): T {
        var result: kotlin.Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return checkNotNull(result).getOrThrow()
    }

    /** Waits for [condition], since traversal results and hand-offs land asynchronously. */
    private fun awaitCondition(condition: () -> Boolean): Boolean {
        repeat(AWAIT_ATTEMPTS) {
            if (condition()) return true
            Thread.sleep(AWAIT_STEP_MS)
        }
        return condition()
    }

    /**
     * A host showing the fixture between two focus stops. Call members on the main thread.
     */
    private interface Host : AutoCloseable {
        /** The activity content holding the stops and the host. */
        val content: View

        fun focusStop(stop: Stop)

        fun isStopFocused(stop: Stop): Boolean

        /** Whether the host, or anything inside it, holds Android input focus. */
        fun hostHasFocus(): Boolean

        fun hostCenterOnScreen(): Pair<Float, Float>

        fun setFocus(mode: RiveFocusMode)

        /** Takes the host out of the layout. */
        fun hide()

        /**
         * Key codes of presses that reached the surrounding UI unconsumed, in order. The Compose
         * host also records presses a focus stop received.
         */
        val keysLeftToHost: List<Int>
    }

    /** Hosts the fixture through [RiveCanvasSession] between two views. */
    private class CanvasHost(
        activity: ComponentActivity,
        resources: CloseableRiveResources,
        focus: RiveFocusMode,
    ) : Host {
        private val session = RiveCanvasSession(
            context = activity,
            riveWorker = resources.file.riveWorker,
            artboard = resources.artboard,
            stateMachine = resources.stateMachine,
            focus = focus,
        )
        override val keysLeftToHost = mutableListOf<Int>()
        private val hostView = CanvasHostView(activity, session, keysLeftToHost)
        private val stops = Stop.entries.associateWith {
            View(activity).apply {
                isFocusable = true
                isFocusableInTouchMode = true
            }
        }

        override val content: View = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(stops.getValue(Stop.Before), LinearLayout.LayoutParams(STOP_PX, STOP_PX))
            addView(hostView, LinearLayout.LayoutParams(HOST_PX, HOST_PX))
            addView(stops.getValue(Stop.After), LinearLayout.LayoutParams(STOP_PX, STOP_PX))
        }

        init {
            activity.lifecycleScope.launch { session.beginPlaying(activity.lifecycle) }
            activity.lifecycleScope.launch {
                session.focusability.collect { focusability -> focusability.applyTo(hostView) }
            }
        }

        override fun focusStop(stop: Stop) {
            stops.getValue(stop).requestFocus()
        }

        override fun isStopFocused(stop: Stop): Boolean = stops.getValue(stop).isFocused

        override fun hostHasFocus(): Boolean = hostView.hasFocus()

        override fun hostCenterOnScreen(): Pair<Float, Float> {
            val location = IntArray(2)
            hostView.getLocationOnScreen(location)
            return location[0] + hostView.width / 2f to location[1] + hostView.height / 2f
        }

        override fun setFocus(mode: RiveFocusMode) {
            session.focus = mode
        }

        override fun hide() {
            hostView.visibility = View.GONE
        }

        override fun close() = session.close()
    }

    /**
     * Forwards focus, keys, and touches to [session]. Stops forwarding once it closes.
     *
     * @param keysLeftToHost Receives the key code of each press [session] didn't consume.
     */
    @SuppressLint("ViewConstructor")
    private class CanvasHostView(
        context: Context,
        private val session: RiveCanvasSession,
        private val keysLeftToHost: MutableList<Int>,
    ) : View(context) {
        override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
            if (!session.closed) session.setRegion(Rect(0, 0, width, height))
        }

        override fun onDraw(canvas: Canvas) {
            if (!session.closed) session.draw(canvas)
        }

        override fun onFocusChanged(gainFocus: Boolean, direction: Int, previous: Rect?) {
            super.onFocusChanged(gainFocus, direction, previous)
            if (!session.closed) session.onFocusChanged(this, gainFocus, direction)
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            val consumed = !session.closed && session.onKeyEvent(event)
            if (!consumed && event.action == KeyEvent.ACTION_DOWN) {
                keysLeftToHost += event.keyCode
            }
            return consumed || super.dispatchKeyEvent(event)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (session.closed) return false
            if (event.actionMasked == MotionEvent.ACTION_DOWN) requestFocus()
            return session.onTouchEvent(event)
        }
    }

    /** Hosts the fixture through the [Rive] composable between two focusable composables. */
    private class ComposeHost(
        activity: ComponentActivity,
        resources: CloseableRiveResources,
        focus: RiveFocusMode,
    ) : Host {
        private val focusMode = mutableStateOf(focus)
        private val visible = mutableStateOf(true)
        private val requesters = Stop.entries.associateWith { FocusRequester() }
        private val stopFocused = Stop.entries.associateWith { mutableStateOf(false) }
        private var hostFocused = false
        override val keysLeftToHost = mutableListOf<Int>()

        override val content: View = ComposeView(activity).apply {
            setContent {
                // Keys the Rive surface didn't consume bubble up to its ancestors.
                Column(
                    Modifier.onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            keysLeftToHost += event.nativeKeyEvent.keyCode
                        }
                        false
                    }
                ) {
                    FocusStop(Stop.Before)
                    if (visible.value) {
                        Rive(
                            file = resources.file,
                            modifier = Modifier
                                .requiredSize(HOST_DP.dp)
                                .onFocusChanged { hostFocused = it.hasFocus },
                            artboard = resources.artboard,
                            stateMachine = resources.stateMachine,
                            focus = focusMode.value,
                        )
                    }
                    FocusStop(Stop.After)
                }
            }
        }

        @Composable
        private fun FocusStop(stop: Stop) {
            Box(
                Modifier
                    .requiredSize(STOP_DP.dp)
                    .focusRequester(requesters.getValue(stop))
                    .onFocusChanged { stopFocused.getValue(stop).value = it.isFocused }
                    .focusTarget()
            )
        }

        override fun focusStop(stop: Stop) {
            requesters.getValue(stop).requestFocus()
        }

        override fun isStopFocused(stop: Stop): Boolean = stopFocused.getValue(stop).value

        override fun hostHasFocus(): Boolean = hostFocused

        override fun hostCenterOnScreen(): Pair<Float, Float> {
            val location = IntArray(2)
            content.getLocationOnScreen(location)
            val density = content.resources.displayMetrics.density
            val center = HOST_DP * density / 2f
            return location[0] + center to location[1] + STOP_DP * density + center
        }

        override fun setFocus(mode: RiveFocusMode) {
            focusMode.value = mode
        }

        override fun hide() {
            visible.value = false
        }

        override fun close() = Unit
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun hosts(): List<HostKind> = HostKind.entries

        private const val STOP_PX = 80
        private const val HOST_PX = 400
        private const val STOP_DP = 40
        private const val HOST_DP = 200
        private const val HELD_KEY_REPEATS = 5
        private const val AWAIT_ATTEMPTS = 60
        private const val AWAIT_STEP_MS = 50L
        private const val SETTLE_MS = 500L

        /** Longer than the session's one-second navigation timeout. */
        private const val NAVIGATION_SETTLE_MS = 1_500L
    }
}

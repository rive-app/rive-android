package app.rive

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import app.rive.GlobalViewModelTestFixture.DEFAULT_INSTANCE
import app.rive.GlobalViewModelTestFixture.GLOBALS_ARTBOARD
import app.rive.GlobalViewModelTestFixture.GLOBAL_STRING_2
import app.rive.GlobalViewModelTestFixture.GLOBAL_VIEW_MODEL
import app.rive.GlobalViewModelTestFixture.GLOBAL_VIEW_MODEL_2
import app.rive.GlobalViewModelTestFixture.InstanceSpec
import app.rive.GlobalViewModelTestFixture.MAIN_VIEW_MODEL
import app.rive.GlobalViewModelTestFixture.withInstances
import app.rive.core.RiveWorker
import app.rive.core.StateMachineHandle
import app.rive.runtime.kotlin.test.R
import app.rive.semantics.ProjectedSemanticHierarchy
import app.rive.semantics.SemanticActionType
import app.rive.semantics.SemanticState
import app.rive.semantics.SemanticTreeModel
import app.rive.semantics.hitTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.ZERO

@RunWith(AndroidJUnit4::class)
@OptIn(
    ExperimentalHardwareBitmapRendering::class,
    ExperimentalRiveGlobalViewModels::class,
)
class RiveCanvasSessionTest : RiveAndroidTest() {
    @Test
    fun isSupported_matchesApiGate() {
        assertEquals(
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
            RiveCanvasSession.isSupported()
        )
    }

    @Test
    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.P)
    fun constructor_throwsBelowApi29() = runBlocking<Unit> {
        val res = loadRiveResources(R.raw.empty)
        assertFailsWith<IllegalStateException>(
            "Session should fail fast when API < 29"
        ) {
            RiveCanvasSession(
                context = context,
                riveWorker = riveWorker,
                artboard = res.artboard,
                stateMachine = res.stateMachine
            )
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun operations_afterClose_throw() = runBlocking {
        withPlayingSession {
            assertFalse(session.closed)
            close()
            assertTrue(session.closed)

            assertFailsWith<RiveResourceClosedException>(
                "setRegion should fail after close"
            ) {
                session.setRegion(Rect(0, 0, 64, 64))
            }
            assertFailsWith<RiveResourceClosedException>(
                "Changing semantics mode should fail after close"
            ) {
                session.semantics = RiveSemanticsMode.On
            }
            assertFailsWith<RiveResourceClosedException>(
                "beginPlaying should fail after close"
            ) {
                session.beginPlaying(lifecycle)
            }

            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            try {
                val softwareCanvas = Canvas(bitmap)
                assertFailsWith<RiveResourceClosedException>(
                    "Draw should fail after close"
                ) {
                    session.draw(softwareCanvas)
                }
            } finally {
                bitmap.recycle()
            }

            val eventTime = SystemClock.uptimeMillis()
            val postCloseDown = MotionEvent.obtain(
                eventTime,
                eventTime,
                MotionEvent.ACTION_DOWN,
                8f,
                8f,
                0
            )
            try {
                assertFailsWith<RiveResourceClosedException>(
                    "Touch should fail after close"
                ) {
                    session.onTouchEvent(postCloseDown)
                }
            } finally {
                postCloseDown.recycle()
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun beginPlaying_whenResumed_emitsFrame() = runBlocking {
        withPlayingSession {
            resume()

            awaitFrameCountGreaterThan(0)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun setRegion_withDifferentSize_emitsFrame() = runBlocking {
        withPlayingSession {
            resume()
            awaitFrameCountGreaterThan(0)

            val beforeResize = currentFrameCount()
            setRegion(Rect(0, 0, 128, 128))

            awaitFrameCountGreaterThan(beforeResize)
        }
    }

    /** Verifies that a resize rejects a delayed settled callback from the preceding generation. */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun setRegion_ignoresSettledCallbackFromEarlierGeneration() = runBlocking {
        withPlayingSession {
            resume()
            awaitFrameCountGreaterThan(0)
            val settledFrameCount = awaitFrameCountSettled()

            setRegion(Rect(0, 0, 128, 128))
            emitStaleSettledCallback()

            awaitFrameCountGreaterThan(settledFrameCount)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun beginPlaying_whenSettled_stopsEmittingFrames() = runBlocking {
        withPlayingSession {
            resume()
            awaitFrameCountGreaterThan(0)

            val settledFrameCount = awaitFrameCountSettled()

            delay(250)
            assertEquals(
                settledFrameCount,
                currentFrameCount(),
                "Frame count should stop growing once the session settles"
            )
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun onTouchEvent_afterSettled_emitsFrame() = runBlocking {
        withPlayingSession {
            resume()
            awaitFrameCountGreaterThan(0)
            val settledFrameCount = awaitFrameCountSettled()

            touchDownUp(24f, 24f)

            awaitFrameCountGreaterThan(settledFrameCount)
        }
    }

    /** Verifies ordinary session playback does not implicitly activate semantic draining. */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun playback_withoutEnableSemanticsDoesNotPublishSemanticTree() = runBlocking {
        val resources = loadDefaultRiveResources(R.raw.tabtest)
        val viewModelInstance = ViewModelInstance.create(
            resources.file,
            ViewModelSource.DefaultForArtboard(resources.artboard).defaultInstance(),
        )
        try {
            coroutineScope {
                lateinit var lifecycleOwner: TestLifecycleOwner
                lateinit var session: RiveCanvasSession
                lateinit var tree: SemanticTreeModel
                withContext(Dispatchers.Main.immediate) {
                    lifecycleOwner = TestLifecycleOwner()
                    session = RiveCanvasSession(
                        context = context,
                        riveWorker = riveWorker,
                        artboard = resources.artboard,
                        stateMachine = resources.stateMachine,
                        viewModelInstance = viewModelInstance,
                        fit = Fit.Fill,
                    ).also { created ->
                        created.setRegion(INITIAL_SEMANTICS_REGION)
                    }
                    tree = resources.stateMachine.semanticTree
                }

                val playJob = launch(Dispatchers.Main.immediate) {
                    session.beginPlaying(lifecycleOwner.lifecycle)
                }
                try {
                    withContext(Dispatchers.Main.immediate) {
                        assertFalse(resources.stateMachine.settled.value)
                        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
                    }
                    withTimeout(SEMANTICS_TIMEOUT_MILLIS) {
                        resources.stateMachine.settled.first { settled -> settled }
                    }

                    withContext(Dispatchers.Main.immediate) {
                        assertEquals(0, tree.version)
                        assertEquals(0, tree.nodeCount)
                    }
                } finally {
                    withContext(Dispatchers.Main.immediate) { session.close() }
                    playJob.cancelAndJoin()
                }
            }
        } finally {
            withContext(Dispatchers.Main.immediate) { viewModelInstance.close() }
        }
    }

    /** Verifies session activation drains initial and resized view-space semantic geometry. */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun semanticsOn_whilePlayingPublishesAndResizesSemanticTree() = runBlocking {
        val resources = loadDefaultRiveResources(R.raw.tabtest)
        val viewModelInstance = ViewModelInstance.create(
            resources.file,
            ViewModelSource.DefaultForArtboard(resources.artboard).defaultInstance(),
        )
        try {
            coroutineScope {
                lateinit var lifecycleOwner: TestLifecycleOwner
                lateinit var session: RiveCanvasSession
                lateinit var tree: SemanticTreeModel
                withContext(Dispatchers.Main.immediate) {
                    lifecycleOwner = TestLifecycleOwner()
                    session = RiveCanvasSession(
                        context = context,
                        riveWorker = riveWorker,
                        artboard = resources.artboard,
                        stateMachine = resources.stateMachine,
                        viewModelInstance = viewModelInstance,
                        fit = Fit.Fill,
                    ).also { created ->
                        created.setRegion(INITIAL_SEMANTICS_REGION)
                    }
                    tree = resources.stateMachine.semanticTree
                    assertEquals(0, tree.version)
                    assertEquals(0, tree.nodeCount)
                }

                val playJob = launch(Dispatchers.Main.immediate) {
                    session.beginPlaying(lifecycleOwner.lifecycle)
                }
                try {
                    withContext(Dispatchers.Main.immediate) {
                        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
                        session.semantics = RiveSemanticsMode.On
                        session.semantics = RiveSemanticsMode.On
                    }
                    val initialVersion = withTimeout(SEMANTICS_TIMEOUT_MILLIS) {
                        tree.versionFlow.first { version -> version > 0 }
                    }
                    val initialBounds = withContext(Dispatchers.Main.immediate) {
                        tree.boundsForLabel(SEMANTIC_NODE_LABEL)
                    }

                    withContext(Dispatchers.Main.immediate) {
                        session.setRegion(RESIZED_SEMANTICS_REGION)
                    }
                    withTimeout(SEMANTICS_TIMEOUT_MILLIS) {
                        tree.versionFlow.first { version -> version > initialVersion }
                    }
                    val resizedBounds = withContext(Dispatchers.Main.immediate) {
                        tree.boundsForLabel(SEMANTIC_NODE_LABEL)
                    }

                    assertEquals(initialBounds.width() * 2f, resizedBounds.width(), 0.1f)
                    assertEquals(initialBounds.height(), resizedBounds.height(), 0.1f)
                } finally {
                    withContext(Dispatchers.Main.immediate) { session.close() }
                    playJob.cancelAndJoin()
                }
            }
        } finally {
            withContext(Dispatchers.Main.immediate) { viewModelInstance.close() }
        }
    }

    /** Verifies a semantic action wakes a settled session for another advance and render. */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun semanticAction_afterSettledEmitsFrame() = runBlocking {
        val resources = loadDefaultRiveResources(R.raw.tabtest)
        val viewModelInstance = ViewModelInstance.create(
            resources.file,
            ViewModelSource.DefaultForArtboard(resources.artboard).defaultInstance(),
        )
        try {
            coroutineScope {
                lateinit var lifecycleOwner: TestLifecycleOwner
                lateinit var session: RiveCanvasSession
                lateinit var tree: SemanticTreeModel
                val frameCount = AtomicInteger(0)
                withContext(Dispatchers.Main.immediate) {
                    lifecycleOwner = TestLifecycleOwner()
                    session = RiveCanvasSession(
                        context = context,
                        riveWorker = riveWorker,
                        artboard = resources.artboard,
                        stateMachine = resources.stateMachine,
                        viewModelInstance = viewModelInstance,
                        fit = Fit.Fill,
                    ).also { created ->
                        created.setRegion(INITIAL_SEMANTICS_REGION)
                    }
                    tree = resources.stateMachine.semanticTree
                }
                val frameCollector = launch {
                    session.frameAvailable.collect { frameCount.incrementAndGet() }
                }
                val playJob = launch(Dispatchers.Main.immediate) {
                    session.beginPlaying(lifecycleOwner.lifecycle)
                }

                try {
                    withContext(Dispatchers.Main.immediate) {
                        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
                        session.semantics = RiveSemanticsMode.On
                    }
                    withTimeout(SEMANTICS_TIMEOUT_MILLIS) {
                        tree.versionFlow.first { version -> version > 0 }
                    }
                    withTimeout(SEMANTICS_TIMEOUT_MILLIS) {
                        resources.stateMachine.settled.first { settled -> settled }
                    }
                    val settledFrameCount = frameCount.awaitSettledFrameCount()
                    val semanticNodeId = withContext(Dispatchers.Main.immediate) {
                        tree.nodeIdForLabel(SEMANTIC_NODE_LABEL)
                    }

                    withContext(Dispatchers.Main.immediate) {
                        resources.stateMachine.fireSemanticAction(
                            semanticNodeId,
                            SemanticActionType.Tap,
                        )
                    }

                    withTimeout(SEMANTICS_TIMEOUT_MILLIS) {
                        while (frameCount.get() <= settledFrameCount) {
                            delay(16)
                        }
                    }
                } finally {
                    withContext(Dispatchers.Main.immediate) { session.close() }
                    playJob.cancelAndJoin()
                    frameCollector.cancelAndJoin()
                }
            }
        } finally {
            withContext(Dispatchers.Main.immediate) { viewModelInstance.close() }
        }
    }

    /** Verifies closing a semantics-enabled session queues authored focus cleanup. */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun close_withSemanticsEnabledClearsSemanticFocus() = runBlocking<Unit> {
        val resources = loadDefaultRiveResources(R.raw.semantic_list_scroll_focus_fixed)
        val viewModelInstance = ViewModelInstance.create(
            resources.file,
            ViewModelSource.DefaultForArtboard(resources.artboard).defaultInstance(),
        )
        try {
            coroutineScope {
                lateinit var lifecycleOwner: TestLifecycleOwner
                lateinit var session: RiveCanvasSession
                lateinit var tree: SemanticTreeModel
                withContext(Dispatchers.Main.immediate) {
                    lifecycleOwner = TestLifecycleOwner()
                    session = RiveCanvasSession(
                        context = context,
                        riveWorker = riveWorker,
                        artboard = resources.artboard,
                        stateMachine = resources.stateMachine,
                        viewModelInstance = viewModelInstance,
                        fit = Fit.Fill,
                    ).also { created ->
                        created.setRegion(INITIAL_SEMANTICS_REGION)
                    }
                    tree = resources.stateMachine.semanticTree
                }
                val playJob = launch(Dispatchers.Main.immediate) {
                    session.beginPlaying(lifecycleOwner.lifecycle)
                }
                var sessionClosed = false

                try {
                    withContext(Dispatchers.Main.immediate) {
                        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
                        session.semantics = RiveSemanticsMode.On
                    }
                    withTimeout(SEMANTICS_TIMEOUT_MILLIS) {
                        tree.versionFlow.first { version -> version > 0 }
                    }
                    val focusNodeId = withContext(Dispatchers.Main.immediate) {
                        tree.nodeIdForLabel(FOCUS_NODE_LABEL)
                    }
                    withContext(Dispatchers.Main.immediate) {
                        resources.stateMachine.requestSemanticFocus(focusNodeId)
                    }
                    val focusedVersion = withTimeout(SEMANTICS_TIMEOUT_MILLIS) {
                        tree.versionFlow.first {
                            withContext(Dispatchers.Main.immediate) {
                                tree.isNodeFocused(focusNodeId)
                            }
                        }
                    }

                    withContext(Dispatchers.Main.immediate) {
                        session.close()
                        sessionClosed = true
                    }
                    playJob.cancelAndJoin()

                    withContext(Dispatchers.Main.immediate) {
                        resources.stateMachine.advance(ZERO)
                        resources.stateMachine.drainSemanticsDiff(
                            fit = Fit.Fill,
                            surfaceWidth = INITIAL_SEMANTICS_REGION.width().toFloat(),
                            surfaceHeight = INITIAL_SEMANTICS_REGION.height().toFloat(),
                        )
                    }
                    withTimeout(SEMANTICS_TIMEOUT_MILLIS) {
                        tree.versionFlow.first { version ->
                            version > focusedVersion && withContext(Dispatchers.Main.immediate) {
                                !tree.isNodeFocused(focusNodeId)
                            }
                        }
                    }
                } finally {
                    if (!sessionClosed) {
                        withContext(Dispatchers.Main.immediate) { session.close() }
                    }
                    playJob.cancelAndJoin()
                }
            }
        } finally {
            withContext(Dispatchers.Main.immediate) { viewModelInstance.close() }
        }
    }

    /** Verifies the render-region origin is not folded into session-local semantic bounds. */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun semanticsOn_withOffsetRegionPublishesRenderRegionLocalBounds() = runBlocking {
        // Both sessions render the same file at the same size. Only the destination origin differs.
        val zeroOriginBounds = semanticBoundsForRegion(INITIAL_SEMANTICS_REGION)
        val offsetOriginBounds = semanticBoundsForRegion(OFFSET_SEMANTICS_REGION)

        assertEquals(zeroOriginBounds.left, offsetOriginBounds.left, 0.1f)
        assertEquals(zeroOriginBounds.top, offsetOriginBounds.top, 0.1f)
        assertEquals(zeroOriginBounds.right, offsetOriginBounds.right, 0.1f)
        assertEquals(zeroOriginBounds.bottom, offsetOriginBounds.bottom, 0.1f)
    }

    /** Verifies a Canvas host applies its non-zero render-region origin exactly once. */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun semanticHost_withOffsetRegionMapsBoundsAndHitTests() = runBlocking<Unit> {
        withSemanticTreeForRegion(OFFSET_SEMANTICS_REGION) { tree ->
            val nodeId = tree.nodeIdForLabel(SEMANTIC_NODE_LABEL)
            val localBounds = tree.boundsForLabel(SEMANTIC_NODE_LABEL)
            val host = TestCanvasSemanticHost(tree, OFFSET_SEMANTICS_REGION)
            val hostBounds = host.boundsInHost(nodeId)

            assertEquals(
                localBounds.left + OFFSET_SEMANTICS_REGION.left,
                hostBounds.left,
                0.1f,
            )
            assertEquals(
                localBounds.top + OFFSET_SEMANTICS_REGION.top,
                hostBounds.top,
                0.1f,
            )
            assertEquals(
                localBounds.right + OFFSET_SEMANTICS_REGION.left,
                hostBounds.right,
                0.1f,
            )
            assertEquals(
                localBounds.bottom + OFFSET_SEMANTICS_REGION.top,
                hostBounds.bottom,
                0.1f,
            )
            assertEquals(nodeId, host.hitTest(hostBounds.centerX(), hostBounds.centerY()))
            assertNull(
                host.hitTest(
                    OFFSET_SEMANTICS_REGION.left - 1f,
                    OFFSET_SEMANTICS_REGION.top - 1f,
                )
            )
        }
    }

    /** Verifies that mutating an explicitly bound global wakes a settled canvas session. */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun globalViewModelMutation_afterSettled_emitsFrame() = runBlocking {
        val resources = loadRiveResources(
            R.raw.data_bind_test_impl,
            artboardName = GLOBALS_ARTBOARD,
        )
        withInstances(
            resources.file,
            InstanceSpec(MAIN_VIEW_MODEL, DEFAULT_INSTANCE),
            InstanceSpec(GLOBAL_VIEW_MODEL, DEFAULT_INSTANCE),
            InstanceSpec(GLOBAL_VIEW_MODEL_2, DEFAULT_INSTANCE),
        ) { (main, global, global2) ->
            val session = withContext(Dispatchers.Main.immediate) {
                RiveCanvasSession(
                    context = context,
                    riveWorker = riveWorker,
                    artboard = resources.artboard,
                    stateMachine = resources.stateMachine,
                    viewModelInstance = main,
                    globalViewModelInstances = mapOf(
                        GLOBAL_VIEW_MODEL to global,
                        GLOBAL_VIEW_MODEL_2 to global2,
                    ),
                )
            }
            withPlayingSession(
                session = session,
                stateMachineHandle = resources.stateMachine.stateMachineHandle,
            ) {
                resume()
                awaitFrameCountGreaterThan(0)
                val settledFrameCount = awaitFrameCountSettled()

                global2.setString(GLOBAL_STRING_2, "Canvas Global 2")

                awaitFrameCountGreaterThan(settledFrameCount)
            }
        }
    }

    /** Verifies that restarting playback establishes a boundary against prior settled callbacks. */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun beginPlaying_again_ignoresSettledCallbackFromEarlierGeneration() = runBlocking {
        withPlayingSession {
            resume()
            awaitFrameCountGreaterThan(0)
            val settledFrameCount = awaitFrameCountSettled()

            restartPlaying()
            emitStaleSettledCallback()

            awaitFrameCountGreaterThan(settledFrameCount)
        }
    }

    private suspend fun withPlayingSession(block: suspend PlayingSession.() -> Unit) {
        val res = loadRiveResources(R.raw.empty)
        val session = withContext(Dispatchers.Main.immediate) {
            RiveCanvasSession(
                context = context,
                riveWorker = riveWorker,
                artboard = res.artboard,
                stateMachine = res.stateMachine,
            )
        }
        withPlayingSession(session, res.stateMachine.stateMachineHandle, block)
    }

    /**
     * Runs [block] with [session] actively collecting frames and driven by a test lifecycle.
     *
     * @param session The session to play and close after [block].
     * @param stateMachineHandle The handle used to inject settling callbacks in tests.
     * @param block The test operation to run with the playing session fixture.
     */
    private suspend fun withPlayingSession(
        session: RiveCanvasSession,
        stateMachineHandle: StateMachineHandle,
        block: suspend PlayingSession.() -> Unit,
    ) {
        coroutineScope {
            val lifecycleOwner = withContext(Dispatchers.Main.immediate) {
                TestLifecycleOwner()
            }
            withContext(Dispatchers.Main.immediate) {
                session.setRegion(Rect(0, 0, 96, 96))
            }
            val frameCount = AtomicInteger(0)
            val frameCollector = launch {
                session.frameAvailable.collect {
                    frameCount.incrementAndGet()
                }
            }
            val startPlaying = {
                launch(Dispatchers.Main.immediate) {
                    session.beginPlaying(lifecycleOwner.lifecycle)
                }
            }
            val playJob = startPlaying()

            val playingSession = PlayingSession(
                session = session,
                riveWorker = riveWorker,
                stateMachineHandle = stateMachineHandle,
                lifecycleOwner = lifecycleOwner,
                frameCount = frameCount,
                playJob = playJob,
                startPlaying = startPlaying,
                frameCollector = frameCollector,
            )
            try {
                playingSession.block()
            } finally {
                playingSession.close()
            }
        }
    }

    /**
     * Publishes the initial real-asset semantic tree for [region] and returns one node's bounds.
     *
     * @param region Destination render region supplied to the Canvas session.
     * @return Normalized render-region-local bounds for [SEMANTIC_NODE_LABEL].
     */
    private suspend fun semanticBoundsForRegion(region: Rect): RectF {
        return withSemanticTreeForRegion(region) { tree ->
            tree.boundsForLabel(SEMANTIC_NODE_LABEL)
        }
    }

    /**
     * Publishes a real-asset semantic tree for [region] and evaluates [block] on the main thread.
     *
     * @param region Destination render region supplied to the Canvas session.
     * @param block Main-thread operation to perform while the semantic fixture remains active.
     * @return Value produced by [block].
     */
    private suspend fun <T> withSemanticTreeForRegion(
        region: Rect,
        block: (SemanticTreeModel) -> T,
    ): T {
        val resources = loadDefaultRiveResources(R.raw.tabtest)
        val viewModelInstance = ViewModelInstance.create(
            resources.file,
            ViewModelSource.DefaultForArtboard(resources.artboard).defaultInstance(),
        )
        return try {
            coroutineScope {
                lateinit var lifecycleOwner: TestLifecycleOwner
                lateinit var session: RiveCanvasSession
                lateinit var tree: SemanticTreeModel
                withContext(Dispatchers.Main.immediate) {
                    lifecycleOwner = TestLifecycleOwner()
                    session = RiveCanvasSession(
                        context = context,
                        riveWorker = riveWorker,
                        artboard = resources.artboard,
                        stateMachine = resources.stateMachine,
                        viewModelInstance = viewModelInstance,
                        fit = Fit.Fill,
                    ).also { created ->
                        created.setRegion(region)
                    }
                    tree = resources.stateMachine.semanticTree
                }

                val playJob = launch(Dispatchers.Main.immediate) {
                    session.beginPlaying(lifecycleOwner.lifecycle)
                }
                try {
                    withContext(Dispatchers.Main.immediate) {
                        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
                        session.semantics = RiveSemanticsMode.On
                    }
                    withTimeout(SEMANTICS_TIMEOUT_MILLIS) {
                        tree.versionFlow.first { version -> version > 0 }
                    }
                    withContext(Dispatchers.Main.immediate) {
                        block(tree)
                    }
                } finally {
                    withContext(Dispatchers.Main.immediate) { session.close() }
                    playJob.cancelAndJoin()
                }
            }
        } finally {
            withContext(Dispatchers.Main.immediate) { viewModelInstance.close() }
        }
    }

    /**
     * Test-only model of the coordinate work required from a client-owned Canvas host.
     *
     * Core publishes semantic geometry local to [renderRegion]. The host adds that region's origin
     * when exposing bounds and removes it before hit testing the projected semantic hierarchy.
     *
     * @param tree Current render-region-local semantic tree.
     * @param renderRegion Rive destination rectangle in host-view coordinates.
     */
    @MainThread
    private class TestCanvasSemanticHost(
        private val tree: SemanticTreeModel,
        renderRegion: Rect,
    ) {
        private val region = Rect(renderRegion)
        private val hierarchy = ProjectedSemanticHierarchy.from(tree)

        /**
         * Returns one node's semantic bounds in host-view coordinates.
         *
         * @param nodeId Rive semantic node ID.
         * @return Normalized node bounds offset by the render-region origin.
         */
        fun boundsInHost(nodeId: Int): RectF {
            val node = requireNotNull(tree.nodeById(nodeId)) {
                "Semantic node '$nodeId' was not published"
            }
            return RectF(
                minOf(node.minX, node.maxX) + region.left,
                minOf(node.minY, node.maxY) + region.top,
                maxOf(node.minX, node.maxX) + region.left,
                maxOf(node.minY, node.maxY) + region.top,
            )
        }

        /**
         * Hit-tests a host-view point against render-region-local semantic geometry.
         *
         * @param x Horizontal host-view coordinate in physical pixels.
         * @param y Vertical host-view coordinate in physical pixels.
         * @return Deepest matching Rive semantic node ID, or `null` outside the region/tree.
         */
        fun hitTest(x: Float, y: Float): Int? {
            if (x < region.left || x >= region.right || y < region.top || y >= region.bottom) {
                return null
            }
            return hierarchy.hitTest(tree, x - region.left, y - region.top)
        }
    }

    /**
     * Minimal lifecycle owner for driving [RiveCanvasSession.beginPlaying] through the same
     * lifecycle state transitions a host view or activity would provide.
     */
    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply {
            currentState = Lifecycle.State.CREATED
        }

        override val lifecycle: Lifecycle
            get() = registry

        fun moveToState(state: Lifecycle.State) {
            registry.currentState = state
        }
    }

    /**
     * Test fixture that owns a live [RiveCanvasSession] plus the coroutine jobs required to observe
     * its frame publication loop.
     *
     * Tests use this instead of directly touching collectors, lifecycle plumbing, or frame
     * counters, keeping assertions focused on the session API surface.
     */
    private class PlayingSession(
        val session: RiveCanvasSession,
        private val riveWorker: RiveWorker,
        private val stateMachineHandle: StateMachineHandle,
        private val lifecycleOwner: TestLifecycleOwner,
        private val frameCount: AtomicInteger,
        private var playJob: Job,
        private val startPlaying: () -> Job,
        private val frameCollector: Job,
    ) {
        /** Lifecycle supplied to [RiveCanvasSession.beginPlaying]. */
        val lifecycle: Lifecycle
            get() = lifecycleOwner.lifecycle

        /** @return The number of public [RiveCanvasSession.frameAvailable] events observed. */
        fun currentFrameCount(): Int = frameCount.get()

        /**
         * Waits until the session has emitted more than [count] frame events.
         *
         * This is the main assertion hook for tests that expect a user-visible frame to become
         * available after lifecycle, resize, or input changes.
         */
        suspend fun awaitFrameCountGreaterThan(
            count: Int,
            timeoutMs: Long = 5_000L
        ) {
            withTimeout(timeoutMs) {
                while (frameCount.get() <= count) {
                    delay(16)
                }
            }
        }

        /**
         * Waits until frame publication has been quiet for [quietMs].
         *
         * This observes settled-skip behavior through the session's public frame signal rather than
         * reading worker internals directly.
         */
        suspend fun awaitFrameCountSettled(
            quietMs: Long = 250L,
            timeoutMs: Long = 5_000L
        ): Int {
            var lastCount = frameCount.get()
            var lastChangedAt = SystemClock.uptimeMillis()
            withTimeout(timeoutMs) {
                while (SystemClock.uptimeMillis() - lastChangedAt < quietMs) {
                    delay(16)
                    val count = frameCount.get()
                    if (count != lastCount) {
                        lastCount = count
                        lastChangedAt = SystemClock.uptimeMillis()
                    }
                }
            }
            return lastCount
        }

        /** Moves the test lifecycle to RESUMED so [RiveCanvasSession.beginPlaying] can render. */
        suspend fun resume() {
            withContext(Dispatchers.Main.immediate) {
                lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
            }
        }

        /** Applies a region change on the main thread, matching the session API contract. */
        suspend fun setRegion(region: Rect) {
            withContext(Dispatchers.Main.immediate) {
                session.setRegion(region)
            }
        }

        /**
         * Cancels the active playback invocation and starts another one for the same session.
         */
        suspend fun restartPlaying() {
            playJob.cancelAndJoin()
            withContext(Dispatchers.Main.immediate) {
                playJob = startPlaying()
            }
        }

        /**
         * Delivers a settled callback whose request ID predates every real request in this test.
         */
        suspend fun emitStaleSettledCallback() {
            withContext(Dispatchers.Main.immediate) {
                riveWorker.onStateMachineSettled(Long.MIN_VALUE, stateMachineHandle)
            }
        }

        /**
         * Sends a down/up pair through the session to test pointer-driven render wakeup behavior.
         */
        suspend fun touchDownUp(x: Float, y: Float) {
            val downAt = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(
                downAt,
                downAt,
                MotionEvent.ACTION_DOWN,
                x,
                y,
                0
            )
            val up = MotionEvent.obtain(
                downAt,
                downAt + 16L,
                MotionEvent.ACTION_UP,
                x,
                y,
                0
            )
            try {
                withContext(Dispatchers.Main.immediate) {
                    assertTrue(session.onTouchEvent(down))
                    assertTrue(session.onTouchEvent(up))
                }
            } finally {
                down.recycle()
                up.recycle()
            }
        }

        /** Closes the session and stops the fixture's collector/playback jobs. */
        suspend fun close() {
            withContext(Dispatchers.Main.immediate) {
                session.close()
            }
            playJob.cancelAndJoin()
            frameCollector.cancelAndJoin()
        }
    }

    /** Constants for real semantic-tree session integration. */
    private companion object {
        val INITIAL_SEMANTICS_REGION = Rect(0, 0, 96, 96)
        val RESIZED_SEMANTICS_REGION = Rect(0, 0, 192, 96)
        val OFFSET_SEMANTICS_REGION = Rect(37, 61, 133, 157)
        const val SEMANTIC_NODE_LABEL = "All"
        const val FOCUS_NODE_LABEL = "Element 1"
        const val SEMANTICS_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * Returns normalized view-space bounds for the semantic node carrying [label].
 *
 * @param label Authored label identifying the node to inspect.
 * @return The matching node's normalized view-space bounds.
 */
@MainThread
private fun SemanticTreeModel.boundsForLabel(label: String): RectF {
    val pending = ArrayDeque<Int>().apply { roots.forEach(::addLast) }
    while (pending.isNotEmpty()) {
        val node = nodeById(pending.removeFirst()) ?: continue
        if (node.label == label) {
            return RectF(
                minOf(node.minX, node.maxX),
                minOf(node.minY, node.maxY),
                maxOf(node.minX, node.maxX),
                maxOf(node.minY, node.maxY),
            )
        }
        node.children.forEach(pending::addLast)
    }
    error("Semantic node '$label' was not published")
}

/**
 * Returns the semantic node ID carrying [label].
 *
 * @param label Authored label identifying the node.
 * @return Matching semantic node ID.
 */
@MainThread
private fun SemanticTreeModel.nodeIdForLabel(label: String): Int {
    val pending = ArrayDeque<Int>().apply { roots.forEach(::addLast) }
    while (pending.isNotEmpty()) {
        val node = nodeById(pending.removeFirst()) ?: continue
        if (node.label == label) {
            return node.id
        }
        node.children.forEach(pending::addLast)
    }
    error("Semantic node '$label' was not published")
}

/**
 * Reports whether the node identified by [nodeId] carries Rive's authored focused state.
 *
 * @param nodeId Semantic node ID to inspect.
 * @return `true` when the current node snapshot is semantically focused.
 */
@MainThread
private fun SemanticTreeModel.isNodeFocused(nodeId: Int): Boolean {
    val node = requireNotNull(nodeById(nodeId)) { "Semantic node '$nodeId' was not published" }
    return SemanticState.has(node.stateFlags, SemanticState.Focused)
}

/**
 * Waits for frame publication to remain unchanged, then returns the stable count.
 *
 * @param quietMs Duration without a new frame required to consider publication settled.
 * @param timeoutMs Maximum total wait duration.
 * @return Stable frame count after the quiet interval.
 */
private suspend fun AtomicInteger.awaitSettledFrameCount(
    quietMs: Long = 250L,
    timeoutMs: Long = 5_000L,
): Int {
    var lastCount = get()
    var lastChangedAt = SystemClock.uptimeMillis()
    withTimeout(timeoutMs) {
        while (SystemClock.uptimeMillis() - lastChangedAt < quietMs) {
            delay(16)
            val count = get()
            if (count != lastCount) {
                lastCount = count
                lastChangedAt = SystemClock.uptimeMillis()
            }
        }
    }
    return lastCount
}

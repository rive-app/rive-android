package app.rive

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import app.rive.core.withDefaultRiveResources
import app.rive.core.withPolling
import app.rive.test.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalHardwareBitmapRendering::class)
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
        riveWorker.withPolling {
            withDefaultRiveResources(R.raw.empty) {
                assertFailsWith<IllegalStateException>(
                    "Session should fail fast when API < 29"
                ) {
                    RiveCanvasSession(
                        riveWorker = riveWorker,
                        artboard = artboard,
                        stateMachine = stateMachine
                    )
                }
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun operations_afterClose_throw() = runBlocking {
        withPlayingSession {
            close()

            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            try {
                val softwareCanvas = Canvas(bitmap)
                assertFailsWith<IllegalStateException>(
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
                assertFailsWith<IllegalStateException>(
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

    private suspend fun withPlayingSession(block: suspend PlayingSession.() -> Unit) {
        riveWorker.withPolling {
            withDefaultRiveResources(R.raw.empty) {
                coroutineScope {
                    val lifecycleOwner = withContext(Dispatchers.Main.immediate) {
                        TestLifecycleOwner()
                    }
                    val session = withContext(Dispatchers.Main.immediate) {
                        RiveCanvasSession(
                            riveWorker = riveWorker,
                            artboard = artboard,
                            stateMachine = stateMachine,
                        ).also { created ->
                            created.setRegion(Rect(0, 0, 96, 96))
                        }
                    }
                    val frameCount = AtomicInteger(0)
                    val frameCollector = launch {
                        session.frameAvailable.collect {
                            frameCount.incrementAndGet()
                        }
                    }
                    val playJob = launch(Dispatchers.Main.immediate) {
                        session.beginPlaying(lifecycleOwner.lifecycle)
                    }

                    val playingSession = PlayingSession(
                        session = session,
                        lifecycleOwner = lifecycleOwner,
                        frameCount = frameCount,
                        playJob = playJob,
                        frameCollector = frameCollector,
                    )
                    try {
                        playingSession.block()
                    } finally {
                        playingSession.close()
                    }
                }
            }
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
        private val lifecycleOwner: TestLifecycleOwner,
        private val frameCount: AtomicInteger,
        private val playJob: Job,
        private val frameCollector: Job,
    ) {
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
}

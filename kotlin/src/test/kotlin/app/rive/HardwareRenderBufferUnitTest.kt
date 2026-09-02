@file:OptIn(ExperimentalHardwareBitmapRendering::class)

package app.rive

import android.graphics.Bitmap
import android.os.Build
import android.os.Trace
import app.rive.core.ArtboardHandle
import app.rive.core.CommandQueue
import app.rive.core.FileHandle
import app.rive.core.RiveSurface
import app.rive.core.StateMachineHandle
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

private const val HARDWARE_TEST_WIDTH = 64
private const val HARDWARE_TEST_HEIGHT = 32
private const val HARDWARE_TEST_FILE_HANDLE = 10L
private const val HARDWARE_TEST_ARTBOARD_HANDLE = 20L
private const val HARDWARE_TEST_STATE_MACHINE_HANDLE = 30L

class HardwareRenderBufferUnitTest : FunSpec({
    beforeSpec {
        mockkStatic(Trace::class)
        every { Trace.beginSection(any()) } just runs
        every { Trace.endSection() } just runs
    }

    afterSpec {
        unmockkStatic(Trace::class)
    }

    test("Constructor rejects invalid configuration before creating a frame source") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val factory = mockk<HardwareFrameSourceFactory>()

        shouldThrow<IllegalArgumentException> {
            HardwareRenderBuffer.createForTesting(0, HARDWARE_TEST_HEIGHT, worker, factory)
        }
        shouldThrow<IllegalArgumentException> {
            HardwareRenderBuffer.createForTesting(HARDWARE_TEST_WIDTH, 0, worker, factory)
        }
        shouldThrow<IllegalArgumentException> {
            HardwareRenderBuffer.createForTesting(
                HARDWARE_TEST_WIDTH,
                HARDWARE_TEST_HEIGHT,
                worker,
                factory,
                firstFrameTimeoutMillis = -1L,
            )
        }
        shouldThrow<IllegalStateException> {
            HardwareRenderBuffer.createForTesting(
                HARDWARE_TEST_WIDTH,
                HARDWARE_TEST_HEIGHT,
                worker,
                factory,
                sdkInt = Build.VERSION_CODES.P,
            )
        }

        verify(exactly = 0) { factory.create(any(), any(), any()) }
    }

    test("Constructor creates and observes an injected frame source") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val surface = TestRiveSurface(
            worker,
            width = HARDWARE_TEST_WIDTH,
            height = HARDWARE_TEST_HEIGHT,
            resizable = false,
        )
        val source = FakeHardwareFrameSource(surface)
        val factory = mockk<HardwareFrameSourceFactory>()
        every {
            factory.create(HARDWARE_TEST_WIDTH, HARDWARE_TEST_HEIGHT, worker)
        } returns source

        val buffer = HardwareRenderBuffer.createForTesting(
            HARDWARE_TEST_WIDTH,
            HARDWARE_TEST_HEIGHT,
            worker,
            factory,
        )

        buffer.width shouldBe HARDWARE_TEST_WIDTH
        buffer.height shouldBe HARDWARE_TEST_HEIGHT
        buffer.surface shouldBe surface
        source.hasListener.shouldBeTrue()
    }

    test("Constructor completes cleanup when registration and stop throw the same failure") {
        val worker = mockk<CommandQueue>(relaxed = true)
        val surface = TestRiveSurface(
            worker,
            width = HARDWARE_TEST_WIDTH,
            height = HARDWARE_TEST_HEIGHT,
            resizable = false,
        )
        val failure = IllegalStateException("Listener registration failed")
        val bitmap = testBitmap()
        val source = FakeHardwareFrameSource(
            surface,
            listenerRegistrationFailure = failure,
            stopFailure = failure,
            frameDuringListenerRegistration = bitmap,
        )

        val error = shouldThrow<IllegalStateException> {
            HardwareRenderBuffer.createForTesting(
                HARDWARE_TEST_WIDTH,
                HARDWARE_TEST_HEIGHT,
                worker,
                HardwareFrameSourceFactory { _, _, _ -> source },
            )
        }

        error shouldBe failure
        source.stopCount shouldBe 1
        surface.closed.shouldBeTrue()
        verify(exactly = 1) { bitmap.recycle() }
    }

    test("Close stops the frame source and closes its surface exactly once") {
        val subject = HardwareRenderBufferSubject()

        subject.buffer.close()
        subject.buffer.close()

        subject.buffer.closed.shouldBeTrue()
        subject.surface.closed.shouldBeTrue()
        subject.source.stopCount shouldBe 1
        subject.source.surfaceWasOpenWhenStopped.shouldBeTrue()
    }

    test("A frame already in flight when close begins is recycled") {
        val subject = HardwareRenderBufferSubject()
        val bitmap = testBitmap()
        subject.buffer.close()

        subject.source.emitInFlightFrame(bitmap)

        verify(exactly = 1) { bitmap.recycle() }
    }

    test("Close completes remaining cleanup and preserves failures when source stop fails") {
        val stopFailure = IllegalStateException("Frame source stop failed")
        val recycleFailure = IllegalStateException("Bitmap recycle failed")
        val subject = HardwareRenderBufferSubject(stopFailure = stopFailure)
        val current = testBitmap()
        val pending = testBitmap(recycleFailure)
        subject.source.emitFrame(current)
        subject.buffer.consumeLatestBitmap()
        subject.source.emitFrame(pending)

        val error = shouldThrow<IllegalStateException> { subject.buffer.close() }

        error shouldBe stopFailure
        error.suppressed.toList() shouldContainExactly listOf(recycleFailure)
        subject.buffer.closed.shouldBeTrue()
        subject.surface.closed.shouldBeTrue()
        verify(exactly = 1) { pending.recycle() }
        verify(exactly = 1) { current.recycle() }
    }

    test("Operations after close throw the resource closed exception") {
        val subject = HardwareRenderBufferSubject()
        subject.buffer.close()

        shouldThrow<RiveResourceClosedException> {
            subject.buffer.render(subject.artboard, subject.stateMachine)
        }
        shouldThrow<RiveResourceClosedException> {
            subject.buffer.consumeLatestBitmap()
        }

        verify(exactly = 0) { subject.worker.draw(any(), any(), any(), any(), any()) }
    }

    test("Render rejects a closed artboard before drawing") {
        val subject = HardwareRenderBufferSubject()
        subject.artboard.close()

        shouldThrow<RiveResourceClosedException> {
            subject.buffer.render(subject.artboard, subject.stateMachine)
        }

        verify(exactly = 0) { subject.worker.draw(any(), any(), any(), any(), any()) }
    }

    test("Render rejects a closed state machine before drawing") {
        val subject = HardwareRenderBufferSubject()
        subject.stateMachine.close()

        shouldThrow<RiveResourceClosedException> {
            subject.buffer.render(subject.artboard, subject.stateMachine)
        }

        verify(exactly = 0) { subject.worker.draw(any(), any(), any(), any(), any()) }
    }

    test("Render rejects incompatible resource arguments before drawing") {
        val subject = HardwareRenderBufferSubject()

        shouldThrow<RiveIncompatibleResourceException> {
            subject.buffer.render(subject.foreignArtboard, subject.stateMachine)
        }
        shouldThrow<RiveIncompatibleResourceException> {
            subject.buffer.render(subject.artboard, subject.foreignStateMachine)
        }
        shouldThrow<RiveIncompatibleResourceException> {
            subject.buffer.render(subject.artboard, subject.siblingStateMachine)
        }

        verify(exactly = 0) { subject.worker.draw(any(), any(), any(), any(), any()) }
    }

    test("Render waits for an asynchronous first frame and skips later waits") {
        val subject = HardwareRenderBufferSubject(firstFrameTimeoutMillis = 1_000L)
        val bitmap = testBitmap()
        lateinit var publisher: Thread
        every {
            Trace.beginSection("Rive/RenderBuffer/Hardware/WaitFirstFrame")
        } answers {
            // This trace begins after draw returns and immediately before the bounded wait.
            publisher = Thread {
                subject.source.emitFrame(bitmap)
            }.apply { start() }
        }

        subject.buffer.render(subject.artboard, subject.stateMachine)
        publisher.join(1_000L)

        subject.buffer.consumeLatestBitmap() shouldBe bitmap
        publisher.isAlive shouldBe false

        subject.buffer.render(subject.artboard, subject.stateMachine)

        verify(exactly = 2) {
            subject.worker.draw(
                subject.artboard.artboardHandle,
                subject.stateMachine.stateMachineHandle,
                subject.surface,
                any(),
                any(),
            )
        }
        verify(exactly = 1) {
            Trace.beginSection("Rive/RenderBuffer/Hardware/WaitFirstFrame")
        }
    }

    test("Render reports first-frame acquisition failures") {
        val subject = HardwareRenderBufferSubject()
        val failure = IllegalStateException("Frame acquisition failed")
        every {
            subject.worker.draw(any(), any(), any(), any(), any())
        } answers {
            subject.source.emitFailure(failure)
        }

        val error = shouldThrow<RiveRenderException> {
            subject.buffer.render(subject.artboard, subject.stateMachine)
        }

        error.cause shouldBe failure
    }

    test("Render reports a first-frame timeout without sleeping") {
        val subject = HardwareRenderBufferSubject(firstFrameTimeoutMillis = 0L)

        shouldThrow<RiveRenderException> {
            subject.buffer.render(subject.artboard, subject.stateMachine)
        }
    }

    test("Consume returns null before a frame is published") {
        val subject = HardwareRenderBufferSubject()

        subject.buffer.consumeLatestBitmap() shouldBe null
    }

    test("Consume reports frame-source failures") {
        val subject = HardwareRenderBufferSubject()
        val failure = IllegalStateException("Frame acquisition failed")
        subject.source.emitFailure(failure)

        val error = shouldThrow<RiveRenderException> {
            subject.buffer.consumeLatestBitmap()
        }

        error.cause shouldBe failure
    }

    test("Publishing swaps frames and recycles superseded bitmaps") {
        val subject = HardwareRenderBufferSubject()
        val first = testBitmap()
        val second = testBitmap()
        val third = testBitmap()

        subject.source.emitFrame(first)
        subject.source.emitFrame(second)
        subject.buffer.consumeLatestBitmap() shouldBe second
        subject.source.emitFrame(third)
        subject.buffer.consumeLatestBitmap() shouldBe third
        subject.buffer.close()

        verify(exactly = 1) { first.recycle() }
        verify(exactly = 1) { second.recycle() }
        verify(exactly = 1) { third.recycle() }
    }

    test("Publishing emits frame availability") {
        val subject = HardwareRenderBufferSubject()

        coroutineScope {
            val frameAvailable = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(1_000L) { subject.buffer.frameAvailable.first() }
            }
            subject.source.emitFrame(testBitmap())
            frameAvailable.await()
        }
    }
})

private class HardwareRenderBufferSubject(
    firstFrameTimeoutMillis: Long = 0L,
    stopFailure: Throwable? = null,
) {
    val worker = mockk<CommandQueue>(relaxed = true)
    val foreignWorker = mockk<CommandQueue>(relaxed = true)
    val surface = TestRiveSurface(
        worker,
        width = HARDWARE_TEST_WIDTH,
        height = HARDWARE_TEST_HEIGHT,
        resizable = false,
    )
    val source = FakeHardwareFrameSource(surface, stopFailure = stopFailure)
    val artboard = Artboard(
        ArtboardHandle(HARDWARE_TEST_ARTBOARD_HANDLE),
        worker,
        FileHandle(HARDWARE_TEST_FILE_HANDLE),
        "Artboard",
    )
    val foreignArtboard = Artboard(
        ArtboardHandle(HARDWARE_TEST_ARTBOARD_HANDLE + 1L),
        foreignWorker,
        FileHandle(HARDWARE_TEST_FILE_HANDLE),
        "Foreign Artboard",
    )
    val stateMachine = StateMachine(
        StateMachineHandle(HARDWARE_TEST_STATE_MACHINE_HANDLE),
        worker,
        artboard.artboardHandle,
        "State Machine",
    )
    val foreignStateMachine = StateMachine(
        StateMachineHandle(HARDWARE_TEST_STATE_MACHINE_HANDLE + 1L),
        foreignWorker,
        foreignArtboard.artboardHandle,
        "Foreign State Machine",
    )
    val siblingStateMachine = StateMachine(
        StateMachineHandle(HARDWARE_TEST_STATE_MACHINE_HANDLE + 2L),
        worker,
        ArtboardHandle(HARDWARE_TEST_ARTBOARD_HANDLE + 2L),
        "Sibling State Machine",
    )
    val buffer = HardwareRenderBuffer.createForTesting(
        HARDWARE_TEST_WIDTH,
        HARDWARE_TEST_HEIGHT,
        worker,
        HardwareFrameSourceFactory { _, _, _ -> source },
        firstFrameTimeoutMillis = firstFrameTimeoutMillis,
    )
}

private class FakeHardwareFrameSource(
    override val surface: RiveSurface,
    private val listenerRegistrationFailure: Throwable? = null,
    private val stopFailure: Throwable? = null,
    private val frameDuringListenerRegistration: Bitmap? = null,
) : HardwareFrameSource {
    private var listener: HardwareFrameSource.Listener? = null
    private var lastListener: HardwareFrameSource.Listener? = null

    var stopCount = 0
        private set

    var surfaceWasOpenWhenStopped = false
        private set

    val hasListener: Boolean
        get() = listener != null

    override fun setListener(listener: HardwareFrameSource.Listener?) {
        this.listener = listener
        if (listener != null) {
            lastListener = listener
            frameDuringListenerRegistration?.let { listener.onFrame(it) }
            listenerRegistrationFailure?.let { throw it }
        }
    }

    override fun stop() {
        stopCount += 1
        surfaceWasOpenWhenStopped = !surface.closed
        listener = null
        stopFailure?.let { throw it }
    }

    /** Publishes [bitmap] to the current listener. */
    fun emitFrame(bitmap: Bitmap) {
        listener?.onFrame(bitmap)
    }

    /** Simulates a frame callback admitted before [stop] removed the listener. */
    fun emitInFlightFrame(bitmap: Bitmap) {
        lastListener?.onFrame(bitmap)
    }

    /** Publishes [failure] to the current listener. */
    fun emitFailure(failure: Throwable) {
        listener?.onFailure(failure)
    }
}

/** @return A mock bitmap that tracks recycled state. */
private fun testBitmap(recycleFailure: Throwable? = null): Bitmap {
    val bitmap = mockk<Bitmap>()
    var recycled = false
    every { bitmap.isRecycled } answers { recycled }
    every { bitmap.recycle() } answers {
        recycleFailure?.let { throw it }
        recycled = true
    }
    return bitmap
}

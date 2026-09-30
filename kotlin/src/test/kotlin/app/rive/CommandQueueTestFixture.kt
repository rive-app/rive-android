package app.rive

import app.rive.core.ArtboardHandle
import app.rive.core.CommandQueue
import app.rive.core.CommandQueueBridge
import app.rive.core.Listeners
import app.rive.core.RenderContext
import app.rive.core.StateMachineHandle
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

internal const val COMMAND_QUEUE_ADDR = 1L
internal const val RENDER_CONTEXT_ADDR = 2L
internal const val HANDLE_NUM = 123L
internal const val ARTBOARD_HANDLE_NUM = 456L

/**
 * Provides the baseline mocks and coroutine environment shared by command-queue unit tests.
 *
 * Tests remain responsible for stubbing the native operation they exercise. This fixture only
 * supplies the successful command-queue construction behavior common to every functional area.
 */
internal class CommandQueueTestFixture {
    val renderContextMock = mockk<RenderContext>()
    val listenersMock = mockk<Listeners>()
    val commandQueueBridgeMock = mockk<CommandQueueBridge>()

    /** Installs the test dispatcher and baseline native bridge behavior before each test. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun setUp() {
        // CommandQueue resumes native requests on Main, which local unit tests do not provide.
        Dispatchers.setMain(UnconfinedTestDispatcher())

        every { renderContextMock.nativeObjectPointer } returns RENDER_CONTEXT_ADDR
        every { renderContextMock.close() } just runs

        every { listenersMock.close() } just runs

        every { commandQueueBridgeMock.cppConstructor(any()) } returns COMMAND_QUEUE_ADDR
        every { commandQueueBridgeMock.cppDelete(any()) } just runs
        every { commandQueueBridgeMock.isCurrentThreadCommandServer(any()) } returns false
        every {
            commandQueueBridgeMock.cppCreateListeners(
                COMMAND_QUEUE_ADDR,
                any()
            )
        } returns listenersMock
        every { commandQueueBridgeMock.cppSetTracingEnabled(any(), any()) } just runs
        every { commandQueueBridgeMock.cppSetDeferredEnabled(any(), any()) } just runs
        every { commandQueueBridgeMock.cppCancelDraw(any(), any()) } just runs
        every { commandQueueBridgeMock.cppRunOnCommandServer(any(), any()) } just runs
    }

    /**
     * Creates a state machine on [commandQueue] through confirmed creation, so its durable state
     * is registered as it would be in production.
     *
     * @param commandQueue The queue that should own the state machine.
     * @return The confirmed handle, backed by [HANDLE_NUM].
     */
    suspend fun registerStateMachine(commandQueue: CommandQueue): StateMachineHandle =
        coroutineScope {
            val requestID = slot<Long>()
            every {
                commandQueueBridgeMock.cppCreateDefaultStateMachine(
                    COMMAND_QUEUE_ADDR,
                    capture(requestID),
                    ARTBOARD_HANDLE_NUM
                )
            } returns HANDLE_NUM
            val creation = async(start = CoroutineStart.UNDISPATCHED) {
                commandQueue.createDefaultStateMachineConfirmed(ArtboardHandle(ARTBOARD_HANDLE_NUM))
            }
            commandQueue.onStateMachineInstantiated(
                requestID.captured,
                StateMachineHandle(HANDLE_NUM)
            )
            creation.await()
        }

    /** Restores the coroutine environment and clears mocks after each test. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }
}

/**
 * Installs a [CommandQueueTestFixture] into this specification.
 *
 * @return The fixture whose mocks can be configured by individual tests.
 */
internal fun FunSpec.installCommandQueueTestFixture(): CommandQueueTestFixture =
    CommandQueueTestFixture().also { fixture ->
        beforeTest {
            fixture.setUp()
        }
        afterTest {
            fixture.tearDown()
        }
    }

package app.rive

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.core.CommandQueue
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.nanoseconds

@RunWith(AndroidJUnit4::class)
class CommandQueueSurfaceOrderingTest {
    @Before
    fun setup() {
        Rive.init(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun drawThenDestroySurface_stressDoesNotBreakQueue() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val commandQueue = CommandQueue()

        val keepPolling = java.util.concurrent.atomic.AtomicBoolean(true)
        val pollThread = thread(name = "RivePollTest") {
            while (keepPolling.get()) {
                commandQueue.pollMessages()
                Thread.sleep(1)
            }
        }

        try {
            val bytes = context.resources.openRawResource(R.raw.state_machine_configurations)
                .use { it.readBytes() }
            val fileHandle = runBlocking { commandQueue.loadFile(bytes) }

            val artboardNames = runBlocking { commandQueue.getArtboardNames(fileHandle) }
            assertTrue("Expected at least one artboard", artboardNames.isNotEmpty())
            val artboardHandle = commandQueue.createArtboardByName(fileHandle, artboardNames.first())

            val stateMachineNames = runBlocking { commandQueue.getStateMachineNames(artboardHandle) }
            assertTrue("Expected at least one state machine", stateMachineNames.isNotEmpty())
            val stateMachineHandle =
                commandQueue.createStateMachineByName(artboardHandle, stateMachineNames.first())
            commandQueue.advanceStateMachine(stateMachineHandle, 0.nanoseconds)

            // Repeatedly enqueue draw+destroy back-to-back, the ordering that
            // previously could use a torn-down render target.
            repeat(200) {
                val surface = commandQueue.createImageSurface(64, 64)
                commandQueue.draw(
                    artboardHandle,
                    stateMachineHandle,
                    surface,
                    Fit.Contain(),
                    Color.TRANSPARENT
                )
                commandQueue.destroyRiveSurface(surface)
            }

            commandQueue.deleteStateMachine(stateMachineHandle)
            commandQueue.deleteArtboard(artboardHandle)
            commandQueue.deleteFile(fileHandle)
        } finally {
            keepPolling.set(false)
            pollThread.join(2000)
            commandQueue.release("CommandQueueSurfaceOrderingTest", "Test cleanup")
        }
    }
}

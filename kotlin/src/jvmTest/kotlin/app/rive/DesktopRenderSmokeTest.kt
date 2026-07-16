package app.rive

import app.rive.core.RiveWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration

class DesktopRenderSmokeTest {
    @Test
    fun renders_a_riv_frame_offscreen() = runBlocking {
        val worker = RiveWorker(renderBackend = RenderBackend.Vulkan)
        val polling = launch(Dispatchers.Main) {
            while (isActive) {
                worker.pollMessages()
                delay(1)
            }
        }
        try {
            val bytes = assertNotNull(
                javaClass.classLoader.getResourceAsStream("basketball.riv"),
                "basketball.riv test resource missing"
            ).readBytes()

            withContext(Dispatchers.Main) {
                val file = worker.loadFile(bytes)
                val artboard = worker.createDefaultArtboard(file)
                val stateMachine = worker.createDefaultStateMachine(artboard)
                worker.advanceStateMachine(stateMachine, Duration.ZERO)

                val width = 200
                val height = 200
                val surface = worker.createImageSurface(width, height)
                val buffer = ByteArray(width * height * 4)
                worker.drawToBuffer(artboard, stateMachine, surface, buffer, width, height)

                val nonZero = buffer.count { it != 0.toByte() }
                assertTrue(
                    nonZero > width * height, // more than 25% of one channel
                    "Rendered frame appears empty ($nonZero non-zero bytes)"
                )

                surface.close()
                worker.deleteStateMachine(stateMachine)
                worker.deleteArtboard(artboard)
                worker.deleteFile(file)
            }
        } finally {
            polling.cancel()
            worker.release("DesktopRenderSmokeTest")
        }
    }
}

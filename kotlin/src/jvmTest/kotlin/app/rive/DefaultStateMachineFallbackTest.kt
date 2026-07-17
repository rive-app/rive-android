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

class DefaultStateMachineFallbackTest {
    /**
     * off_road_car_blog.riv is an older export whose artboard has a state machine but no
     * default state machine set. The command server falls back to the first state machine
     * (see the command_server.cpp submodule patch); without it, every advance/draw fails.
     */
    @Test
    fun renders_file_whose_artboard_has_no_default_state_machine() = runBlocking {
        val worker = RiveWorker(renderBackend = RenderBackend.Vulkan)
        val polling = launch(Dispatchers.Main) {
            while (isActive) {
                worker.pollMessages()
                delay(1)
            }
        }
        try {
            val bytes = assertNotNull(
                javaClass.classLoader.getResourceAsStream("off_road_car_blog.riv"),
                "off_road_car_blog.riv test resource missing"
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
                    nonZero > width * height,
                    "Rendered frame appears empty ($nonZero non-zero bytes)"
                )

                surface.close()
                worker.deleteStateMachine(stateMachine)
                worker.deleteArtboard(artboard)
                worker.deleteFile(file)
            }
        } finally {
            polling.cancel()
            worker.release("DefaultStateMachineFallbackTest")
        }
    }
}

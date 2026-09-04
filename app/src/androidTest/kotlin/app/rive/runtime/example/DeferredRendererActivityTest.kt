package app.rive.runtime.example

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.rive.RenderBackend
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises GPU Canvas file switching through the deferred-renderer sample activity. */
@RunWith(AndroidJUnit4::class)
class DeferredRendererActivityTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /** Verifies file switches plus recreation rebuild each OpenGL-owned resource tree. */
    @Test
    fun switchingFiles_recreatesEachResourceTree() {
        prepareDevice()
        val scenario = ActivityScenario.launch(DeferredRendererActivity::class.java)
        try {
            var selectedFile = "ore.riv"
            awaitContent(selectedFile, RenderBackend.OpenGL)

            repeat(10) {
                selectedFile = selectedFile.otherFile()
                clickButton(selectedFile)
                awaitContent(selectedFile, RenderBackend.OpenGL)
            }

            scenario.recreate()
            awaitContent(selectedFile, RenderBackend.OpenGL)
        } finally {
            scenario.close()
        }
    }

    /** Verifies the sample can switch between OpenGL and Vulkan on a device or emulator. */
    @Test
    fun switchingBackends_recreatesTheResourceTree() {
        prepareDevice()
        val scenario = ActivityScenario.launch(DeferredRendererActivity::class.java)
        try {
            awaitContent("ore.riv", RenderBackend.OpenGL)

            clickButton("Vulkan")
            awaitContent("ore.riv", RenderBackend.Vulkan)

            clickButton("OpenGL")
            awaitContent("ore.riv", RenderBackend.OpenGL)
        } finally {
            scenario.close()
        }
    }

    /** Wakes and unlocks the emulator so UiAutomator has an active accessibility hierarchy. */
    private fun prepareDevice() {
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
    }

    /**
     * Clicks the visible selector button for a bundled Rive file.
     *
     * @param buttonText The filename displayed by the selector button.
     */
    private fun clickButton(buttonText: String) {
        val button = device.wait(Until.findObject(By.text(buttonText)), TIMEOUT_MILLIS)
        assertNotNull("Timed out waiting for the $buttonText selector", button)
        button!!.click()
    }

    /**
     * Waits until the selected file has created its complete resource tree and Rive content.
     *
     * @param fileName The selected Rive filename.
     * @param renderBackend The selected rendering backend.
     */
    private fun awaitContent(fileName: String, renderBackend: RenderBackend) {
        val contentDescription = DeferredRendererContentDescriptions.forFile(
            fileName,
            renderBackend,
        )
        assertTrue(
            "Timed out waiting for $contentDescription",
            device.wait(Until.hasObject(By.desc(contentDescription)), TIMEOUT_MILLIS),
        )
    }

    /** @return The other bundled Rive filename. */
    private fun String.otherFile(): String =
        if (this == "ore.riv") "multi-stage.riv" else "ore.riv"

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}

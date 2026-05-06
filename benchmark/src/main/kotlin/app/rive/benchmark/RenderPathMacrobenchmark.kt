package app.rive.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RenderPathMacrobenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    // benchmark-macro 1.2.4 does not expose CompilationMode.SpeedProfile().
    // This is the equivalent setup: collect profile during warmup, no baseline profile.
    private val speedProfileCompilationMode = CompilationMode.Partial(
        baselineProfileMode = BaselineProfileMode.Disable,
        warmupIterations = 3
    )

    @Test
    fun startup_compose() = measureStartup(Path.Compose)

    @Test
    fun startup_hardware_canvas() = measureStartup(Path.HardwareCanvas)

    @Test
    fun frame_compose() = measureFrame(Path.Compose)

    @Test
    fun frame_hardware_canvas() = measureFrame(Path.HardwareCanvas)

    private fun measureStartup(path: Path) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = speedProfileCompilationMode,
            iterations = 10,
            startupMode = StartupMode.COLD,
            setupBlock = {
                device.pressHome()
            }
        ) {
            launchActivity(path)
        }
    }

    private fun measureFrame(path: Path) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = speedProfileCompilationMode,
            iterations = 12,
            startupMode = StartupMode.WARM,
            setupBlock = {
                device.pressHome()
                launchActivity(path)
            }
        ) {
            device.waitForIdle()
            Thread.sleep(5_000)
        }
    }

    private fun launchActivity(path: Path) {
        val launchOutput = device.executeShellCommand(path.launchCommand())
        check(launchOutput.contains("Status: ok")) {
            "Failed to launch ${path.activityClassName}: $launchOutput"
        }
        device.waitForIdle()
    }

    private enum class Path(val activityClassName: String) {
        Compose("app.rive.benchmark.BenchmarkComposeActivity"),
        HardwareCanvas("app.rive.benchmark.BenchmarkHardwareBitmapCanvasActivity");

        fun launchCommand(): String = buildString {
            append("am start -W")
            append(" -a android.intent.action.MAIN")
            append(" -c android.intent.category.LAUNCHER")
            append(" -f 0x10008000")
            append(" -n ")
            append(TARGET_PACKAGE)
            append("/")
            append(activityClassName)
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "app.rive.runtime.example"
    }
}

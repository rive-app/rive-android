package app.rive

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import app.rive.runtime.kotlin.test.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith

/** Exercises artboard operations whose results cross the JNI boundary. */
@RunWith(AndroidJUnit4::class)
class ArtboardTest : RiveAndroidTest() {
    /** Verifies native state-machine metadata is returned for the selected artboard. */
    @Test
    fun getStateMachineNames_returnsNativeNames() = runBlocking {
        val resources = loadRiveResources(
            rawResourceId = R.raw.multiple_state_machines,
            stateMachineName = "one",
        )
        val names = resources.artboard.getStateMachineNames()

        assertEquals(4, names.size)
        assertEquals(setOf("one", "two", "three", "four"), names.toSet())
    }

    /** Verifies the native query observes both resize and reset on the same instance. */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    @OptIn(ExperimentalHardwareBitmapRendering::class)
    fun getSize_tracksResizeAndReset() = runBlocking {
        val artboard = loadRiveResources(
            rawResourceId = R.raw.multiple_state_machines,
            stateMachineName = "one",
        ).artboard
        assertEquals(ArtboardSize(500f, 500f), artboard.getSize())

        HardwareRenderBuffer(201, 103, riveWorker).use { buffer ->
            artboard.resizeArtboard(buffer.surface, scaleFactor = 2f)
            assertEquals(ArtboardSize(100.5f, 51.5f), artboard.getSize())

            artboard.resetArtboardSize()
            assertEquals(ArtboardSize(500f, 500f), artboard.getSize())
        }
    }
}

package app.rive

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.test.R
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals

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
}

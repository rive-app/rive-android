package app.rive

import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.core.CommandQueue
import org.junit.Rule
import org.junit.runner.RunWith
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CommandQueueComposeTest : RiveAndroidTest() {
    @BeforeTest
    fun setup() {
        // Disable frames to avoid tests not terminating due to rememberCommandQueue's polling loop
        composeRule.mainClock.autoAdvance = false
    }

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun releases_when_removed_from_tree() {
        lateinit var queue: CommandQueue
        lateinit var show: MutableState<Boolean>

        composeRule.setContent {
            show = remember { mutableStateOf(true) }
            if (show.value) {
                queue = rememberRiveWorker(autoPoll = false)
            }
        }

        assertEquals(1, queue.refCount)
        assertFalse(queue.isDisposed)

        // Remove from the Compose tree
        composeRule.runOnUiThread { show.value = false }

        composeRule.mainClock.advanceTimeByFrame()

        assertEquals(0, queue.refCount)
        assertTrue(queue.isDisposed)
    }
}

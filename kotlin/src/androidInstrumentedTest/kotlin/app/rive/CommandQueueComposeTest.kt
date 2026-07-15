package app.rive

import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.core.CommandQueue
import app.rive.test.R
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

    @Test
    fun rememberRiveFile_reloads_when_worker_changes() {
        val firstWorker = CommandQueue()
        val secondWorker = CommandQueue()
        lateinit var activeWorker: MutableState<CommandQueue>
        var showContent: MutableState<Boolean>? = null
        var fileResult: Result<RiveFile> = Result.Loading

        composeRule.mainClock.autoAdvance = true

        fun waitForFileLoadedBy(worker: CommandQueue) {
            composeRule.waitUntil(timeoutMillis = 5_000) {
                worker.pollMessages()
                (fileResult as? Result.Success)?.value?.riveWorker === worker
            }
        }

        try {
            val source = RiveFileSource.RawRes(R.raw.empty, context.resources)

            composeRule.setContent {
                activeWorker = remember { mutableStateOf(firstWorker) }
                val activeShowContent = remember { mutableStateOf(true) }
                showContent = activeShowContent
                if (activeShowContent.value) {
                    fileResult = rememberRiveFile(source, activeWorker.value)
                }
            }

            waitForFileLoadedBy(firstWorker)

            composeRule.runOnUiThread {
                activeWorker.value = secondWorker
            }

            waitForFileLoadedBy(secondWorker)

            assertEquals(1, firstWorker.refCount)
            assertEquals(2, secondWorker.refCount)
        } finally {
            showContent?.let { activeShowContent ->
                composeRule.runOnUiThread {
                    activeShowContent.value = false
                }
            }
            composeRule.waitForIdle()

            if (!firstWorker.isDisposed) {
                firstWorker.release(javaClass.simpleName, "Test cleanup")
            }
            if (!secondWorker.isDisposed) {
                secondWorker.release(javaClass.simpleName, "Test cleanup")
            }
        }
    }
}

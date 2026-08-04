package app.rive

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.test.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class RiveViewResourceValidationTest : RiveAndroidTest() {
    @Test
    fun setRiveFile_rejectsClosedResources() = runBlocking<Unit> {
        val closedFileResources = loadDefaultRiveResources(R.raw.empty)
        val closedArtboardResources = loadDefaultRiveResources(R.raw.empty)
        closedFileResources.file.close()
        closedArtboardResources.artboard.close()

        withContext(Dispatchers.Main.immediate) {
            val view = RiveView(context)
            assertFailsWith<RiveResourceClosedException> {
                view.setRiveFile(closedFileResources.file)
            }
            assertFailsWith<RiveResourceClosedException> {
                view.setRiveFile(
                    closedArtboardResources.file,
                    closedArtboardResources.artboard,
                )
            }
        }
    }

    @Test
    fun setRiveFile_rejectsArtboardFromAnotherFile() = runBlocking<Unit> {
        val first = loadDefaultRiveResources(R.raw.empty)
        val second = loadDefaultRiveResources(R.raw.empty)

        withContext(Dispatchers.Main.immediate) {
            val view = RiveView(context)
            assertFailsWith<RiveIncompatibleResourceException> {
                view.setRiveFile(first.file, second.artboard)
            }
        }
    }
}

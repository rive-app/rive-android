package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RiveArtboardVolumeTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context

    @Test
    fun artboardVolume() {
        val file = File(appContext.resources.openRawResource(R.raw.audio_test).readBytes())
        val artboard = file.firstArtboard

        assertEquals(1f, artboard.volume)

        artboard.volume = 0.5f
        assertEquals(0.5f, artboard.volume)

        artboard.volume = 0f
        assertEquals(0.0f, artboard.volume)
    }
}

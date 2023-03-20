package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.RiveArtboardRenderer
import app.rive.runtime.kotlin.core.errors.MalformedFileException
import app.rive.runtime.kotlin.core.errors.UnsupportedRuntimeVersionException
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RiveFileLoadTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context
    private lateinit var mockRenderer: RiveArtboardRenderer

    @Before
    fun init() {
        mockRenderer = TestUtils.MockArtboardRenderer()
    }

    @Test(expected = UnsupportedRuntimeVersionException::class)
    fun loadFormat6() {
        File(appContext.resources.openRawResource(R.raw.sample6).readBytes())
        assert(false)
    }

    @Test(expected = MalformedFileException::class)
    fun loadJunk() {
        File(appContext.resources.openRawResource(R.raw.junk).readBytes())
        assert(false)
    }

    @Test
    fun loadFormatFlux() {
        val file = File(appContext.resources.openRawResource(R.raw.flux_capacitor).readBytes())
        assertEquals(1, file.firstArtboard.animationCount)
    }

    @Test
    fun loadFormatBuggy() {
        val file = File(appContext.resources.openRawResource(R.raw.off_road_car_blog).readBytes())
        assertEquals(5, file.firstArtboard.animationCount)
    }
}
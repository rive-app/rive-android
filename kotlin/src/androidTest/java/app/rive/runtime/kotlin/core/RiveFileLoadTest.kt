package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.core.errors.*
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RiveFileLoadTest {

    @Test(expected = UnsupportedRuntimeVersionException::class)
    fun loadFormat6() {
        val appContext = initTests()
        File(appContext.resources.openRawResource(R.raw.sample6).readBytes())
        assert(false);
    }

    @Test(expected = MalformedFileException::class)
    fun loadJunk() {
        val appContext = initTests()
        File(appContext.resources.openRawResource(R.raw.junk).readBytes())
        assert(false);
    }

    @Test
    fun loadFormatFlux() {
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.flux_capacitor).readBytes())
        assertEquals(1, file.firstArtboard.animationCount);
    }

    @Test
    fun loadFormatBuggy() {
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.off_road_car_blog).readBytes())
        assertEquals(5, file.firstArtboard.animationCount);
    }
}
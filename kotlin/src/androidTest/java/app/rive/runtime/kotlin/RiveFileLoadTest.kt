package app.rive.runtime.kotlin

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RiveFileLoadTest {

    @Test(expected = RiveException::class)
    fun loadFormat6() {
        val appContext = initTests()
        File(appContext.resources.openRawResource(R.raw.sample6).readBytes())
        assert(false);
    }

    @Test(expected = RiveException::class)
    fun loadJunk() {
        val appContext = initTests()
        File(appContext.resources.openRawResource(R.raw.junk).readBytes())
        assert(false);
    }

    @Test
    fun loadFormatFlux() {
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.flux_capacitor).readBytes())
        assertEquals(file.artboard().animationCount(), 1);
    }

    @Test
    fun loadFormatBuggy() {
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.off_road_car_blog).readBytes())
        assertEquals(file.artboard().animationCount(), 5);
    }
}
package app.rive.runtime.kotlin

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RiveArtboardLoadTest {
    @Test
    fun loadArtboard() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("app.rive.runtime.kotlin.test", appContext.packageName)

        Rive.init()
        var file = File(appContext.resources.openRawResource(R.raw.multipleartboards).readBytes())
        file.artboard()
    }

    @Test(expected = RiveException::class)
    fun loadArtboardNoArtboard() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("app.rive.runtime.kotlin.test", appContext.packageName)

        Rive.init()
        var file = File(appContext.resources.openRawResource(R.raw.noartboard).readBytes())
        file.artboard()
    }

    @Test
    fun loadArtboardOne() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("app.rive.runtime.kotlin.test", appContext.packageName)

        Rive.init()
        var file = File(appContext.resources.openRawResource(R.raw.multipleartboards).readBytes())
        var artboard1 = file.artboard(name = "artboard1")
        assertEquals(artboard1.animationCount(), 1);
    }

    @Test
    fun loadArtboardTwo() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("app.rive.runtime.kotlin.test", appContext.packageName)

        Rive.init()
        var file = File(appContext.resources.openRawResource(R.raw.multipleartboards).readBytes())
        var artboard2 = file.artboard(name = "artboard2")
        assertEquals(artboard2.animationCount(), 2);
    }

    @Test(expected = RiveException::class)
    fun loadArtboardThree() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("app.rive.runtime.kotlin.test", appContext.packageName)

        Rive.init()
        var file = File(appContext.resources.openRawResource(R.raw.multipleartboards).readBytes())
        var artboard3 = file.artboard(name = "artboard3")
        assertEquals(artboard3.animationCount(), 3);
    }
}
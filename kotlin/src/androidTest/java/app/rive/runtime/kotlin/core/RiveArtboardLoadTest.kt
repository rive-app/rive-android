package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RiveArtboardLoadTest {
    @Test
    fun loadArtboard() {
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.multipleartboards).readBytes())
        // Access an artboard can bail when we don't have one.
        file.firstArtboard
        assertEquals(2, file.artboardCount);
        // Note index order seems to be reversed.
        assertEquals(
            file.artboard(name = "artboard1").cppPointer,
            file.artboard(1).cppPointer
        )
        assertEquals(
            file.artboard(name = "artboard2").cppPointer,
            file.artboard(0).cppPointer
        )
        assertEquals(listOf<String>("artboard2", "artboard1"), file.artboardNames)
    }

    @Test(expected = RiveException::class)
    fun loadArtboardNoArtboard() {
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.noartboard).readBytes())
        file.firstArtboard
    }

    @Test
    fun loadArtboardNoArtboardCheck() {
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.noartboard).readBytes())
        assertEquals(0, file.artboardCount);
        assertEquals(listOf<String>(), file.artboardNames)
    }

    @Test
    fun loadArtboardOne() {
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.multipleartboards).readBytes())
        var artboard1 = file.artboard(name = "artboard1")
        assertEquals(1, artboard1.animationCount);
        assertEquals(1, artboard1.stateMachineCount);
        assertEquals("artboard1", artboard1.name);
        assertEquals(500f, artboard1.bounds.height);
        assertEquals(500f, artboard1.bounds.width);
    }

    @Test
    fun loadArtboardTwo() {
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.multipleartboards).readBytes())
        var artboard2 = file.artboard(name = "artboard2")
        assertEquals(2, artboard2.animationCount);
        assertEquals(2, artboard2.stateMachineCount);
        assertEquals("artboard2", artboard2.name);
    }

    @Test(expected = RiveException::class)
    fun loadArtboardThree() {
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.multipleartboards).readBytes())
        file.artboard(name = "artboard3")
    }

    @Test(expected = RiveException::class)
    fun loadArtboardThreeAlt() {
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.multipleartboards).readBytes())
        file.artboard(2)
    }

    @Test
    fun loadShapesRect() {
        // TODO: access properties once exposed & attempt drawing?
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.shapes).readBytes())
        file.artboard(name = "rect")
        // can we do a draw check?
    }

    @Test
    fun loadShapesEllipse() {
        // TODO: access properties once exposed & attempt drawing?
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.shapes).readBytes())
        file.artboard(name = "ellipse")
    }

    @Test
    fun loadShapesTriangle() {
        // TODO: access properties once exposed & attempt drawing?
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.shapes).readBytes())
        file.artboard(name = "triangle")
    }

    @Test
    fun loadShapesPolygon() {
        // TODO: access properties once exposed & attempt drawing?
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.shapes).readBytes())
        file.artboard(name = "polygon")
    }

    @Test
    fun loadShapesStar() {
        // TODO: access properties once exposed & attempt drawing?
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.shapes).readBytes())
        file.artboard(name = "star")
    }

    @Test
    fun loadShapesPen() {
        // TODO: access properties once exposed & attempt drawing?¬
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.shapes).readBytes())
        file.artboard(name = "pen")
    }

    @Test
    fun loadShapesGroups() {
        // TODO: access properties once exposed & attempt drawing?¬
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.shapes).readBytes())
        file.artboard(name = "groups")
    }

    @Test
    fun loadShapesBone() {
        // TODO: access properties once exposed & attempt drawing?¬
        val appContext = initTests()
        var file = File(appContext.resources.openRawResource(R.raw.shapes).readBytes())
        file.artboard(name = "bone")
    }
}
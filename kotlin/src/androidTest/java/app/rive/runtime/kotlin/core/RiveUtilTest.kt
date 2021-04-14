package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RiveUtilTest {

    @Test
    fun testContainSquareInSquareContainCenter() {
        Rive.init()

        var usedBounds = Rive.calculateRequiredBounds(
            Fit.CONTAIN,
            Alignment.CENTER,
            AABB(100f, 100f),
            AABB(50f, 50f),
        )
        assertEquals(100f, usedBounds.width)
        assertEquals(100f, usedBounds.height)
    }

    @Test
    fun testContainSquareInRectCoverCenter() {
        Rive.init()

        var usedBounds = Rive.calculateRequiredBounds(
            Fit.COVER,
            Alignment.CENTER,
            AABB(100f, 150f),
            AABB(50f, 50f),
        )
        assertEquals(150f, usedBounds.width)
        assertEquals(150f, usedBounds.height)
    }

    @Test
    fun testContainSquareInRectContainCenter() {
        Rive.init()

        var usedBounds = Rive.calculateRequiredBounds(
            Fit.CONTAIN,
            Alignment.CENTER,
            AABB(100f, 150f),
            AABB(50f, 50f),
        )
        assertEquals(100f, usedBounds.width)
        assertEquals(100f, usedBounds.height)
    }
}
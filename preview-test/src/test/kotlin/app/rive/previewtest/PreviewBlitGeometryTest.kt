package app.rive.previewtest

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Reproduces the Android Studio preview blit geometry without Rive: a bitmap containing a
 * mathematically perfect circle, created and drawn exactly like the Rive inspection presenter
 * does. If the circle renders as an oval, the distortion is in the bitmap/draw pipeline.
 */
class PreviewBlitGeometryTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    @Test
    fun syntheticCircleBlit() {
        paparazzi.snapshot {
            SyntheticBlitBox()
        }
    }
}

@Composable
private fun SyntheticBlitBox() {
    var size by remember { mutableStateOf(IntSize.Zero) }
    Layout(
        content = {},
        modifier = Modifier
            .size(250.dp)
            .onSizeChanged { size = it }
            .drawBehind {
                if (size.width > 0 && size.height > 0) {
                    val bitmap = circleBitmap(size.width, size.height)
                    drawImage(bitmap.asImageBitmap())
                }
            }
    ) { _, constraints ->
        layout(constraints.maxWidth, constraints.maxHeight) {}
    }
}

private fun circleBitmap(width: Int, height: Int): Bitmap {
    val argb = IntArray(width * height) { 0xFF303030.toInt() }
    val white = 0xFFFFFFFF.toInt()
    for (x in 0 until width) {
        argb[x] = white
        argb[(height - 1) * width + x] = white
        argb[(height / 2) * width + x] = white
    }
    for (y in 0 until height) {
        argb[y * width] = white
        argb[y * width + width - 1] = white
        argb[y * width + width / 2] = white
    }
    val cx = width / 2.0
    val cy = height / 2.0
    val r = minOf(width, height) / 4.0
    var angle = 0.0
    while (angle < 2 * PI) {
        val x = (cx + r * cos(angle)).toInt()
        val y = (cy + r * sin(angle)).toInt()
        if (x in 0 until width && y in 0 until height) {
            argb[y * width + x] = white
        }
        angle += 0.002
    }
    return Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)
}

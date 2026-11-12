package app.rive.runtime.kotlin.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.RiveImageException
import app.rive.core.RiveWorker
import app.rive.core.assertDisposed
import app.rive.runtime.kotlin.test.R
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.InflaterInputStream
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks the premultiplied sRGB RGBA bytes that native code receives from Android's decoder, and
 * that running out of memory fails a decode instead of crashing.
 */
@RunWith(AndroidJUnit4::class)
class ImageDecoderTest {
    private val resources = TestUtils().context.resources
    private val malformedPng = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x01, 0x02, 0x03)

    @Test
    fun eightBitRgbaPremultiplies() {
        val png = raw(R.raw.image_decode_rgba8)
        assertEquals(Bitmap.Config.ARGB_8888, decodedConfig(png))
        assertMatchesSource(png, tolerance = 1)
    }

    @Test
    fun sixteenBitRgbaConvertsThroughTheFallback() {
        val png = raw(R.raw.image_decode_rgba16)
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        assertEquals(Bitmap.Config.RGBA_F16, decodedConfig(png))
        assertMatchesSource(png, tolerance = 2)
    }

    @Test
    fun sixteenBitRgbConvertsThroughTheFallback() {
        val png = raw(R.raw.image_decode_rgb16)
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        assertEquals(Bitmap.Config.RGBA_F16, decodedConfig(png))
        assertMatchesSource(png, tolerance = 1)
    }

    @Test
    fun displayP3ConvertsToSrgb() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        // Saturated, so its Display P3 values differ clearly from its sRGB ones.
        val srgb = floatArrayOf(0.95f, 0.1f, 0.1f)
        val p3 = ColorSpace.connect(
            ColorSpace.get(ColorSpace.Named.SRGB),
            ColorSpace.get(ColorSpace.Named.DISPLAY_P3),
        ).transform(srgb.copyOf())
        val bitmap = Bitmap.createBitmap(
            5,
            3,
            Bitmap.Config.ARGB_8888,
            true,
            ColorSpace.get(ColorSpace.Named.DISPLAY_P3),
        )
        val stored = ByteArray(5 * 3 * 4) { i -> if (i % 4 == 3) -1 else to8(p3[i % 4]).toByte() }
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(stored))
        val png = ByteArrayOutputStream().also {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        assertTrue("P3 should store different values", abs(to8(p3[1]) - to8(srgb[1])) > 10)

        val decoded = decode(png)!!
        assertEquals(5, decoded.width)
        assertEquals(3, decoded.height)
        for (i in decoded.rgba.indices) {
            val expected = if (i % 4 == 3) 255 else to8(srgb[i % 4])
            assertNear(expected, decoded.rgba[i], tolerance = 2, "byte $i")
        }
    }

    @Test
    fun malformedInputFails() {
        assertNull(decode(malformedPng))
    }

    @Test
    fun failedByteArrayAllocationFailsTheDecode() {
        val heap = Runtime.getRuntime().maxMemory()
        val encoded = paddedPng(heap / 8)
        var thrown: OutOfMemoryError? = null
        val decoded = withHeapNearlyFull(free = heap / 32) {
            try {
                decode(encoded)
            } catch (e: OutOfMemoryError) {
                thrown = e
                null
            }
        }
        assertNull(thrown)
        assertNull(decoded)
        assertNotNull(decode(encoded))
    }

    @Test
    fun failedAllocationOnTheWorkerFailsOnlyThatDecode() {
        val heap = Runtime.getRuntime().maxMemory()
        val encoded = paddedPng(heap / 8)
        val worker = RiveWorker()
        try {
            worker.run {
                runBlocking {
                    withHeapNearlyFull(free = heap / 32) {
                        assertFailsWith<RiveImageException> { worker.decodeImage(encoded) }
                    }
                    worker.decodeImage(encoded)
                }
            }
        } finally {
            worker.release("ImageDecoderTest", "Test cleanup")
            assertDisposed(worker)
        }
    }

    @Test
    fun canvasMalformedInputFails() {
        assertFalse(NativeRendererTestHelper.cppDecodeCanvasImage(malformedPng))
        assertTrue(NativeRendererTestHelper.cppDecodeCanvasImage(raw(R.raw.image_decode_rgba8)))
    }

    @Test
    fun canvasFailedByteArrayAllocationFailsTheDecode() {
        val heap = Runtime.getRuntime().maxMemory()
        val encoded = paddedPng(heap / 8)
        var thrown: OutOfMemoryError? = null
        val decoded = withHeapNearlyFull(free = heap / 32) {
            try {
                NativeRendererTestHelper.cppDecodeCanvasImage(encoded)
            } catch (e: OutOfMemoryError) {
                thrown = e
                false
            }
        }
        assertNull(thrown)
        assertFalse(decoded)
        assertTrue(NativeRendererTestHelper.cppDecodeCanvasImage(encoded))
    }

    @Test
    fun canvasDecoderOutOfMemoryFailsTheDecode() {
        val png = raw(R.raw.image_decode_rgba8)
        NativeRendererTestHelper.cppSimulateCanvasDecoderOutOfMemory(true)
        val decoded = try {
            NativeRendererTestHelper.cppDecodeCanvasImage(png)
        } finally {
            NativeRendererTestHelper.cppSimulateCanvasDecoderOutOfMemory(false)
        }
        assertFalse(decoded)
        assertTrue(NativeRendererTestHelper.cppDecodeCanvasImage(png))
    }

    /**
     * Returns a valid PNG padded with zeros to [size] bytes, so only running out of memory can
     * fail its decode.
     */
    private fun paddedPng(size: Long) = raw(R.raw.image_decode_rgba8).copyOf(size.toInt())

    /**
     * Runs [block] while the Java heap has about [free] bytes left.
     *
     * Fills the heap in 1 MB steps without exhausting it, so other threads, such as ART's
     * `FinalizerDaemon`, can still allocate.
     */
    private inline fun <T> withHeapNearlyFull(free: Long, block: () -> T): T {
        val chunk = 1 shl 20
        val runtime = Runtime.getRuntime()
        runtime.gc()
        val filler = mutableListOf<ByteArray>()
        while (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory() > free + chunk) {
            filler.add(ByteArray(chunk))
        }
        return try {
            block()
        } finally {
            filler.clear()
        }
    }

    private class Decoded(val width: Int, val height: Int, val rgba: ByteArray)

    private fun decode(encoded: ByteArray): Decoded? {
        val size = IntArray(2)
        val rgba = NativeRendererTestHelper.cppDecodeImage(encoded, size) ?: return null
        return Decoded(size[0], size[1], rgba)
    }

    private fun raw(id: Int) = resources.openRawResource(id).use { it.readBytes() }

    /** @return The config Android decodes to, before [ImageDecoder] normalizes it. */
    private fun decodedConfig(png: ByteArray): Bitmap.Config? =
        BitmapFactory.decodeByteArray(png, 0, png.size, ImageDecoder.decodeOptions()).config

    /** Compares each decoded pixel with the fixture's own straight alpha values, premultiplied. */
    private fun assertMatchesSource(png: ByteArray, tolerance: Int) {
        val source = readUnfilteredPng(png)
        val decoded = decode(png)!!
        assertEquals(source.width, decoded.width)
        assertEquals(source.height, decoded.height)
        for (pixel in 0 until source.width * source.height) {
            val alpha = source.rgba[pixel * 4 + 3]
            for (channel in 0 until 4) {
                val value = source.rgba[pixel * 4 + channel]
                val expected = to8(if (channel == 3) alpha else value * alpha)
                val where = "x ${pixel % source.width}, y ${pixel / source.width}, channel $channel"
                assertNear(expected, decoded.rgba[pixel * 4 + channel], tolerance, where)
            }
        }
    }

    private fun assertNear(expected: Int, actual: Byte, tolerance: Int, where: String) {
        val value = actual.toInt() and 0xFF
        assertTrue("$where: expected $expected, got $value", abs(value - expected) <= tolerance)
    }

    private fun to8(unit: Float) = (unit * 255f).roundToInt()

    private class Source(val width: Int, val height: Int, val rgba: FloatArray)

    /** Reads a PNG written with no filters or interlacing, as our fixtures are. */
    private fun readUnfilteredPng(png: ByteArray): Source {
        val buffer = ByteBuffer.wrap(png).position(8) as ByteBuffer
        var width = 0
        var height = 0
        var bytesPerChannel = 1
        var channels = 4
        val compressed = ByteArrayOutputStream()
        while (buffer.hasRemaining()) {
            val length = buffer.int
            val type = ByteArray(4).also { buffer.get(it) }.decodeToString()
            val data = ByteArray(length).also { buffer.get(it) }
            buffer.int
            when (type) {
                "IHDR" -> {
                    val header = ByteBuffer.wrap(data)
                    width = header.int
                    height = header.int
                    bytesPerChannel = data[8] / 8
                    channels = if (data[9].toInt() == 6) 4 else 3
                }

                "IDAT" -> compressed.write(data)
            }
        }
        val rows = InflaterInputStream(compressed.toByteArray().inputStream()).readBytes()
        val rowBytes = 1 + width * channels * bytesPerChannel
        val max = if (bytesPerChannel == 2) 65535f else 255f
        return Source(
            width,
            height,
            FloatArray(width * height * 4) { i ->
                val pixel = i / 4
                val channel = i % 4
                if (channel >= channels) return@FloatArray 1f
                val offset = (pixel / width) * rowBytes + 1 +
                    ((pixel % width) * channels + channel) * bytesPerChannel
                var value = 0
                for (b in 0 until bytesPerChannel) {
                    value = (value shl 8) or (rows[offset + b].toInt() and 0xFF)
                }
                value / max
            }
        )
    }
}

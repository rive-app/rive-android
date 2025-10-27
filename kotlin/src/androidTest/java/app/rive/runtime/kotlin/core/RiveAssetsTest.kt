package app.rive.runtime.kotlin.core

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RiveAssetsTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context
    private val imageBytes = appContext
        .resources
        .openRawResource(R.raw.eve)
        .use { it.readBytes() }

    private val fontBytes = appContext
        .resources
        .openRawResource(R.raw.font)
        .use { it.readBytes() }

    private val audioBytes = appContext
        .resources
        .openRawResource(R.raw.table)
        .use { it.readBytes() }

    @Test(expected = IllegalArgumentException::class)
    fun invalidImageBytes() {
        val renderImage = RiveRenderImage.fromEncoded(byteArrayOf(0x01, 0x02, 0x03))
        // Failed to decode the image, this is a null pointer
        assertFalse(renderImage.hasCppObject)

        // Cannot release a null object.
        renderImage.release()
    }

    @Test
    fun makeRenderImage() {
        val renderImage = RiveRenderImage.fromEncoded(imageBytes)
        assertTrue(renderImage.hasCppObject)

        // Clean up & validate
        renderImage.release()
        assertFalse(renderImage.hasCppObject)
    }

    @Test
    fun makeRenderImageWithRendererType() {
        val renderImage =
            RiveRenderImage.fromEncoded(imageBytes, rendererType = RendererType.Canvas)
        assertTrue(renderImage.hasCppObject)

        // Clean up & validate
        renderImage.release()
        assertFalse(renderImage.hasCppObject)
    }

    @Test
    fun setRenderImage() {
        var imageAsset: ImageAsset? = null
        val renderImage = RiveRenderImage.fromEncoded(imageBytes)
        val myLoader = object : ContextAssetLoader(appContext) {
            override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean {
                if (asset is ImageAsset) {
                    imageAsset = asset
                    asset.image = renderImage
                    return true
                }
                return false
            }

        }
        val file = File(
            appContext.resources.openRawResource(R.raw.asset_load_check).readBytes(),
            fileAssetLoader = myLoader,
            rendererType = RendererType.Rive,
        )
        assertEquals(1, file.firstArtboard.animationCount)
        assertNotNull(imageAsset)
        assert(imageAsset!!.image.cppPointer == renderImage.cppPointer)

        /* Clean things up */
        myLoader.release()
        file.release()
        renderImage.release()
    }

    @Test
    fun imageAssetWidthAndHeightValidation() {
        lateinit var imageAsset: ImageAsset
        val myLoader = object : ContextAssetLoader(appContext) {
            override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean {
                if (asset is ImageAsset) {
                    imageAsset = asset
                    return true
                }
                return false
            }

        }
        val file = File(
            appContext.resources.openRawResource(R.raw.asset_load_check).readBytes(),
            fileAssetLoader = myLoader,
            rendererType = RendererType.Rive,
        )
        assertEquals(1, file.firstArtboard.animationCount)
        assert(imageAsset.width == 1280.0f)
        assert(imageAsset.height == 720.0f)

        /* Clean things up */
        myLoader.release()
        file.release()
    }

    @Test
    fun makeFont() {
        val font = RiveFont.make(fontBytes)
        assertTrue(font.hasCppObject)

        // Clean up & validate
        font.release()
        assertFalse(font.hasCppObject)
    }

    @Test
    fun makeFontWithRendererType() {
        val font = RiveFont.make(fontBytes, rendererType = RendererType.Canvas)
        assertTrue(font.hasCppObject)

        // Clean up & validate
        font.release()
        assertFalse(font.hasCppObject)
    }

    @Test
    fun setFont() {
        var fontAsset: FontAsset? = null
        val customFont = RiveFont.make(fontBytes)
        val myLoader = object : ContextAssetLoader(appContext) {
            override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean {
                if (asset is FontAsset) {
                    fontAsset = asset
                    asset.font = customFont
                    return true
                }
                return false
            }

        }
        val file = File(
            appContext.resources.openRawResource(R.raw.asset_load_check).readBytes(),
            fileAssetLoader = myLoader,
            rendererType = RendererType.Rive,
        )
        assertEquals(1, file.firstArtboard.animationCount)
        assertNotNull(fontAsset)
        assert(fontAsset!!.font.cppPointer == customFont.cppPointer)

        /* Clean things up */
        myLoader.release()
        file.release()
        customFont.release()
    }

    @Test
    fun makeAudio() {
        val audio = RiveAudio.make(audioBytes)
        assertTrue(audio.hasCppObject)

        // Clean up & validate
        audio.release()
        assertFalse(audio.hasCppObject)
    }

    @Test
    fun makeAudioWithRendererType() {
        val audio = RiveAudio.make(audioBytes, rendererType = RendererType.Canvas)
        assertTrue(audio.hasCppObject)

        // Clean up & validate
        audio.release()
        assertFalse(audio.hasCppObject)
    }

    @Test
    fun setAudio() {
        var audioAsset: AudioAsset? = null
        val customAudio = RiveAudio.make(audioBytes)
        val myLoader = object : ContextAssetLoader(appContext) {
            override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean {
                if (asset is AudioAsset) {
                    audioAsset = asset
                    asset.audio = customAudio
                    return true
                }
                return false
            }

        }
        val file = File(
            appContext.resources.openRawResource(R.raw.audio_test).readBytes(),
            fileAssetLoader = myLoader,
        )
        assertEquals(1, file.firstArtboard.animationCount)
        assertNotNull(audioAsset)
        assert(audioAsset!!.audio.cppPointer == customAudio.cppPointer)

        /* Clean things up */
        myLoader.release()
        file.release()
        customAudio.release()
    }

    /** Straight 2x2 RGBA image with semi-transparent pixels. */
    fun testStraightByteImage(): ByteArray = byteArrayOf(
        255.toByte(), 0, 0, 255.toByte(),   // Opaque red
        0, 255.toByte(), 0, 128.toByte(),   // 50% green
        0, 0, 255.toByte(), 64.toByte(),    // 25% blue
        255.toByte(), 255.toByte(), 255.toByte(), 0.toByte() // Transparent white
    )

    /** Premultiplied 2x2 RGBA image with semi-transparent pixels. */
    fun testPremultipliedByteImage(): ByteArray = byteArrayOf(
        255.toByte(), 0, 0, 255.toByte(),   // Opaque red
        0, 128.toByte(), 0, 128.toByte(),   // 50% green premul
        0, 0, 16.toByte(), 64.toByte(),     // 25% blue premul
        0, 0, 0, 0                          // Transparent white premul
    )

    @Test
    fun fromRGBABytes_rive_straightAlpha() {
        val width = 2
        val height = 2
        val image = RiveRenderImage.fromRGBABytes(
            testStraightByteImage(),
            width,
            height,
            RendererType.Rive,
            premultiplied = false
        )
        assertTrue(image.hasCppObject)
        image.release()
        assertFalse(image.hasCppObject)
    }

    @Test
    fun fromRGBABytes_rive_premultiplied() {
        val width = 2
        val height = 2
        val image = RiveRenderImage.fromRGBABytes(
            testPremultipliedByteImage(),
            width,
            height,
            RendererType.Rive,
            premultiplied = true
        )
        assertTrue(image.hasCppObject)
        image.release()
        assertFalse(image.hasCppObject)
    }

    @Test
    fun fromRGBABytes_canvas_straightAlpha() {
        val width = 2
        val height = 2
        val image = RiveRenderImage.fromRGBABytes(
            testStraightByteImage(),
            width,
            height,
            RendererType.Canvas,
            premultiplied = false
        )
        assertTrue(image.hasCppObject)
        image.release()
        assertFalse(image.hasCppObject)
    }

    @Test
    fun fromRGBABytes_canvas_premultiplied() {
        val width = 2
        val height = 2
        val image = RiveRenderImage.fromRGBABytes(
            testPremultipliedByteImage(),
            width,
            height,
            RendererType.Canvas,
            premultiplied = true
        )
        assertTrue(image.hasCppObject)
        image.release()
        assertFalse(image.hasCppObject)
    }

    @Test(expected = IllegalArgumentException::class)
    fun fromRGBABytes_invalidLength() {
        RiveRenderImage.fromRGBABytes(ByteArray(3), 2, 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun fromRGBABytes_invalidDims() {
        // width = 0, height = 1, byte length = 0 is consistent but dims are invalid
        RiveRenderImage.fromRGBABytes(ByteArray(0), 0, 1)
    }

    /** Straight 2x2 RGBA image with semi-transparent pixels. */
    fun testStraightIntsImage(): IntArray = intArrayOf(
        Color.argb(255, 255, 0, 0), // Opaque red
        Color.argb(128, 0, 255, 0), // 50% green
        Color.argb(64, 0, 0, 255),  // 25% blue
        Color.argb(0, 255, 255, 255)// Transparent white
    )

    fun testPremultipliedIntsImage(): IntArray = intArrayOf(
        Color.argb(255, 255, 0, 0), // Opaque red
        Color.argb(128, 0, 128, 0), // 50% green premul
        Color.argb(64, 0, 0, 16),   // 25% blue premul
        Color.argb(0, 0, 0, 0)      // Transparent white premul
    )

    @Test
    fun fromARGBInts_rive_straightAlpha() {
        val width = 2
        val height = 2
        val image = RiveRenderImage.fromARGBInts(
            testStraightIntsImage(),
            width,
            height,
            RendererType.Rive,
            premultiplied = false
        )
        assertTrue(image.hasCppObject)
        image.release()
        assertFalse(image.hasCppObject)
    }

    @Test
    fun fromARGBInts_rive_premultiplied() {
        val width = 2
        val height = 2
        val image = RiveRenderImage.fromARGBInts(
            testPremultipliedIntsImage(),
            width,
            height,
            RendererType.Rive,
            premultiplied = true
        )
        assertTrue(image.hasCppObject)
        image.release()
        assertFalse(image.hasCppObject)
    }

    @Test
    fun fromARGBInts_canvas_straightAlpha() {
        val width = 2
        val height = 2
        val image = RiveRenderImage.fromARGBInts(
            testStraightIntsImage(),
            width,
            height,
            RendererType.Canvas,
            premultiplied = false
        )
        assertTrue(image.hasCppObject)
        image.release()
        assertFalse(image.hasCppObject)
    }

    @Test
    fun fromARGBInts_canvas_premultiplied() {
        val width = 2
        val height = 2
        val image = RiveRenderImage.fromARGBInts(
            testPremultipliedIntsImage(),
            width,
            height,
            RendererType.Canvas,
            premultiplied = true
        )
        assertTrue(image.hasCppObject)
        image.release()
        assertFalse(image.hasCppObject)
    }

    @Test(expected = IllegalArgumentException::class)
    fun fromARGBInts_invalidLength() {
        RiveRenderImage.fromARGBInts(IntArray(3), 2, 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun fromARGBInts_invalidDims() {
        // width = 0, height = 1, colors length = 0 is consistent but dims are invalid
        RiveRenderImage.fromARGBInts(IntArray(0), 0, 1)
    }

    @Test
    fun fromBitmap_rive() {
        val width = 2
        val height = 2
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(testStraightIntsImage(), 0, width, 0, 0, width, height)
        val image = RiveRenderImage.fromBitmap(bitmap, RendererType.Rive)
        assertTrue(image.hasCppObject)
        image.release()
        assertFalse(image.hasCppObject)
    }

    @Test
    fun fromBitmap_canvas() {
        val width = 2
        val height = 2
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(testStraightIntsImage(), 0, width, 0, 0, width, height)
        val image = RiveRenderImage.fromBitmap(bitmap, RendererType.Canvas)
        assertTrue(image.hasCppObject)
        image.release()
        assertFalse(image.hasCppObject)
    }

    @Test(expected = IllegalArgumentException::class)
    fun fromBitmap_recycledBitmap() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.recycle()
        // Should throw due to require(!isRecycled)
        RiveRenderImage.fromBitmap(bitmap)
    }
}

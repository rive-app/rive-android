package app.rive.runtime.kotlin.core

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


    @Test(expected = IllegalArgumentException::class)
    fun invalidImageBytes() {
        val renderImage = RiveRenderImage.make(byteArrayOf(0x01, 0x02, 0x03))
        // Failed to decode the image, this is a null pointer
        assertFalse(renderImage.hasCppObject)

        // Cannot release a null object.
        renderImage.release()
    }

    @Test
    fun makeRenderImage() {
        val renderImage = RiveRenderImage.make(imageBytes)
        assertTrue(renderImage.hasCppObject)

        // Clean up & validate
        renderImage.release()
        assertFalse(renderImage.hasCppObject)
    }

    @Test
    fun makeRenderImageWithRendererType() {
        val renderImage = RiveRenderImage.make(imageBytes, rendererType = RendererType.Canvas)
        assertTrue(renderImage.hasCppObject)

        // Clean up & validate
        renderImage.release()
        assertFalse(renderImage.hasCppObject)
    }

    @Test
    fun setRenderImage() {
        var imageAsset: ImageAsset? = null
        val renderImage = RiveRenderImage.make(imageBytes)
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
            rendererType = RendererType.Skia,
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
    fun makeFont() {
        val renderImage = RiveFont.make(fontBytes)
        assertTrue(renderImage.hasCppObject)

        // Clean up & validate
        renderImage.release()
        assertFalse(renderImage.hasCppObject)
    }

    @Test
    fun makeFontWithRendererType() {
        val renderImage = RiveFont.make(fontBytes, rendererType = RendererType.Canvas)
        assertTrue(renderImage.hasCppObject)

        // Clean up & validate
        renderImage.release()
        assertFalse(renderImage.hasCppObject)
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
            rendererType = RendererType.Skia,
        )
        assertEquals(1, file.firstArtboard.animationCount)
        assertNotNull(fontAsset)
        assert(fontAsset!!.font.cppPointer == customFont.cppPointer)


        /* Clean things up */
        myLoader.release()
        file.release()
        customFont.release()
    }
}
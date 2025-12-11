package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.core.errors.MalformedFileException
import app.rive.runtime.kotlin.core.errors.UnsupportedRuntimeVersionException
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI

@RunWith(AndroidJUnit4::class)
class RiveFileLoadTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context

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
        file.release()
    }

    @Test
    fun loadFormatBuggy() {
        val file = File(appContext.resources.openRawResource(R.raw.off_road_car_blog).readBytes())
        assertEquals(5, file.firstArtboard.animationCount)
        file.release()
    }

    @Test
    fun loadFileWithRendererType() {
        val file =
            File(appContext.resources.openRawResource(R.raw.off_road_car_blog).readBytes())
        assertEquals(5, file.firstArtboard.animationCount)
        assertEquals(RendererType.Rive, file.rendererType)

        val customRendererFile = File(
            appContext.resources.openRawResource(R.raw.off_road_car_blog).readBytes(),
            RendererType.Canvas
        )
        assertEquals(5, customRendererFile.firstArtboard.animationCount)
        assertEquals(RendererType.Canvas, customRendererFile.rendererType)
        customRendererFile.release()
    }

    @Test
    fun customAssetLoader() {
        val myLoader = object : ContextAssetLoader(appContext) {
            override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean {
                appContext.resources.openRawResource(R.raw.eve).use {
                    val bytes = it.readBytes()
                    return asset.decode(bytes)
                }
            }

        }
        val file = File(
            appContext.resources.openRawResource(R.raw.walle).readBytes(),
            fileAssetLoader = myLoader,
            rendererType = RendererType.Rive,
        )
        assertEquals(1, file.firstArtboard.animationCount)

        /* Clean things up */
        myLoader.release()
        file.release()
    }

    @Test
    fun loadAssetsFromCDN() {
        val assetStore = mutableListOf<FileAsset>()
        val myCDNLoader = object : CDNAssetLoader(appContext) {
            override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean {
                assertEquals(
                    "/cdn/uuid/664b0a9c-1fb7-46f1-9ec9-c4d0796523d3",
                    URI(asset.cdnUrl).path
                )
                return assetStore.add(asset)
            }

        }
        val file = File(
            appContext.resources.openRawResource(R.raw.cdn_image).readBytes(),
            fileAssetLoader = myCDNLoader,
            rendererType = RendererType.Rive,
        )

        assertEquals(1, assetStore.size)

        /* Clean things up */
        myCDNLoader.release()
        file.release()
    }
}

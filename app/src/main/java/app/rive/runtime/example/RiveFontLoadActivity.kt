package app.rive.runtime.example

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.core.ContextAssetLoader
import app.rive.runtime.kotlin.core.FileAsset
import kotlin.random.Random

class FontLoadActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            R.layout.rive_font_load_simple
        )
    }
}

open class HandleSimpleRiveAsset(context: Context) : ContextAssetLoader(context) {
    private val fontPool = arrayOf(
        R.raw.montserrat,
        R.raw.opensans,
        R.raw.roboto,
    )

    /** Override this method to customize the asset loading process. */
    override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean {
        val randFontIndex = Random.nextInt(fontPool.size)
        val fontToLoad = fontPool[randFontIndex]
        context.resources.openRawResource(fontToLoad).use {
            return asset.decode(it.readBytes())
        }
    }
}

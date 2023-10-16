package app.rive.runtime.example

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import app.rive.runtime.example.databinding.ActivityAssetLoaderBinding
import app.rive.runtime.kotlin.core.ContextAssetLoader
import app.rive.runtime.kotlin.core.FileAsset
import app.rive.runtime.kotlin.core.FileAssetLoader
import app.rive.runtime.kotlin.core.BytesRequest
import app.rive.runtime.kotlin.core.ExperimentalAssetLoader
import app.rive.runtime.kotlin.core.Rive
import com.android.volley.toolbox.Volley
import com.google.android.material.tabs.TabLayoutMediator
import kotlin.random.Random

class AssetLoaderActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAssetLoaderBinding

    @ExperimentalAssetLoader
    override fun onCreate(savedInstanceState: Bundle?) {
        // Setup
        Rive.init(this)
        super.onCreate(savedInstanceState)

        binding = ActivityAssetLoaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 3


            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> AssetLoaderFragment()
                    1 -> AssetButtonFragment()
                    else -> FontAssetFragment()
                }
            }
        }

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Asset Loader"
                1 -> "Change Images"
                else -> "Fonts"
            }
        }.attach()
    }
}

/**
 * [FileAssetLoader] tailored for the two assets in the walle.riv file.
 *
 * The main purpose of this class is to demonstrate how create a custom [ContextAssetLoader] via XML.
 */
@ExperimentalAssetLoader
class WalleAssetLoader(context: Context) : ContextAssetLoader(context) {
    override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean {
        val identifier =
            if (asset.uniqueFilename.contains("eve")) R.raw.walle_img_eve
            else R.raw.walle_img_walle

        context.resources.openRawResource(identifier).use {
            asset.decode(it.readBytes())
        }
        return true
    }
}

/**
 * Loads a random image from picsum.photos.
 */
@ExperimentalAssetLoader
class RandomNetworkLoader(context: Context) : FileAssetLoader() {
    private val loremImage = "https://picsum.photos"
    private val queue = Volley.newRequestQueue(context)
    private val minSize = 150
    private val maxSize = 500
    private val maxId = 1084

    override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean {
        val randomWidth = Random.nextInt(minSize, maxSize)
        val randomHeight = Random.nextInt(minSize, maxSize)
        val imgId = Random.nextInt(maxId)

        val url = "$loremImage/id/$imgId/$randomWidth/$randomHeight"
        val request = BytesRequest(
            url,
            { bytes -> asset.decode(bytes) },
            {
                Log.e("Request", "onAssetLoaded: failed to load image from $url")
                it.printStackTrace()
            }
        )
        queue.add(request)
        return true
    }
}

package app.rive.runtime.example

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import app.rive.runtime.example.databinding.FragmentAssetButtonBinding
import app.rive.runtime.example.databinding.FragmentAssetLoaderBinding
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.BytesRequest
import app.rive.runtime.kotlin.core.FileAsset
import app.rive.runtime.kotlin.core.FileAssetLoader
import app.rive.runtime.kotlin.core.FontAsset
import app.rive.runtime.kotlin.core.ImageAsset
import app.rive.runtime.kotlin.core.Loop
import app.rive.runtime.kotlin.core.RiveFont
import app.rive.runtime.kotlin.core.RiveRenderImage
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import kotlin.random.Random

private fun makeContainer(context: Context): FrameLayout {
    return FrameLayout(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            // 16dp equivalent
            val dpPadding = (16 * resources.displayMetrics.density).toInt()
            topMargin = dpPadding
        }
    }
}

private fun makeButton(
    context: Context,
    label: String,
    clickListener: View.OnClickListener
): Button {
    return Button(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        text = label
        setOnClickListener(clickListener)
    }
}

class AssetLoaderFragment : Fragment() {
    private var _binding: FragmentAssetLoaderBinding? = null
    private val binding get() = _binding!!
    private lateinit var networkLoader: RandomNetworkLoader

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAssetLoaderBinding.inflate(inflater, container, false)
        val view = binding.root
        val ctx = view.context
        networkLoader = RandomNetworkLoader(ctx)


        makeContainer(ctx).let {
            val riveView = RiveAnimationView.Builder(ctx)
                .setAssetLoader(networkLoader)
                .setResource(R.raw.walle)
                .build()
            it.addView(riveView)
            binding.fragmentLoaderContainer.addView(it)
        }


        makeContainer(ctx).let {
            val cdnRiveView = RiveAnimationView.Builder(ctx)
                .setResource(R.raw.cdn_image)
                .build()
            it.addView(cdnRiveView)
            binding.fragmentLoaderContainer.addView(it)
        }

        return view
    }
}

class AssetButtonFragment : Fragment() {
    private var _binding: FragmentAssetButtonBinding? = null
    private val binding get() = _binding!!

    private val loremImage = "https://picsum.photos"
    private lateinit var queue: RequestQueue
    private lateinit var assetStore: ImageAssetStore
    private val minSize = 750
    private val maxSize = 1000
    private val maxId = 1084

    private fun randomImageButton(context: Context): Button {
        return makeButton(context, "Change Image") {
            val randomWidth = Random.nextInt(minSize, maxSize)
            val randomHeight = Random.nextInt(minSize, maxSize)
            val imgId = Random.nextInt(maxId)
            val url = "$loremImage/id/$imgId/$randomWidth/$randomHeight"
            val request = BytesRequest(
                url,
                { bytes ->
                    assetStore.nextAsset.image = RiveRenderImage.make(bytes)
                },
                {
                    Log.e("Request", "onAssetLoaded: failed to load $url.")
                    it.printStackTrace()
                }
            )
            queue.add(request)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentAssetButtonBinding.inflate(inflater, container, false)
        val view = binding.root
        val ctx = view.context
        queue = Volley.newRequestQueue(ctx)

        assetStore = ImageAssetStore()
        val riveView = RiveAnimationView.Builder(ctx)
            .setAssetLoader(assetStore)
            .setResource(R.raw.asset_load_check)
            .build()

        makeContainer(ctx).let {
            it.addView(riveView)
            binding.fragmentButtonContainer.addView(it)
        }

        randomImageButton(ctx).let {
            binding.fragmentButtonContainer.addView(it)
        }


        return view
    }

    private class ImageAssetStore : FileAssetLoader() {
        private var lastAssetChanged = 0
        val assetList = mutableListOf<ImageAsset>()

        val nextAsset: ImageAsset
            get() = assetList[lastAssetChanged++ % assetList.size]

        override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean {
            if (asset is ImageAsset) {
                return assetList.add(asset)
            }
            return false
        }
    }
}

class FontAssetFragment : Fragment() {
    private var _binding: FragmentAssetButtonBinding? = null
    private lateinit var queue: RequestQueue
    private val binding get() = _binding!!

    private lateinit var fontDecoder: RandomFontDecoder

    private val fontUrls = listOf(
        "https://cdn.rive.app/runtime/flutter/IndieFlower-Regular.ttf",
        "https://cdn.rive.app/runtime/flutter/comic-neue.ttf",
        "https://cdn.rive.app/runtime/flutter/inter.ttf",
        "https://cdn.rive.app/runtime/flutter/inter-tight.ttf",
        "https://cdn.rive.app/runtime/flutter/josefin-sans.ttf",
        "https://cdn.rive.app/runtime/flutter/send-flowers.ttf",
    )


    private fun randomFontButton(context: Context): Button {
        return makeButton(context, "Change Font") {
            val url = fontUrls.random()
            val request = BytesRequest(
                url,
                { bytes -> fontDecoder.fontAsset.font = RiveFont.make(bytes) },
                {
                    Log.e("Request", "onAssetLoaded: failed to load $url.")
                    it.printStackTrace()
                }
            )
            queue.add(request)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentAssetButtonBinding.inflate(inflater, container, false)
        val view = binding.root
        val ctx = view.context
        queue = Volley.newRequestQueue(ctx)

        fontDecoder = RandomFontDecoder(ctx)
        val riveView = RiveAnimationView.Builder(ctx)
            .setAssetLoader(fontDecoder)
            .setAnimationName("Bounce")
            .setLoop(Loop.PINGPONG)
            .setResource(R.raw.fontz_oob)
            .build()

        makeContainer(ctx).let {
            it.addView(riveView)
            binding.fragmentButtonContainer.addView(it)
        }
        randomFontButton(ctx).let {
            binding.fragmentButtonContainer.addView(it)
        }

        return view
    }

    private class RandomFontDecoder(private val context: Context) : FileAssetLoader() {
        lateinit var fontAsset: FontAsset
        override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean {
            if (asset is FontAsset) {
                // Store a reference to the asset.
                fontAsset = asset

                // Load the original font
                context.resources.openRawResource(R.raw.roboto).use {
                    return asset.decode(it.readBytes())
                }
            }
            return false
        }
    }
}

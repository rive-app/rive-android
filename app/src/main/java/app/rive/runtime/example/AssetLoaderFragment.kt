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
import app.rive.runtime.kotlin.core.ExperimentalAssetLoader
import app.rive.runtime.kotlin.core.FileAsset
import app.rive.runtime.kotlin.core.FileAssetLoader
import app.rive.runtime.kotlin.core.Loop
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

@ExperimentalAssetLoader
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

@ExperimentalAssetLoader
class AssetButtonFragment : Fragment() {
    private var _binding: FragmentAssetButtonBinding? = null
    private val binding get() = _binding!!

    private val loremImage = "https://picsum.photos"
    private lateinit var queue: RequestQueue
    private lateinit var assetStore: AssetStore
    private val minSize = 150
    private val maxSize = 500
    private val maxId = 1084
    private var lastAssetChanged = 0

    private fun makeButton(context: Context): Button {
        return Button(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            text = "Change Image"
            setOnClickListener {
                val randomAsset =
                    assetStore.assetList[lastAssetChanged++ % assetStore.assetList.size]
                val randomWidth = Random.nextInt(minSize, maxSize)
                val randomHeight = Random.nextInt(minSize, maxSize)
                val imgId = Random.nextInt(maxId)
                val url = "$loremImage/id/$imgId/$randomWidth/$randomHeight"
                val request = BytesRequest(
                    url,
                    { bytes -> randomAsset.decode(bytes) },
                    {
                        Log.e("Request", "onAssetLoaded: failed to load $url.")
                        it.printStackTrace()
                    }
                )
                queue.add(request)
            }
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

        assetStore = AssetStore()
        val riveView = RiveAnimationView.Builder(ctx)
            .setAssetLoader(assetStore)
            .setResource(R.raw.walle)
            .build()

        makeContainer(ctx).let {
            it.addView(riveView)
            binding.fragmentButtonContainer.addView(it)
        }

        makeButton(ctx).let {
            binding.fragmentButtonContainer.addView(it)
        }


        return view
    }

    @ExperimentalAssetLoader
    private class AssetStore : FileAssetLoader() {
        val assetList = mutableListOf<FileAsset>()
        override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean {
            return assetList.add(asset)
        }
    }
}

@ExperimentalAssetLoader
class FontAssetFragment : Fragment() {
    private var _binding: FragmentAssetButtonBinding? = null
    private val binding get() = _binding!!

    private lateinit var fontDecoder: RandomFontDecoder

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentAssetButtonBinding.inflate(inflater, container, false)
        val view = binding.root
        val ctx = view.context

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

        return view
    }

    @ExperimentalAssetLoader
    private class RandomFontDecoder(private val context: Context) : FileAssetLoader() {
        lateinit var fontAsset: FileAsset
        override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean {
            // Store a reference to the asset.
            fontAsset = asset

            // Load the original font
            context.resources.openRawResource(R.raw.roboto).use {
                return asset.decode(it.readBytes())
            }
        }
    }
}

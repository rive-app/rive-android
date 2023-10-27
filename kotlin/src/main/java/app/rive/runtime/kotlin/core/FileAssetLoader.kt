package app.rive.runtime.kotlin.core

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import app.rive.runtime.kotlin.RiveAnimationView
import com.android.volley.NetworkResponse
import com.android.volley.ParseError
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.Volley

/**
 * This annotation marks the use of [FileAssetLoader]s as experimental as we iterate through
 * the API and validate its consistency.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "The FileAssetLoader API is experimental"
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ExperimentalAssetLoader

@ExperimentalAssetLoader
abstract class FileAssetLoader : NativeObject(NULL_POINTER) {
    init {
        // Make the corresponding C++ object.
        cppPointer = constructor()
        assert(cppPointer != NULL_POINTER)
    }

    /* C++ constructor */
    protected external fun constructor(): Long

    /* Destructor gets called on [dispose()] */
    external override fun cppDelete(pointer: Long)

    private external fun cppSetRendererType(pointer: Long, rendererType: Int)

    /**
     * Override this method to customize the asset loading process.
     */
    abstract fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean

    fun setRendererType(rendererType: RendererType) {
        cppSetRendererType(cppPointer, rendererType.value)
    }
}

@ExperimentalAssetLoader
abstract class ContextAssetLoader(protected val context: Context) : FileAssetLoader()

@ExperimentalAssetLoader
open class FallbackAssetLoader(
    context: Context,
    loadCDNAssets: Boolean = true,
    loader: FileAssetLoader? = null,
) : FileAssetLoader() {

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    val loaders = mutableListOf<FileAssetLoader>().apply {
        loader?.let { add(it) }
        if (loadCDNAssets) add(CDNAssetLoader(context))
    }

    open fun appendLoader(loader: FileAssetLoader) {
        loaders.add(loader)
    }

    override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean {
        return loaders.any { it.loadContents(asset, inBandBytes) }
    }

    override fun release(): Int {
        // Release up all loaders.
        loaders.forEach { it.release() }
        return super.release()
    }

    private fun resetCDNLoader(needsCDNLoader: Boolean, context: Context) {
        // Make sure CDN Loader is set.
        val cdnLoaderIndex = loaders.indexOfFirst { it is CDNAssetLoader }
        if (cdnLoaderIndex == -1 && needsCDNLoader) {
            appendLoader(CDNAssetLoader(context))
        } else if (cdnLoaderIndex >= 0 && !needsCDNLoader) {
            loaders.removeAt(cdnLoaderIndex).release()
        }
    }

    /**
     * Resets the state of the asset loader when building RiveAnimationView with the
     * secondary constructor.
     */
    internal fun resetWith(builder: RiveAnimationView.Builder) {
        // First, try setting up a custom loader.
        builder.assetLoader?.let {
            // Prepend loader to make sure custom always executes first.
            loaders.add(0, it)
        }
        resetCDNLoader(builder.shouldLoadCDNAssets, builder.context)
    }
}

@ExperimentalAssetLoader
open class CDNAssetLoader(context: Context) : FileAssetLoader() {
    private val tag = javaClass.simpleName

    private val queue by lazy(LazyThreadSafetyMode.NONE) { Volley.newRequestQueue(context) }

    override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean {
        val url = asset.cdnUrl
        if (url.isEmpty()) return false

        val request = BytesRequest(
            url,
            { bytes -> asset.decode(bytes) },
            {
                Log.e(tag, "onAssetLoaded: loading image failed.")
                it.printStackTrace()
            }
        )

        queue.add(request)
        return true // This loader handled the asset.
    }
}

class BytesRequest(
    url: String,
    private val onResponse: (bytes: ByteArray) -> Unit,
    errorListener: Response.ErrorListener,
) : Request<ByteArray>(Method.GET, url, errorListener) {

    override fun deliverResponse(response: ByteArray) = onResponse(response)

    override fun parseNetworkResponse(response: NetworkResponse?): Response<ByteArray> {
        return try {
            val bytes = response?.data ?: ByteArray(0)
            Response.success(bytes, HttpHeaderParser.parseCacheHeaders(response))
        } catch (e: Exception) {
            Response.error(ParseError(e))
        }
    }
}
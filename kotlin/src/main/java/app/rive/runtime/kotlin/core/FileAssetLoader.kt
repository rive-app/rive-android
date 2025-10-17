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
 * Base class for asset loading. Overload [loadContents] to customize the loading process.
 *
 * This allows you to choose how assets, i.e. images, fonts, and audio, are loaded when referenced
 * by a Rive file. This is especially useful for referenced assets which you may want to load once
 * and supply to multiple Rive files, e.g. for expensive font files.
 *
 * Asset loaders are typed to a specific [RendererType] via [setRendererType]. This is because
 * images are renderer specific, and the asset loader is ultimately what creates [FileAsset]s.
 *
 * Apply it to a [RiveAnimationView] in one of the following ways:
 * - [RiveAnimationView.Builder.setAssetLoader]
 * - [RiveAnimationView.setAssetLoader]
 * - [File][app.rive.runtime.kotlin.core.File] constructor
 * - [RiveFileRequest][app.rive.runtime.kotlin.RiveFileRequest] constructor
 * - Via the XML attribute `riveAssetLoaderClass` as the class name using reflection
 *    - This will try to instantiate your class via a no-argument constructor or with [Context] if
 *      inheriting from [ContextAssetLoader].
 */
abstract class FileAssetLoader : NativeObject(NULL_POINTER) {
    init {
        // Make the corresponding C++ object.
        cppPointer = constructor()
        refs.incrementAndGet()
        assert(cppPointer != NULL_POINTER)
    }

    /* C++ constructor */
    protected external fun constructor(): Long

    /* Destructor gets called on [dispose()] */
    external override fun cppDelete(pointer: Long)

    external fun cppRef(pointer: Long)

    private external fun cppSetRendererType(pointer: Long, rendererType: Int)

    /**
     * Override to customize the asset loading process.
     *
     * @param asset The [FileAsset] being loaded. This contains metadata about the asset, e.g. its
     *    name and CDN URL when hosted by Rive.
     * @param inBandBytes The embedded bytes that were included in the Rive file. This will be empty
     *    if the asset was marked as "Referenced" or "Hosted" in the Rive editor.
     * @return true if the asset was loaded, false refuse loading. Returning false can be useful
     *    when using multiple [FileAssetLoader]s in a [FallbackAssetLoader] and you want to delegate
     *    loading to the next loader.
     */
    abstract fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean

    fun setRendererType(rendererType: RendererType) =
        cppSetRendererType(cppPointer, rendererType.value)

    override fun acquire(): Int {
        cppRef(cppPointer)
        return super.acquire()
    }
}

/**
 * A [FileAssetLoader] with access to a [Context]. Use this if you need context when the asset
 * loader is constructed via reflection in XML.
 */
abstract class ContextAssetLoader(protected val context: Context) : FileAssetLoader()

/**
 * The default asset loader used by [RiveAnimationView] when loading assets. This allows for setting
 * up cascading asset loaders which may be useful when each represents a different source or policy,
 * e.g. a memory cache, local storage, and network.
 *
 * @param context The application context.
 * @param loadCDNAssets Whether to load assets from Rive's CDN. This appends a [CDNAssetLoader] to
 *    the list of loaders if true (default), allowing loading of assets marked as "Hosted" in the
 *    Rive editor.
 * @param loader An optional initial [FileAssetLoader] to add to the list of loaders.
 */
class FallbackAssetLoader(
    context: Context,
    loadCDNAssets: Boolean = true,
    loader: FileAssetLoader? = null,
) : FileAssetLoader() {
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    val loaders = mutableListOf<FileAssetLoader>()

    init {
        loader?.let { appendLoader(it) }
        if (loadCDNAssets) {
            appendLoader(CDNAssetLoader(context.applicationContext))
        }
    }

    /**
     * Add a [FileAssetLoader] to the end of the list of loaders, i.e. lowest priority. Note that if
     * the constructor parameter [loadCDNAssets] is true (default) a [CDNAssetLoader] will be the
     * current last loader, and this will be added after it.
     *
     * @param loader The [FileAssetLoader] to add.
     */
    fun appendLoader(loader: FileAssetLoader) {
        loaders.add(loader)
        // Make sure everything is disposed.
        dependencies.add(loader)
    }

    /**
     * Add a [FileAssetLoader] to the start of the list of loaders, i.e. highest priority.
     *
     * @param loader The [FileAssetLoader] to add.
     */
    fun prependLoader(loader: FileAssetLoader) {
        loaders.add(0, loader)
        // Make sure everything is disposed.
        dependencies.add(loader)
    }

    /** Attempts to load the asset using each loader in order until one succeeds or all refuse. */
    override fun loadContents(asset: FileAsset, inBandBytes: ByteArray) =
        loaders.any { it.loadContents(asset, inBandBytes) }

    private fun resetCDNLoader(needsCDNLoader: Boolean, context: Context) {
        // Make sure CDN Loader is set.
        val cdnLoaderIndex = loaders.indexOfFirst { it is CDNAssetLoader }
        if (cdnLoaderIndex == -1 && needsCDNLoader) {
            appendLoader(CDNAssetLoader(context.applicationContext))
        } else if (cdnLoaderIndex >= 0 && !needsCDNLoader) {
            loaders.removeAt(cdnLoaderIndex).let {
                dependencies.remove(it)
                it.release()
            }
        }
    }

    /**
     * When using the [builder][RiveAnimationView.Builder], ensure that the user's asset loader is
     * prepended and the CDN loader is added or removed based on the builder setting.
     */
    internal fun resetWith(builder: RiveAnimationView.Builder) {
        // First, try setting up a custom loader.
        builder.assetLoader?.let {
            // Prepend loader to make sure custom always executes first.
            prependLoader(it)
        }
        resetCDNLoader(builder.shouldLoadCDNAssets, builder.context.applicationContext)
    }
}

/**
 * Loads assets from Rive's CDN when marked as "Hosted" in the Rive editor. Uses the
 * [FileAsset.cdnUrl] field as the URL.
 */
open class CDNAssetLoader(context: Context) : FileAssetLoader() {
    private val tag = javaClass.simpleName

    private val queue by lazy { Volley.newRequestQueue(context) }

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

    override fun parseNetworkResponse(response: NetworkResponse?): Response<ByteArray> =
        try {
            val bytes = response?.data ?: ByteArray(0)
            Response.success(bytes, HttpHeaderParser.parseCacheHeaders(response))
        } catch (e: Exception) {
            Response.error(ParseError(e))
        }
}

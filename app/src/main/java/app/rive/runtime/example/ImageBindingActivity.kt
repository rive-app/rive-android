package app.rive.runtime.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.ContextAssetLoader
import app.rive.runtime.kotlin.core.FileAsset
import app.rive.runtime.kotlin.core.FileAssetLoader
import app.rive.runtime.kotlin.core.ImageAsset
import app.rive.runtime.kotlin.core.RendererType
import app.rive.runtime.kotlin.core.RiveRenderImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * This sample exercises the various ways to assign an image at runtime to Rive. These methods are:
 * - Supplying an AssetLoader and calling `decode` on a FileAsset
 * - Supplying an AssetLoader and setting the `image` property on an ImageAsset
 * - Data binding a RiveRenderImage created from encoded image bytes
 * - Data binding a RiveRenderImage created from an Android Bitmap
 * - Data binding a RiveRenderImage created from RGBA bytes (both premultiplied and straight alpha)
 * - Data binding a RiveRenderImage created from ARGB ints (both premultiplied and straight alpha)
 *
 * Each of these methods is exercised with both the Rive and Canvas renderers, and for the RGBA/ARGB
 * methods, both premultiplied and straight pixel data is tested.
 *
 * The sample presents a grid of cells, each with a Rive instance and a label describing the
 * configuration. Pressing the "Bind All" button binds all the images in sequence. If all is working
 * as expected, each Rive instance should show the same image.
 *
 * The test image has four quadrants: opaque red, 75% transparent green, 25% transparent blue, and
 * fully transparent white. It is then composited over black. If alpha is not handled correctly
 * there will be color bleed between the quadrants, especially white.
 *
 * The test image can be generated with this ImageMagick command:
 * ```
 * magick -size 2x2 xc:none -alpha set \
 *   -fill "rgba(255,0,0,1.0)"   -draw "point 0,0" \
 *   -fill "rgba(0,255,0,0.75)"  -draw "point 1,0" \
 *   -fill "rgba(0,0,255,0.25)"  -draw "point 0,1" \
 *   -fill "rgba(255,255,255,0)" -draw "point 1,1" alpha_test.png
 */

/** Strategy for the various methods of making a RiveRenderImage from different inputs. */
private sealed interface RenderImageMethod {
    suspend fun make(
        ctx: Context,
        rendererType: RendererType,
        isPremultiplied: Boolean
    ): RiveRenderImage

    /** From a raw image resource to an Android Bitmap. */
    class FromBitmap(private val resId: Int) : RenderImageMethod {
        override suspend fun make(
            ctx: Context,
            rendererType: RendererType,
            isPremultiplied: Boolean
        ): RiveRenderImage = withContext(Dispatchers.IO) {
            val bmp = BitmapFactory.decodeResource(
                ctx.resources,
                resId,
                // Stores the bitmap as ARGB888 and premultiplied internally
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inPremultiplied = true
                })
                ?: error("Failed to decode bitmap")
            RiveRenderImage.fromBitmap(bmp, rendererType)
        }
    }

    /** From user supplied encoded image bytes. */
    class FromEncoded(private val resId: Int) : RenderImageMethod {
        override suspend fun make(
            ctx: Context,
            rendererType: RendererType,
            isPremultiplied: Boolean
        ): RiveRenderImage = withContext(Dispatchers.IO) {
            val bytes = ctx.resources.openRawResource(resId).use { it.readBytes() }
            RiveRenderImage.fromEncoded(bytes, rendererType)
        }
    }

    /** From user supplied ARGB ints. */
    class FromARGB(private val pixels: IntArray, private val width: Int, private val height: Int) :
        RenderImageMethod {
        override suspend fun make(
            ctx: Context,
            rendererType: RendererType,
            isPremultiplied: Boolean
        ): RiveRenderImage = withContext(Dispatchers.IO) {
            RiveRenderImage.fromARGBInts(pixels, width, height, rendererType, isPremultiplied)
        }
    }

    /** From user supplied RGBA bytes. */
    class FromRGBA(
        private val pixelBytes: ByteArray,
        private val width: Int,
        private val height: Int
    ) : RenderImageMethod {
        override suspend fun make(
            ctx: Context,
            rendererType: RendererType,
            isPremultiplied: Boolean
        ): RiveRenderImage = withContext(Dispatchers.IO) {
            RiveRenderImage.fromRGBABytes(pixelBytes, width, height, rendererType, isPremultiplied)
        }
    }
}

/** Configuration for a cell. Needs to support both data binding and asset-loader-based flows. */
private data class ImageConfig(
    val label: String,
    val rendererType: RendererType,
    val isPremultiplied: Boolean? = null, // Only relevant for ARGB/RGBA
    // For data-binding-based flows: a factory that creates a RiveRenderImage for this config.
    val method: RenderImageMethod? = null,
    // For asset-loader-based flows: a custom loader for this config.
    val assetLoader: FileAssetLoader? = null,
) {
    /** Apply this configuration to the given RiveAnimationView. */
    suspend fun apply(view: RiveAnimationView, ctx: Context) {
        // Data binding path
        if (method != null) {
            // Direct: produce a RiveRenderImage and set the Image property.
            val renderImage = method.make(ctx, rendererType, isPremultiplied ?: true)
            val imageProp = view.controller.stateMachines
                .first().viewModelInstance?.getImageProperty("Image")
            imageProp?.set(renderImage)
            renderImage.release()
            view.play()
        } else if (assetLoader != null) {
            // Asset loader path: trigger the state machine to show the loaded image.
            val triggerProp = view.controller.stateMachines
                .first().viewModelInstance?.getTriggerProperty("Show Test Image")
            triggerProp?.trigger()
            view.play()
        }
    }

    fun displayLabel(): String = buildString {
        append(label)
        append(" — ")
        append(if (rendererType == RendererType.Rive) "Rive" else "Canvas")
        isPremultiplied?.let { append(" — ").append(if (it) "Premul" else "Straight") }
    }
}

/** AssetLoader that uses FileAsset::decode to supply image data. */
class DecodeAssetLoader(ctx: Context) : ContextAssetLoader(ctx) {
    override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean {
        if (asset.name != ALPHA_TEST_ASSET_NAME) return false
        require(asset is ImageAsset)

        val encodedBytes = context.resources.openRawResource(ALPHA_TEST_RES).use { it.readBytes() }
        return asset.decode(encodedBytes)
    }
}

/** AssetLoader that creates a RiveRenderImage and sets it on an ImageAsset. */
class ImageAssetLoader(ctx: Context, private val rendererType: RendererType) :
    ContextAssetLoader(ctx) {
    override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean {
        if (asset.name != ALPHA_TEST_ASSET_NAME) return false
        require(asset is ImageAsset)

        val encodedBytes = context.resources.openRawResource(ALPHA_TEST_RES).use { it.readBytes() }
        val renderImage = RiveRenderImage.fromEncoded(encodedBytes, rendererType)
        asset.image = renderImage
        renderImage.release()
        return true
    }
}

val ALPHA_TEST_RES = R.raw.alpha_test
const val ALPHA_TEST_ASSET_NAME = "alpha_test"
val PIXELS = intArrayOf(0xFFFF0000.toInt(), 0xC000FF00.toInt(), 0x400000FF, 0x00FFFFFF)
val PIXEL_BYTES = argbToRgbaBytes(PIXELS)
const val WIDTH = 2
const val HEIGHT = 2

/** Create all the configuration combinations. */
private fun buildConfigs(ctx: Context): List<ImageConfig> {
    fun bothRenderers(
        baseLabel: String,
        method: RenderImageMethod? = null,
        premul: Boolean? = null,
        loader: ((RendererType) -> FileAssetLoader)? = null
    ) = listOf(
        ImageConfig(
            baseLabel, RendererType.Rive, premul, method,
            loader?.invoke(RendererType.Rive)
        ),
        ImageConfig(
            baseLabel, RendererType.Canvas, premul, method,
            loader?.invoke(RendererType.Canvas)
        ),
    )

    return buildList {
        addAll(bothRenderers("Asset Loader (Decode)", loader = { _ -> DecodeAssetLoader(ctx) }))
        addAll(bothRenderers("Asset Loader (Image)", loader = { rt -> ImageAssetLoader(ctx, rt) }))
        addAll(bothRenderers("Encoded", method = RenderImageMethod.FromEncoded(ALPHA_TEST_RES)))
        addAll(bothRenderers("Bitmap", method = RenderImageMethod.FromBitmap(ALPHA_TEST_RES)))
        addAll(
            bothRenderers(
                "RGBA Bytes",
                premul = false,
                method = RenderImageMethod.FromRGBA(PIXEL_BYTES, WIDTH, HEIGHT)
            )
        )
        addAll(
            bothRenderers(
                "RGBA Bytes", premul = true,
                method = RenderImageMethod.FromRGBA(
                    premultiplyRGBABytes(PIXEL_BYTES),
                    WIDTH,
                    HEIGHT
                )
            )
        )
        addAll(
            bothRenderers(
                "ARGB Ints",
                premul = false,
                method = RenderImageMethod.FromARGB(PIXELS, WIDTH, HEIGHT)
            )
        )
        addAll(
            bothRenderers(
                "ARGB Ints", premul = true,
                method = RenderImageMethod.FromARGB(
                    premultiplyARGBInts(PIXELS),
                    WIDTH,
                    HEIGHT
                )
            )
        )
    }
}

class ImageBindingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // Build all combinations we want to exercise
            val configs = remember { buildConfigs(this) }

            // Maintain references to the interior views for binding
            val riveViews = remember {
                mutableStateListOf<RiveAnimationView?>().apply { repeat(configs.size) { add(null) } }
            }

            val bound = remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    val scope = rememberCoroutineScope()
                    Button(
                        onClick = {
                            if (bound.value) return@Button
                            bound.value = true
                            scope.launch {
                                // Apply each config to its corresponding view
                                for (i in configs.indices) {
                                    val view = riveViews[i]!!
                                    configs[i].apply(view, this@ImageBindingActivity)
                                }
                            }
                        },
                        enabled = !bound.value,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (bound.value) "Bound" else "Bind All") }
                }

                Spacer(Modifier.height(12.dp))

                RiveGrid(
                    configs = configs,
                    onViewReadyAt = { index, view -> riveViews[index] = view },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RiveGrid(
    configs: List<ImageConfig>,
    onViewReadyAt: (index: Int, view: RiveAnimationView) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        for (i in configs.indices step 2) {
            Row(modifier = Modifier.fillMaxWidth()) {
                val left = configs[i]
                RiveLabeledView(
                    label = left.displayLabel(),
                    rendererType = left.rendererType,
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp),
                    assetLoader = left.assetLoader,
                    onViewReady = { onViewReadyAt(i, it) }
                )

                if (i + 1 < configs.size) {
                    val right = configs[i + 1]
                    RiveLabeledView(
                        label = right.displayLabel(),
                        rendererType = right.rendererType,
                        modifier = Modifier
                            .weight(1f)
                            .padding(8.dp),
                        assetLoader = right.assetLoader,
                        onViewReady = { onViewReadyAt(i + 1, it) }
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RiveLabeledView(
    label: String,
    rendererType: RendererType,
    modifier: Modifier = Modifier,
    assetLoader: FileAssetLoader? = null,
    onViewReady: (RiveAnimationView) -> Unit
) {
    Column(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                val builder = RiveAnimationView.Builder(ctx)
                    .setResource(R.raw.image_binding_test)
                    .setStateMachineName("State Machine 1")
                    .setAutoplay(true)
                    .setAutoBind(true)
                builder.setRendererType(rendererType)
                // Attach a custom asset loader for this cell if provided.
                if (assetLoader != null) {
                    builder.setAssetLoader(assetLoader)
                }
                builder.build().also { view -> onViewReady(view) }
            }
        )
        Text(
            text = label,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Helper: Mask for least significant byte. */
const val LSB_MASK = 0xFF

/** Helper: Convert ARGB ints to RGBA bytes. */
private fun argbToRgbaBytes(pixels: IntArray): ByteArray {
    val out = ByteArray(pixels.size * 4)
    var byteIdx = 0
    for (p in pixels) {
        val a = (p ushr 24) and LSB_MASK
        val r = (p ushr 16) and LSB_MASK
        val g = (p ushr 8) and LSB_MASK
        val b = (p) and LSB_MASK
        out[byteIdx++] = r.toByte()
        out[byteIdx++] = g.toByte()
        out[byteIdx++] = b.toByte()
        out[byteIdx++] = a.toByte()
    }
    return out
}

const val ROUNDING_BIAS = 127

/** Helper: Premultiply a channel by alpha: c' = c * (α / 255) (with integer rounding). */
private fun premulChannel(c: Int, a: Int): Int = (c * a + ROUNDING_BIAS) / 255

/** Helper: Premultiply an array of ARGB ints. */
private fun premultiplyARGBInts(straight: IntArray): IntArray {
    val out = IntArray(straight.size)
    for (i in straight.indices) {
        val pixel = straight[i]
        val a = (pixel ushr 24) and LSB_MASK
        val r = (pixel ushr 16) and LSB_MASK
        val g = (pixel ushr 8) and LSB_MASK
        val b = pixel and LSB_MASK
        when (a) {
            255 -> out[i] = pixel
            0 -> out[i] = 0
            else -> {
                val premulR = premulChannel(r, a)
                val premulG = premulChannel(g, a)
                val premulB = premulChannel(b, a)
                out[i] = (a shl 24) or (premulR shl 16) or (premulG shl 8) or premulB
            }
        }
    }
    return out
}

/** Helper: Premultiply an array of RGBA bytes. */
private fun premultiplyRGBABytes(straight: ByteArray): ByteArray {
    require(straight.size % 4 == 0) { "RGBA byte array length must be a multiple of 4" }
    val out = ByteArray(straight.size)
    for (i in straight.indices step 4) {
        val r = straight[i].toInt() and LSB_MASK
        val g = straight[i + 1].toInt() and LSB_MASK
        val b = straight[i + 2].toInt() and LSB_MASK
        when (val a = straight[i + 3].toInt() and LSB_MASK) {
            255 -> (0..3).forEach { offset -> out[i + offset] = straight[i + offset] }
            0 -> (0..3).forEach { offset -> out[i + offset] = 0 }
            else -> {
                val premulR = premulChannel(r, a)
                val premulG = premulChannel(g, a)
                val premulB = premulChannel(b, a)
                out[i] = premulR.toByte()
                out[i + 1] = premulG.toByte()
                out[i + 2] = premulB.toByte()
                out[i + 3] = a.toByte()
            }
        }
    }
    return out
}

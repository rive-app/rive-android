package app.rive.runtime.kotlin.core

import android.content.Context
import android.graphics.RectF
import android.os.Build
import android.os.Process
import app.rive.RiveLog
import app.rive.runtime.kotlin.core.Rive.init
import app.rive.runtime.kotlin.core.Rive.initializeCppEnvironment
import app.rive.runtime.kotlin.fonts.FontHelper
import app.rive.runtime.kotlin.fonts.Fonts
import app.rive.runtime.kotlin.fonts.NativeFontHelper
import com.getkeepsafe.relinker.ReLinker

object Rive {
    private const val TAG = "Rive"
    private external fun cppInitialize()
    private external fun cppCalculateRequiredBounds(
        fit: Fit, alignment: Alignment,
        availableBounds: RectF,
        artboardBounds: RectF,
        requiredBounds: RectF,
        scaleFactor: Float
    )

    private const val RIVE_ANDROID = "rive-android"

    private object NativeLoader {
        fun loadLibrary(
            context: Context,
            libraryName: String,
            allowLegacyReLinkerFallback: Boolean
        ) {
            // We assume Marshmallow+ has stable native loading behavior; avoid ReLinker there.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                loadWithSystemLoader(libraryName)
                return
            }

            // Don't use Relinker, regardless of API level.
            if (!allowLegacyReLinkerFallback) {
                loadWithSystemLoader(libraryName)
                return
            }

            // On older Android versions, attempt platform loading first, then legacy ReLinker fallback.
            try {
                loadWithSystemLoader(libraryName)
            } catch (_: UnsatisfiedLinkError) {
                loadWithReLinker(context, libraryName)
            }
        }

        private fun loadWithSystemLoader(libraryName: String) =
            try {
                System.loadLibrary(libraryName)
            } catch (error: UnsatisfiedLinkError) {
                logLoadFailure(libraryName, "System.loadLibrary", error)
                throw error
            }

        private fun loadWithReLinker(context: Context, libraryName: String) =
            try {
                ReLinker
                    /**
                     * `recursively` allows resolving dependent .so files, e.g. the C++ standard
                     * library, when the Rive library .so is separated into another folder, such as
                     * /data/data/, rather than the original /data/apk/
                     */
                    .recursively()
                    .loadLibrary(context, libraryName)
            } catch (error: UnsatisfiedLinkError) {
                logLoadFailure(libraryName, "ReLinker", error)
                throw error
            }

        private fun logLoadFailure(
            libraryName: String,
            loader: String,
            error: UnsatisfiedLinkError
        ) {
            val supportedABIs = Build.SUPPORTED_ABIS.joinToString(prefix = "[", postfix = "]")
            val is64BitDevice =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Process.is64Bit()
                } else {
                    Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
                }
            RiveLog.e(TAG, error) {
                "Failed to load lib$libraryName.so using $loader. " +
                    "Supported ABIs: $supportedABIs. " +
                    "Device bitness: ${if (is64BitDevice) "64-bit" else "32-bit"}. " +
                    "Check your APK/AAB contains lib/<abi>/lib$libraryName.so and verify ABI " +
                    "filters, split APK/dynamic feature delivery, and 32-bit support " +
                    "(for example armeabi-v7a) are not stripped."
            }
        }
    }

    /**
     * Public getter for the default renderer type.
     *
     * This can be customized via [Rive.init].
     */
    var defaultRendererType: RendererType = RendererType.Rive
        private set

    /**
     * Initialises Rive.
     *
     * This loads the C++ libraries required to use Rive objects and then makes sure to initialize
     * the C++ environment.
     *
     * If you're still running into native loading issues (for example with split APKs / dynamic
     * feature delivery), you can load native libs yourself and then call
     * [initializeCppEnvironment].
     * ```
     * System.loadLibrary("rive-android")
     * // or if you're using split APKs / dynamic feature delivery:
     * SplitInstallHelper.loadLibrary(context, "rive-android")
     * Rive.initializeCppEnvironment()
     * ```
     *
     * For split APK / on-demand feature delivery, native libraries can live outside the base APK.
     * In those setups, call `SplitInstallHelper.loadLibrary(...)` from the split context before
     * calling [initializeCppEnvironment]. More details:
     * https://developer.android.com/guide/playcore/feature-delivery/on-demand#native-code
     *
     * @param defaultRenderer The default renderer to use when initializing [File] or
     *    [app.rive.runtime.kotlin.RiveAnimationView][RiveAnimationView]. Defaults to
     *    [RendererType.Rive].
     * @throws UnsatisfiedLinkError if native libraries cannot be loaded.
     */
    fun init(context: Context, defaultRenderer: RendererType = RendererType.Rive) {
        RiveLog.i(TAG) { "Initializing Rive runtime" }
        defaultRendererType = defaultRenderer
        try {
            NativeLoader.loadLibrary(
                context = context,
                libraryName = RIVE_ANDROID,
                allowLegacyReLinkerFallback = true
            )
        } catch (error: UnsatisfiedLinkError) {
            RiveLog.e(TAG) {
                "Native loading failed for librive-android.so. If your app loads native libraries " +
                    "manually, load the Rive library first, then call " +
                    "Rive.initializeCppEnvironment(). For split APK/dynamic feature delivery, " +
                    "load from the split context with SplitInstallHelper.loadLibrary(...) before " +
                    "calling initializeCppEnvironment(). See " +
                    "https://developer.android.com/guide/playcore/feature-delivery/on-demand#native-code"
            }
            throw error
        }
        initializeCppEnvironment()
    }

    /**
     * Initialises the JNI Bindings.
     *
     * We update the C++ environment with a pointer to the JavaVM so that it can interact with JVM
     * objects.
     *
     * Normally done as part of init, and only required if you are avoiding calling [init].
     */
    @JvmStatic
    fun initializeCppEnvironment() = cppInitialize()

    fun calculateRequiredBounds(
        fit: Fit,
        alignment: Alignment,
        availableBounds: RectF,
        artboardBounds: RectF,
        scaleFactor: Float = 1.0f,
    ): RectF {
        val requiredBounds = RectF()
        cppCalculateRequiredBounds(
            fit,
            alignment,
            availableBounds,
            artboardBounds,
            requiredBounds,
            scaleFactor
        )
        return requiredBounds
    }

    /**
     * Set a fallback font for the Rive runtime.
     *
     * @param byteArray The [ByteArray] bytes for a font file.
     * @return Whether the font was successfully registered.
     */
    @Deprecated(
        "Prefer defining a `FontFallbackStrategy` instead",
        level = DeprecationLevel.WARNING
    )
    fun setFallbackFont(byteArray: ByteArray): Boolean =
        NativeFontHelper.cppRegisterFallbackFont(byteArray)

    /**
     * Set a fallback font for the Rive runtime.
     *
     * @param opts The [Fonts.FontOpts] specifying the desired font characteristics. If not
     *    provided, default options are used.
     * @return Whether the font was successfully registered.
     */
    @Deprecated(
        "Prefer defining a `FontFallbackStrategy` instead",
        level = DeprecationLevel.WARNING
    )
    fun setFallbackFont(opts: Fonts.FontOpts? = null): Boolean =
        FontHelper.getFallbackFontBytes(opts)?.let { bytes ->
            NativeFontHelper.cppRegisterFallbackFont(bytes)
        } == true
}

package app.rive

import android.os.Build

/** The preferred GPU rendering backend to use. */
enum class RenderBackend {
    /**
     * Prefer Vulkan rendering when the device and runtime support it.
     *
     * Vulkan is only considered on Android API 29 / Android 10 and newer. API level alone
     * does not guarantee a usable Vulkan driver, so worker creation may fall back to [OpenGL]
     * when Vulkan native initialization fails.
     */
    Vulkan,

    /** Use the OpenGL ES renderer. */
    OpenGL,
}

/**
 * Resolves the backend that should be attempted first for the current platform.
 *
 * @param renderBackend Caller-requested backend preference.
 * @param sdkInt Android API level to evaluate. Defaults to the device API level.
 * @return [RenderBackend.Vulkan] only when Vulkan was requested and the API level can support it;
 *    otherwise [RenderBackend.OpenGL].
 */
internal fun effectiveRenderBackend(
    renderBackend: RenderBackend,
    sdkInt: Int = Build.VERSION.SDK_INT,
): RenderBackend =
    if (renderBackend == RenderBackend.Vulkan && sdkInt >= Build.VERSION_CODES.Q) {
        RenderBackend.Vulkan
    } else {
        RenderBackend.OpenGL
    }

package app.rive

import android.os.Build

internal actual fun effectiveRenderBackend(renderBackend: RenderBackend): RenderBackend =
    effectiveRenderBackend(renderBackend, Build.VERSION.SDK_INT)

/**
 * Android backend gate with an injectable API level for tests.
 *
 * @return [RenderBackend.Vulkan] only when Vulkan was requested and the API level can support it;
 *    otherwise [RenderBackend.OpenGL].
 */
internal fun effectiveRenderBackend(
    renderBackend: RenderBackend,
    sdkInt: Int,
): RenderBackend =
    if (renderBackend == RenderBackend.Vulkan && sdkInt >= Build.VERSION_CODES.Q) {
        RenderBackend.Vulkan
    } else {
        RenderBackend.OpenGL
    }

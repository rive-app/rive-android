package app.rive.core

import app.rive.RenderBackend
import app.rive.RiveInitializationException

internal actual fun createPlatformBridge(): CommandQueueBridge {
    RiveNative.ensureLoaded()
    return CommandQueueJNIBridge()
}

internal actual fun createPlatformRenderContext(renderBackend: RenderBackend): RenderContext {
    RiveNative.ensureLoaded()
    return when (renderBackend) {
        RenderBackend.Vulkan -> RenderContextVulkan()
        RenderBackend.OpenGL -> throw RiveInitializationException(
            "Desktop rendering is Vulkan-only (MoltenVK on macOS)"
        )
    }
}

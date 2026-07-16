package app.rive

// Desktop rendering is Vulkan-only (MoltenVK on macOS), regardless of preference.
internal actual fun effectiveRenderBackend(renderBackend: RenderBackend): RenderBackend =
    RenderBackend.Vulkan

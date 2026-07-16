package app.rive

/** The preferred GPU rendering backend to use. */
enum class RenderBackend {
    /**
     * Prefer Vulkan rendering when the device and runtime support it.
     *
     * On Android, Vulkan is only considered on API 29 / Android 10 and newer. API level alone
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
 * @return The backend to attempt first; platforms may downgrade the request (e.g. Android
 *    returns [RenderBackend.OpenGL] when the API level cannot support Vulkan).
 */
internal expect fun effectiveRenderBackend(renderBackend: RenderBackend): RenderBackend

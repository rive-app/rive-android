package app.rive.core

import app.rive.RiveLog

/**
 * Desktop Vulkan rendering context (MoltenVK on macOS). Offscreen image surfaces only — desktop
 * has no window-surface path; frames are read back and blitted into the composition.
 *
 * Mirrors the Android class of the same name so the shared JNI symbols
 * (`Java_app_rive_core_RenderContextVulkan_*`) resolve against either binary.
 */
internal class RenderContextVulkan : RenderContext() {
    private external fun cppConstructor(): Long
    private external fun cppDelete(pointer: Long)

    companion object {
        const val TAG = "Rive/RenderContextVulkan"
    }

    private val cppPointer = UniquePointer(cppConstructor(), TAG) { pointer ->
        RiveLog.d(TAG) { "Deleting RenderContextVulkan native object" }
        cppDelete(pointer)
    }

    /** The native pointer to the RenderContextVulkan object. */
    override val nativeObjectPointer: Long
        get() = cppPointer.pointer

    override fun dispose() = cppPointer.close()

    override fun createImageSurface(
        width: Int,
        height: Int,
        drawKey: DrawKey,
        commandQueue: CommandQueue,
    ): RiveSurface = RiveSurfaceVulkanImage.create(this, width, height, commandQueue, drawKey)
}

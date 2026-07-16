#pragma once

#ifdef __ANDROID__
#include <EGL/egl.h>
#endif
#include <cstdint>
#include <memory>
#include <string>

#include "models/render_surface.hpp"
#include "rive/renderer/render_context.hpp"
#include "rive/renderer/render_target.hpp"
#include "rive/renderer/rive_render_image.hpp"

#ifdef RIVE_VULKAN
#include "models/render_surface_vulkan.hpp"
#endif

struct ANativeWindow;

namespace rive_vkb
{
class VulkanDevice;
class VulkanInstance;
} // namespace rive_vkb

#ifdef RIVE_VULKAN
namespace rive
{
namespace gpu
{
class RenderContextVulkanImpl;
class VulkanContext;
} // namespace gpu
} // namespace rive
#endif

namespace rive_android
{

constexpr const char* TAG_RC = "RiveN/RenderContext";

/**
 * Result of startup, both in RenderContext initialization and CommandQueue
 * startup.
 */
struct StartupResult
{
    bool success;
    int32_t errorCode;
    std::string message;
};

/**
 * Abstract base class for native RenderContext implementations.
 * Contains the needed operations for:
 * - Initialization of resources
 * - Destruction of resources
 * - Beginning a frame (binding the context to a surface)
 * - Presenting a frame (swapping buffers)
 *
 * Also holds a pointer to the Rive RenderContext instance.
 *
 * ⚠️ This class has thread affinity. It must be initialized, used, and
 * destroyed on the thread with the backend rendering context (i.e. the command
 * server thread), though it may be created and deleted on a different thread.
 */
class RenderContext
{
public:
    virtual ~RenderContext() = default;

    /**
     * Initialize the RenderContext and its resources. Call on the render
     * thread.
     */
    virtual StartupResult initialize() = 0;

    /**
     * Destroy backend resources owned by this RenderContext.
     *
     * Must be called on the render thread. Implementations must tolerate being
     * called after failed or partial initialization, and must be idempotent.
     */
    virtual void destroy() = 0;

    /**
     * Make a renderable Rive image from decoded image data.
     *
     * @param width The width of the image.
     * @param height The height of the image.
     * @param imageDataRGBA The decoded image data in RGBA format.
     * @return A renderable rive::RenderImage backed by the underlying
     *   renderer's texture type.
     */
    virtual rive::rcp<rive::RenderImage> createRenderImage(
        uint32_t width,
        uint32_t height,
        std::unique_ptr<const uint8_t[]> imageDataRGBA) = 0;

    /**
     * Create a render target for a backend-specific surface.
     *
     * Width and height are requested dimensions supplied by the runtime.
     * Backends may adjust or ignore them when the final drawable extent is
     * defined by prepared surface resources.
     *
     * @param surface Backend-specific surface pointer.
     * @param width Requested render target width in pixels.
     * @param height Requested render target height in pixels.
     * @return The created render target, or nullptr if the backend cannot
     * create a compatible target.
     */
    virtual rive::gpu::RenderTarget* createRenderTarget(RenderSurface* surface,
                                                        uint32_t width,
                                                        uint32_t height) = 0;

    // Frame rendering calls these in sequence:
    // beginFrame(), flush(), present().

    /**
     * Prepare a backend surface for drawing and return its concrete render
     * target.
     *
     * @param surface Backend-specific surface pointer.
     * @return The concrete render target for this frame, or nullptr if drawing
     * cannot proceed.
     */
    virtual rive::gpu::RenderTarget* beginFrame(RenderSurface* surface) = 0;
    /**
     * Flush backend-specific render commands to the surface's current target.
     *
     * @param surface Backend-specific surface pointer.
     * @return true if the frame was flushed.
     */
    virtual bool flush(RenderSurface* surface) = 0;
    /**
     * Present the frame by swapping buffers on the provided surface.
     *
     * @param surface Backend-specific surface pointer.
     * @return true if the frame was presented.
     */
    virtual bool present(RenderSurface* surface) = 0;

    /**
     * Read pixels from the current render target into an RGBA buffer.
     *
     * @param surface Backend-specific surface pointer.
     * @param width Width to read in pixels.
     * @param height Height to read in pixels.
     * @param pixels Destination RGBA buffer.
     * @return true if pixels were read into the destination buffer.
     */
    virtual bool readPixels(RenderSurface* surface,
                            uint32_t width,
                            uint32_t height,
                            uint8_t* pixels) = 0;

    std::unique_ptr<rive::gpu::RenderContext> riveContext;
};

#ifdef __ANDROID__
/** Native RenderContext implementation for EGL/OpenGL ES. */
struct RenderContextGL : RenderContext
{
    RenderContextGL(EGLDisplay eglDisplay, EGLContext eglContext);
    ~RenderContextGL() override = default;

    StartupResult initialize() override;
    void destroy() override;

    rive::rcp<rive::RenderImage> createRenderImage(
        uint32_t width,
        uint32_t height,
        std::unique_ptr<const uint8_t[]> imageDataRGBA) override;

    rive::gpu::RenderTarget* createRenderTarget(RenderSurface*,
                                                uint32_t width,
                                                uint32_t height) override;

    rive::gpu::RenderTarget* beginFrame(RenderSurface* surface) override;
    bool flush(RenderSurface* surface) override;
    bool present(RenderSurface* surface) override;

    bool readPixels(RenderSurface* surface,
                    uint32_t width,
                    uint32_t height,
                    uint8_t* pixels) override;

    EGLDisplay eglDisplay;
    EGLContext eglContext;

private:
    /** A 1x1 PBuffer to bind to the context (some devices do not support
     * surface-less bindings).
     * We must have a valid binding for `MakeContext` to succeed. */
    EGLSurface pBuffer;
};
#endif // __ANDROID__

#ifdef RIVE_VULKAN

/** Native RenderContext implementation for Vulkan. */
struct RenderContextVulkan : RenderContext
{
    RenderContextVulkan();
    ~RenderContextVulkan() override;

    StartupResult initialize() override;
    void destroy() override;

#ifdef __ANDROID__
    RenderSurfaceVulkan* createWindowSurface(ANativeWindow* nativeWindow,
                                             int width,
                                             int height);
#endif

    static RenderSurfaceVulkan* createImageSurface(int width, int height);

    rive::rcp<rive::RenderImage> createRenderImage(
        uint32_t width,
        uint32_t height,
        std::unique_ptr<const uint8_t[]> imageDataRGBA) override;

    rive::gpu::RenderTarget* createRenderTarget(RenderSurface* nativeSurface,
                                                uint32_t width,
                                                uint32_t height) override;

    rive::gpu::RenderTarget* beginFrame(RenderSurface* nativeSurface) override;
    bool flush(RenderSurface* nativeSurface) override;
    bool present(RenderSurface* nativeSurface) override;

    bool readPixels(RenderSurface* nativeSurface,
                    uint32_t width,
                    uint32_t height,
                    uint8_t* pixels) override;

private:
    [[nodiscard]] rive::gpu::RenderContextVulkanImpl* impl() const;
    [[nodiscard]] rive::gpu::VulkanContext* vk() const;
    bool ensureFrameSurface(RenderSurfaceVulkan* surface);
    bool ensureSwapchain(VulkanWindowSurface& window);
    bool ensureHeadlessFrameSynchronizer(VulkanImageSurface& image);

    std::unique_ptr<rive_vkb::VulkanInstance> m_instance;
    std::unique_ptr<rive_vkb::VulkanDevice> m_device;
};

#endif

} // namespace rive_android

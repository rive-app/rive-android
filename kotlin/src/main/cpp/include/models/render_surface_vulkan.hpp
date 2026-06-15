#pragma once

#ifdef RIVE_VULKAN

#include <cstdint>
#include <memory>
#include <variant>
#include <vulkan/vulkan.h>

#include "models/render_surface.hpp"

struct ANativeWindow;

namespace rive_vkb
{
class VulkanDevice;
class VulkanFrameSynchronizer;
class VulkanHeadlessFrameSynchronizer;
class VulkanSwapchain;
} // namespace rive_vkb

namespace rive_android
{

/**
 * Pending state for VulkanWindowSurface.
 *
 * The window surface has an Android/Vulkan surface but no prepared swapchain.
 * RenderContextVulkan consumes this state during beginFrame() to lazily create
 * the swapchain on the command server thread.
 */
struct VulkanPendingWindowSurface
{
    explicit VulkanPendingWindowSurface(uint64_t initialFrameNumber = 0);

    // Frame number to resume from when the swapchain is recreated.
    uint64_t initialFrameNumber = 0;
};

/**
 * Prepared state for VulkanWindowSurface.
 *
 * The window surface owns a swapchain that can provide frame metadata through
 * VulkanFrameSynchronizer.
 */
struct VulkanPreparedWindowSurface
{
    explicit VulkanPreparedWindowSurface(
        std::unique_ptr<rive_vkb::VulkanSwapchain>&& swapchain);

    std::unique_ptr<rive_vkb::VulkanSwapchain> swapchain;
};

/**
 * Android-window-backed Vulkan surface state.
 *
 * Owns the ANativeWindow reference, VkSurfaceKHR, and either pending or
 * prepared swapchain state for a TextureView-backed RiveSurface.
 */
struct VulkanWindowSurface
{
    VulkanWindowSurface(ANativeWindow* nativeWindow,
                        VkInstance instance,
                        PFN_vkDestroySurfaceKHR destroySurfaceKHR);
    ~VulkanWindowSurface();

    /**
     * @return The prepared swapchain synchronizer, or nullptr before
     * resources have been created.
     */
    [[nodiscard]] rive_vkb::VulkanFrameSynchronizer* synchronizer();
    /** @return The prepared swapchain, or nullptr while creation is pending. */
    [[nodiscard]] rive_vkb::VulkanSwapchain* swapchain();
    /** @return Pending creation state, or nullptr when a swapchain is prepared.
     */
    [[nodiscard]] VulkanPendingWindowSurface* pending();
    /** Move this window surface into the prepared state. */
    void prepare(std::unique_ptr<rive_vkb::VulkanSwapchain>&& swapchain);
    /** Drop the prepared swapchain and mark it for lazy recreation. */
    void invalidate();

    ANativeWindow* nativeWindow = nullptr;
    VkInstance instance = VK_NULL_HANDLE;
    VkSurfaceKHR surface = VK_NULL_HANDLE;
    PFN_vkDestroySurfaceKHR destroySurfaceKHR = nullptr;
    std::variant<VulkanPendingWindowSurface, VulkanPreparedWindowSurface> state;
};

/**
 * Pending state for VulkanImageSurface.
 *
 * The image surface has fixed requested dimensions but no prepared headless
 * synchronizer. RenderContextVulkan consumes this state during beginFrame() to
 * lazily create off-screen frame resources on the command server thread.
 */
struct VulkanPendingImageSurface
{
    VulkanPendingImageSurface(uint32_t width,
                              uint32_t height,
                              uint64_t initialFrameNumber = 0);

    uint32_t width = 0;
    uint32_t height = 0;
    // Frame number to resume from when the synchronizer is recreated.
    uint64_t initialFrameNumber = 0;
};

/**
 * Prepared state for VulkanImageSurface.
 *
 * The image surface owns a headless synchronizer that provides off-screen
 * frame metadata through VulkanFrameSynchronizer.
 */
struct VulkanPreparedImageSurface
{
    explicit VulkanPreparedImageSurface(
        std::unique_ptr<rive_vkb::VulkanHeadlessFrameSynchronizer>&&
            synchronizer);

    std::unique_ptr<rive_vkb::VulkanHeadlessFrameSynchronizer> synchronizer;
};

/**
 * Off-screen Vulkan image surface state.
 *
 * Owns either pending fixed-size creation data or a prepared headless frame
 * synchronizer for drawToBuffer/image rendering.
 */
struct VulkanImageSurface
{
    VulkanImageSurface(uint32_t width, uint32_t height);
    ~VulkanImageSurface();

    /**
     * @return The prepared headless synchronizer, or nullptr before resources
     * have been created.
     */
    [[nodiscard]] rive_vkb::VulkanFrameSynchronizer* synchronizer();
    /**
     * @return The prepared headless synchronizer, or nullptr while creation is
     * pending.
     */
    [[nodiscard]] rive_vkb::VulkanHeadlessFrameSynchronizer*
    headlessFrameSynchronizer();
    /**
     * @return Pending creation state, or nullptr when a synchronizer is
     * prepared.
     */
    [[nodiscard]] VulkanPendingImageSurface* pending();
    /** Move this image surface into the prepared state. */
    void prepare(std::unique_ptr<rive_vkb::VulkanHeadlessFrameSynchronizer>&&
                     synchronizer);

    std::variant<VulkanPendingImageSurface, VulkanPreparedImageSurface> state;
};

/**
 * Per-surface Vulkan state owned by Kotlin RiveSurface wrappers.
 *
 * RenderContextVulkan owns shared backend state such as the instance, device,
 * and Rive render context. This type holds the native state that varies per
 * Android or off-screen surface.
 *
 * Its backend variant makes the window and image paths exclusive: one surface
 * owns either an Android VkSurfaceKHR and swapchain, or a headless frame
 * synchronizer, never both.
 */
struct RenderSurfaceVulkan : RenderSurface
{
    static std::unique_ptr<RenderSurfaceVulkan> MakeWindow(
        rive_vkb::VulkanDevice* device,
        ANativeWindow* nativeWindow,
        VkInstance instance,
        PFN_vkDestroySurfaceKHR destroySurfaceKHR,
        int width,
        int height);

    static std::unique_ptr<RenderSurfaceVulkan> MakeImage(int width,
                                                          int height);

    ~RenderSurfaceVulkan();

    [[nodiscard]] VulkanWindowSurface* window();
    [[nodiscard]] VulkanImageSurface* image();
    /**
     * @return The prepared synchronizer for the active backend variant, or
     * nullptr before resources have been created.
     */
    [[nodiscard]] rive_vkb::VulkanFrameSynchronizer* synchronizer();

    std::variant<VulkanWindowSurface, VulkanImageSurface> backend;
    // Borrowed from RenderContextVulkan so destruction can wait for idle before
    // releasing per-surface Vulkan resources. RenderContextVulkan must outlive
    // every RenderSurfaceVulkan created from it.
    rive_vkb::VulkanDevice* device = nullptr;

private:
    void onResize() override;

    RenderSurfaceVulkan(rive_vkb::VulkanDevice* device,
                        ANativeWindow* nativeWindow,
                        VkInstance instance,
                        PFN_vkDestroySurfaceKHR destroySurfaceKHR,
                        uint32_t width,
                        uint32_t height);

    RenderSurfaceVulkan(uint32_t width, uint32_t height);
};

} // namespace rive_android

#endif

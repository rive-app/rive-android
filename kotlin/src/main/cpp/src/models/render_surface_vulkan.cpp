#include "models/render_surface_vulkan.hpp"

#ifdef RIVE_VULKAN

#ifdef __ANDROID__
#include <android/native_window.h>
#endif
#include <utility>

#include "rive_vk_bootstrap/vulkan_device.hpp"
#include "rive_vk_bootstrap/vulkan_headless_frame_synchronizer.hpp"
#include "rive_vk_bootstrap/vulkan_swapchain.hpp"

namespace rive_android
{

VulkanPendingWindowSurface::VulkanPendingWindowSurface(
    uint64_t initialFrameNumber) :
    initialFrameNumber(initialFrameNumber)
{}

VulkanPreparedWindowSurface::VulkanPreparedWindowSurface(
    std::unique_ptr<rive_vkb::VulkanSwapchain>&& swapchain) :
    swapchain(std::move(swapchain))
{}

VulkanWindowSurface::VulkanWindowSurface(
    ANativeWindow* nativeWindow,
    VkInstance instance,
    PFN_vkDestroySurfaceKHR destroySurfaceKHR) :
    nativeWindow(nativeWindow),
    instance(instance),
    destroySurfaceKHR(destroySurfaceKHR),
    state(std::in_place_type<VulkanPendingWindowSurface>)
{
#ifdef __ANDROID__
    ANativeWindow_acquire(this->nativeWindow);
#endif
}

VulkanWindowSurface::~VulkanWindowSurface()
{
    state.emplace<VulkanPendingWindowSurface>();
    if (surface != VK_NULL_HANDLE && destroySurfaceKHR != nullptr &&
        instance != VK_NULL_HANDLE)
    {
        destroySurfaceKHR(instance, surface, nullptr);
    }
    if (nativeWindow != nullptr)
    {
#ifdef __ANDROID__
        ANativeWindow_release(nativeWindow);
#endif
    }
}

rive_vkb::VulkanFrameSynchronizer* VulkanWindowSurface::synchronizer()
{
    return swapchain();
}

rive_vkb::VulkanSwapchain* VulkanWindowSurface::swapchain()
{
    auto* prepared = std::get_if<VulkanPreparedWindowSurface>(&state);
    return prepared != nullptr ? prepared->swapchain.get() : nullptr;
}

VulkanPendingWindowSurface* VulkanWindowSurface::pending()
{
    return std::get_if<VulkanPendingWindowSurface>(&state);
}

void VulkanWindowSurface::prepare(
    std::unique_ptr<rive_vkb::VulkanSwapchain>&& swapchain)
{
    state.emplace<VulkanPreparedWindowSurface>(std::move(swapchain));
}

void VulkanWindowSurface::invalidate()
{
    auto initialFrameNumber = uint64_t{0};
    if (auto* pending = std::get_if<VulkanPendingWindowSurface>(&state))
    {
        initialFrameNumber = pending->initialFrameNumber;
    }
    else if (auto* prepared = std::get_if<VulkanPreparedWindowSurface>(&state))
    {
        if (prepared->swapchain != nullptr)
        {
            initialFrameNumber = prepared->swapchain->currentFrameNumber();
        }
    }
    state.emplace<VulkanPendingWindowSurface>(initialFrameNumber);
}

VulkanPendingImageSurface::VulkanPendingImageSurface(
    uint32_t width,
    uint32_t height,
    uint64_t initialFrameNumber) :
    width(width), height(height), initialFrameNumber(initialFrameNumber)
{}

VulkanPreparedImageSurface::VulkanPreparedImageSurface(
    std::unique_ptr<rive_vkb::VulkanHeadlessFrameSynchronizer>&& synchronizer) :
    synchronizer(std::move(synchronizer))
{}

VulkanImageSurface::VulkanImageSurface(uint32_t width, uint32_t height) :
    state(std::in_place_type<VulkanPendingImageSurface>, width, height)
{}

VulkanImageSurface::~VulkanImageSurface() = default;

rive_vkb::VulkanFrameSynchronizer* VulkanImageSurface::synchronizer()
{
    return headlessFrameSynchronizer();
}

rive_vkb::VulkanHeadlessFrameSynchronizer* VulkanImageSurface::
    headlessFrameSynchronizer()
{
    auto* prepared = std::get_if<VulkanPreparedImageSurface>(&state);
    return prepared != nullptr ? prepared->synchronizer.get() : nullptr;
}

VulkanPendingImageSurface* VulkanImageSurface::pending()
{
    return std::get_if<VulkanPendingImageSurface>(&state);
}

void VulkanImageSurface::prepare(
    std::unique_ptr<rive_vkb::VulkanHeadlessFrameSynchronizer>&& synchronizer)
{
    state.emplace<VulkanPreparedImageSurface>(std::move(synchronizer));
}

std::unique_ptr<RenderSurfaceVulkan> RenderSurfaceVulkan::MakeWindow(
    rive_vkb::VulkanDevice* device,
    ANativeWindow* nativeWindow,
    VkInstance instance,
    PFN_vkDestroySurfaceKHR destroySurfaceKHR,
    int width,
    int height)
{
    return std::unique_ptr<RenderSurfaceVulkan>(
        new RenderSurfaceVulkan(device,
                                nativeWindow,
                                instance,
                                destroySurfaceKHR,
                                static_cast<uint32_t>(width),
                                static_cast<uint32_t>(height)));
}

std::unique_ptr<RenderSurfaceVulkan> RenderSurfaceVulkan::MakeImage(int width,
                                                                    int height)
{
    return std::unique_ptr<RenderSurfaceVulkan>(
        new RenderSurfaceVulkan(static_cast<uint32_t>(width),
                                static_cast<uint32_t>(height)));
}

RenderSurfaceVulkan::~RenderSurfaceVulkan()
{
    if (device != nullptr)
    {
        device->waitUntilIdle();
    }
    // Drop the Rive target before backend members tear down their swapchain or
    // headless images. The target only keeps non-owning handles to those frame
    // images, and device-idle above guarantees no in-flight command buffer can
    // still reference them during teardown.
    resetRenderTarget();
}

VulkanWindowSurface* RenderSurfaceVulkan::window()
{
    return std::get_if<VulkanWindowSurface>(&backend);
}

VulkanImageSurface* RenderSurfaceVulkan::image()
{
    return std::get_if<VulkanImageSurface>(&backend);
}

rive_vkb::VulkanFrameSynchronizer* RenderSurfaceVulkan::synchronizer()
{
    if (auto* window = this->window())
    {
        return window->synchronizer();
    }
    return this->image()->synchronizer();
}

void RenderSurfaceVulkan::onResize()
{
    if (auto* window = this->window())
    {
        window->invalidate();
    }
}

RenderSurfaceVulkan::RenderSurfaceVulkan(
    rive_vkb::VulkanDevice* device,
    ANativeWindow* nativeWindow,
    VkInstance instance,
    PFN_vkDestroySurfaceKHR destroySurfaceKHR,
    uint32_t width,
    uint32_t height) :
    RenderSurface(width, height),
    backend(std::in_place_type<VulkanWindowSurface>,
            nativeWindow,
            instance,
            destroySurfaceKHR),
    device(device)
{}

RenderSurfaceVulkan::RenderSurfaceVulkan(uint32_t width, uint32_t height) :
    RenderSurface(width, height),
    backend(std::in_place_type<VulkanImageSurface>, width, height)
{}

} // namespace rive_android

#endif

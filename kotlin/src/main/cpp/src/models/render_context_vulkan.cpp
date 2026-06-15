#include "models/render_context.hpp"

#ifdef RIVE_VULKAN

#include <algorithm>
#include <android/native_window.h>
#include <cassert>
#include <cstring>
#include <memory>
#include <utility>
#include <variant>
#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>

#include "helpers/general.hpp"
#include "helpers/rive_log.hpp"
#include "rive/gpu_texture_format.hpp"
#include "rive/renderer/vulkan/render_context_vulkan_impl.hpp"
#include "rive/renderer/vulkan/render_target_vulkan.hpp"
#include "rive_vk_bootstrap/vulkan_device.hpp"
#include "rive_vk_bootstrap/vulkan_headless_frame_synchronizer.hpp"
#include "rive_vk_bootstrap/vulkan_instance.hpp"
#include "rive_vk_bootstrap/vulkan_swapchain.hpp"

namespace rive_android
{

namespace
{
// Combines lambdas into one overload set for std::visit on std::variant.
// This is the common C++17 pattern for exhaustive variant dispatch.
template <typename... Ts> struct Overloaded : Ts...
{
    using Ts::operator()...;
};
template <typename... Ts> Overloaded(Ts...) -> Overloaded<Ts...>;

constexpr VkFormat HEADLESS_IMAGE_FORMAT = VK_FORMAT_R8G8B8A8_UNORM;
constexpr VkImageUsageFlags HEADLESS_IMAGE_USAGE_FLAGS =
    VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT |
    VK_IMAGE_USAGE_TRANSFER_DST_BIT;

bool missingPreparedFrameResource(const char* operation, const char* resource)
{
    assert(false && "Vulkan frame resource missing after prepare");
    RiveLogE(TAG_RC,
             "Internal Vulkan render error: %s missing during %s",
             resource,
             operation);
    return false;
}

/** Drop the current window swapchain so the next frame recreates it. */
void invalidateSwapchain(RenderSurfaceVulkan* surface)
{
    auto* window = surface->window();
    if (window != nullptr)
    {
        window->invalidate();
        surface->resetRenderTarget();
    }
}

/**
 * Invalidate the window swapchain when Vulkan reports a recoverable
 * presentation-surface state.
 *
 * @return True if the result was handled by invalidating the swapchain.
 */
bool invalidateSwapchainIfRecoverable(RenderSurfaceVulkan* surface,
                                      VkResult result)
{
    if (result == VK_ERROR_OUT_OF_DATE_KHR)
    {
        RiveLogD(TAG_RC,
                 "Vulkan window swapchain is out of date; invalidating for "
                 "recreation");
        invalidateSwapchain(surface);
        return true;
    }
    if (result == VK_SUBOPTIMAL_KHR)
    {
        RiveLogD(TAG_RC,
                 "Vulkan window swapchain is suboptimal; invalidating for "
                 "recreation");
        invalidateSwapchain(surface);
        return true;
    }
    return false;
}

bool beginWindowFrame(RenderSurfaceVulkan* surface,
                      VulkanWindowSurface& window,
                      rive::gpu::RenderTargetVulkanImpl& vulkanTarget)
{
    auto* synchronizer = window.synchronizer();
    if (synchronizer == nullptr)
    {
        return missingPreparedFrameResource("beginFrame", "swapchain");
    }
    auto* swapchain = window.swapchain();
    if (swapchain == nullptr)
    {
        return missingPreparedFrameResource("beginFrame", "swapchain");
    }
    if (!swapchain->isFrameStarted())
    {
        auto result = swapchain->beginFrame();
        if (result != VK_SUCCESS)
        {
            if (invalidateSwapchainIfRecoverable(surface, result))
            {
                return false;
            }
            RiveLogE(TAG_RC,
                     "Failed to begin Vulkan swapchain frame: %d",
                     result);
            return false;
        }
    }
    vulkanTarget.setTargetImageView(synchronizer->vkImageView(),
                                    synchronizer->vkImage(),
                                    synchronizer->lastAccess());
    return true;
}

bool beginImageFrame(VulkanImageSurface& image,
                     rive::gpu::RenderTargetVulkanImpl& vulkanTarget)
{
    auto* synchronizer = image.synchronizer();
    if (synchronizer == nullptr)
    {
        return missingPreparedFrameResource("beginFrame",
                                            "headless frame synchronizer");
    }
    auto* frameSynchronizer = image.headlessFrameSynchronizer();
    if (frameSynchronizer == nullptr)
    {
        return missingPreparedFrameResource("beginFrame",
                                            "headless frame synchronizer");
    }
    if (!frameSynchronizer->isFrameStarted())
    {
        auto result = frameSynchronizer->beginFrame();
        if (result != VK_SUCCESS)
        {
            RiveLogE(TAG_RC, "Failed to begin Vulkan image frame: %d", result);
            return false;
        }
    }
    vulkanTarget.setTargetImageView(synchronizer->vkImageView(),
                                    synchronizer->vkImage(),
                                    synchronizer->lastAccess());
    return true;
}

bool flushWindowFrame(rive::gpu::RenderContext* riveContext,
                      VulkanWindowSurface& window,
                      rive::gpu::RenderTarget* renderTarget)
{
    auto* synchronizer = window.synchronizer();
    if (synchronizer == nullptr)
    {
        return missingPreparedFrameResource("flush", "swapchain");
    }
    riveContext->flush({
        .renderTarget = renderTarget,
        .externalCommandBuffer = synchronizer->currentCommandBuffer(),
        .currentFrameNumber = synchronizer->currentFrameNumber(),
        .safeFrameNumber = synchronizer->safeFrameNumber(),
    });
    return true;
}

bool flushImageFrame(rive::gpu::RenderContext* riveContext,
                     VulkanImageSurface& image,
                     rive::gpu::RenderTarget* renderTarget)
{
    auto* synchronizer = image.synchronizer();
    if (synchronizer == nullptr)
    {
        return missingPreparedFrameResource("flush",
                                            "headless frame synchronizer");
    }
    riveContext->flush({
        .renderTarget = renderTarget,
        .externalCommandBuffer = synchronizer->currentCommandBuffer(),
        .currentFrameNumber = synchronizer->currentFrameNumber(),
        .safeFrameNumber = synchronizer->safeFrameNumber(),
    });
    return true;
}

bool presentWindowFrame(RenderSurfaceVulkan* surface,
                        VulkanWindowSurface& window,
                        rive::gpu::RenderTargetVulkanImpl& vulkanTarget)
{
    auto* synchronizer = window.synchronizer();
    if (synchronizer == nullptr)
    {
        return missingPreparedFrameResource("present", "swapchain");
    }
    auto* swapchain = window.swapchain();
    if (swapchain == nullptr)
    {
        return missingPreparedFrameResource("present", "swapchain");
    }
    auto lastAccess = vulkanTarget.targetLastAccess();
    auto result = swapchain->endFrame(lastAccess);
    if (result != VK_SUCCESS)
    {
        if (invalidateSwapchainIfRecoverable(surface, result))
        {
            return false;
        }
        RiveLogE(TAG_RC, "Failed to present Vulkan frame: %d", result);
        return false;
    }
    return true;
}

bool presentImageFrame(VulkanImageSurface& image,
                       rive::gpu::RenderTargetVulkanImpl& vulkanTarget)
{
    auto* synchronizer = image.synchronizer();
    if (synchronizer == nullptr)
    {
        return missingPreparedFrameResource("present",
                                            "headless frame synchronizer");
    }
    auto* frameSynchronizer = image.headlessFrameSynchronizer();
    if (frameSynchronizer == nullptr)
    {
        return missingPreparedFrameResource("present",
                                            "headless frame synchronizer");
    }
    auto lastAccess = vulkanTarget.targetLastAccess();
    auto result = frameSynchronizer->endFrame(lastAccess);
    if (result != VK_SUCCESS)
    {
        RiveLogE(TAG_RC, "Failed to present Vulkan frame: %d", result);
        return false;
    }
    return true;
}

class PixelReadFinishGuard
{
public:
    explicit PixelReadFinishGuard(
        rive_vkb::VulkanFrameSynchronizer* synchronizer) :
        m_synchronizer(synchronizer)
    {}

    PixelReadFinishGuard(const PixelReadFinishGuard&) = delete;
    PixelReadFinishGuard& operator=(const PixelReadFinishGuard&) = delete;

    ~PixelReadFinishGuard() { m_synchronizer->finishPixelRead(); }

private:
    rive_vkb::VulkanFrameSynchronizer* m_synchronizer;
};

void copyMappedPixelReadToRGBA(
    const rive_vkb::VulkanFrameSynchronizer::MappedPixelRead& read,
    uint32_t width,
    uint32_t height,
    uint8_t* pixels)
{
    if (pixels == nullptr || read.data == nullptr)
    {
        return;
    }

    const uint32_t copyWidth = std::min(width, read.width);
    const uint32_t copyHeight = std::min(height, read.height);
    const size_t dstStrideBytes = static_cast<size_t>(width) * 4;
    const size_t rowBytes = static_cast<size_t>(copyWidth) * 4;

    for (auto y = 0u; y < copyHeight; y++)
    {
        const uint8_t* src =
            read.data + read.strideBytes * (read.height - 1 - y);
        uint8_t* dst = pixels + y * dstStrideBytes;
        std::memcpy(dst, src, rowBytes);

        if (read.format == VK_FORMAT_B8G8R8A8_UNORM)
        {
            // Need to swap BGRA -> RGBA.
            for (auto x = 0u; x < rowBytes; x += 4)
            {
                std::swap(dst[x], dst[x + 2]);
            }
        }
    }
}

bool readWindowPixels(VulkanWindowSurface& window,
                      rive::gpu::RenderTargetVulkanImpl& vulkanTarget,
                      uint32_t width,
                      uint32_t height,
                      uint8_t* pixels)
{
    auto* synchronizer = window.synchronizer();
    if (synchronizer == nullptr)
    {
        return missingPreparedFrameResource("readPixels", "swapchain");
    }
    auto* swapchain = window.swapchain();
    if (swapchain == nullptr)
    {
        return missingPreparedFrameResource("readPixels", "swapchain");
    }
    auto lastAccess = vulkanTarget.targetLastAccess();
    swapchain->queueImageCopy(
        &lastAccess,
        rive::IAABB::MakeWH(static_cast<int>(width), static_cast<int>(height)));
    auto result = swapchain->endFrame(lastAccess);
    if (result == VK_SUCCESS)
    {
        rive_vkb::VulkanFrameSynchronizer::MappedPixelRead read;
        result = swapchain->waitForPixelRead(&read);
        if (result == VK_SUCCESS)
        {
            PixelReadFinishGuard finishGuard(swapchain);
            copyMappedPixelReadToRGBA(read, width, height, pixels);
        }
    }
    if (result != VK_SUCCESS)
    {
        RiveLogE(TAG_RC,
                 "Failed to read pixels from Vulkan swapchain: %d",
                 result);
        return false;
    }
    return true;
}

bool readImagePixels(VulkanImageSurface& image,
                     rive::gpu::RenderTargetVulkanImpl& vulkanTarget,
                     uint32_t width,
                     uint32_t height,
                     uint8_t* pixels)
{
    auto* synchronizer = image.synchronizer();
    if (synchronizer == nullptr)
    {
        return missingPreparedFrameResource("readPixels",
                                            "headless frame synchronizer");
    }
    auto* frameSynchronizer = image.headlessFrameSynchronizer();
    if (frameSynchronizer == nullptr)
    {
        return missingPreparedFrameResource("readPixels",
                                            "headless frame synchronizer");
    }
    auto lastAccess = vulkanTarget.targetLastAccess();
    frameSynchronizer->queueImageCopy(
        &lastAccess,
        rive::IAABB::MakeWH(static_cast<int>(width), static_cast<int>(height)));
    auto result = frameSynchronizer->endFrame(lastAccess);
    if (result == VK_SUCCESS)
    {
        rive_vkb::VulkanFrameSynchronizer::MappedPixelRead read;
        result = frameSynchronizer->waitForPixelRead(&read);
        if (result == VK_SUCCESS)
        {
            PixelReadFinishGuard finishGuard(frameSynchronizer);
            copyMappedPixelReadToRGBA(read, width, height, pixels);
        }
    }
    if (result != VK_SUCCESS)
    {
        RiveLogE(TAG_RC,
                 "Failed to read pixels from Vulkan image surface: %d",
                 result);
        return false;
    }
    return true;
}
} // namespace

RenderContextVulkan::RenderContextVulkan() = default;

RenderContextVulkan::~RenderContextVulkan() = default;

StartupResult RenderContextVulkan::initialize()
{
    using namespace rive_vkb;

    RiveLogD(TAG_RC, "Creating Vulkan instance");
    const char* extensionNames[] = {
        VK_KHR_SURFACE_EXTENSION_NAME,
        VK_KHR_ANDROID_SURFACE_EXTENSION_NAME,
    };
    m_instance = VulkanInstance::Create(VulkanInstance::Options{
        .appName = "Rive Android Runtime",
        .idealAPIVersion = VK_API_VERSION_1_3,
        .requiredExtensions =
            rive::make_span(extensionNames,
                            sizeof(extensionNames) / sizeof(extensionNames[0])),
    });
    if (m_instance == nullptr)
    {
        return {false,
                VK_ERROR_INITIALIZATION_FAILED,
                "Failed to create Vulkan instance"};
    }

    RiveLogD(TAG_RC, "Creating Vulkan device");
    m_device = VulkanDevice::Create(*m_instance,
                                    VulkanDevice::Options{
                                        .printInitializationMessage = true,
                                    });
    if (m_device == nullptr)
    {
        return {false,
                VK_ERROR_INITIALIZATION_FAILED,
                "Failed to create Vulkan device"};
    }

    RiveLogD(TAG_RC, "Creating Rive RenderContextVulkan");
    riveContext = rive::gpu::RenderContextVulkanImpl::MakeContext(
        m_instance->vkInstance(),
        m_device->vkPhysicalDevice(),
        m_device->vkDevice(),
        m_device->vulkanFeatures(),
        m_instance->getVkGetInstanceProcAddrPtr());
    if (!riveContext)
    {
        return {false,
                VK_ERROR_INITIALIZATION_FAILED,
                "Failed to create Rive RenderContextVulkan"};
    }

    auto* renderContextImpl = impl();
    auto* vulkanContext = renderContextImpl->vulkanContext();
    auto vkGetDeviceQueue = reinterpret_cast<PFN_vkGetDeviceQueue>(
        vulkanContext->GetDeviceProcAddr(m_device->vkDevice(),
                                         "vkGetDeviceQueue"));
    if (vkGetDeviceQueue == nullptr)
    {
        RiveLogE(TAG_RC,
                 "Failed to load vkGetDeviceQueue for Vulkan canvas support");
    }
    else
    {
        VkQueue graphicsQueue = VK_NULL_HANDLE;
        vkGetDeviceQueue(m_device->vkDevice(),
                         m_device->graphicsQueueFamilyIndex(),
                         0,
                         &graphicsQueue);
        renderContextImpl->setCanvasQueue(graphicsQueue,
                                          m_device->graphicsQueueFamilyIndex());
    }

    return {true, VK_SUCCESS, "RenderContextVulkan initialized successfully"};
}

void RenderContextVulkan::destroy()
{
    if (m_device != nullptr)
    {
        m_device->waitUntilIdle();
    }

    riveContext = nullptr;
    m_device = nullptr;
    m_instance = nullptr;
}

RenderSurfaceVulkan* RenderContextVulkan::createWindowSurface(
    ANativeWindow* nativeWindow,
    int width,
    int height)
{
    auto destroySurfaceKHR =
        m_instance->loadInstanceFunc<PFN_vkDestroySurfaceKHR>(
            "vkDestroySurfaceKHR");
    auto surface = RenderSurfaceVulkan::MakeWindow(m_device.get(),
                                                   nativeWindow,
                                                   m_instance->vkInstance(),
                                                   destroySurfaceKHR,
                                                   width,
                                                   height);
    auto* window = surface->window();

    VkAndroidSurfaceCreateInfoKHR createInfo = {
        .sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR,
        .window = window->nativeWindow,
    };
    auto createAndroidSurfaceKHR =
        m_instance->loadInstanceFunc<PFN_vkCreateAndroidSurfaceKHR>(
            "vkCreateAndroidSurfaceKHR");
    if (createAndroidSurfaceKHR == nullptr)
    {
        RiveLogE(TAG_RC, "Failed to load vkCreateAndroidSurfaceKHR");
        return nullptr;
    }
    VkResult result = createAndroidSurfaceKHR(m_instance->vkInstance(),
                                              &createInfo,
                                              nullptr,
                                              &window->surface);
    if (result != VK_SUCCESS)
    {
        RiveLogE(TAG_RC, "Failed to create Android Vulkan surface: %d", result);
        return nullptr;
    }

    return surface.release();
}

RenderSurfaceVulkan* RenderContextVulkan::createImageSurface(int width,
                                                             int height)
{
    return RenderSurfaceVulkan::MakeImage(width, height).release();
}

rive::rcp<rive::RenderImage> RenderContextVulkan::createRenderImage(
    uint32_t width,
    uint32_t height,
    std::unique_ptr<const uint8_t[]> imageDataRGBA)
{
    auto mipLevelCount = rive::math::msb(height | width);
    auto texture =
        riveContext->impl()->makeImageTexture(width,
                                              height,
                                              mipLevelCount,
                                              rive::GPUTextureFormat::rgba32,
                                              imageDataRGBA.get());
    return rive::make_rcp<rive::RiveRenderImage>(texture);
}

rive::gpu::RenderTarget* RenderContextVulkan::createRenderTarget(
    RenderSurface* nativeSurface,
    uint32_t width,
    uint32_t height)
{
    auto surface = static_cast<RenderSurfaceVulkan*>(nativeSurface);
    auto* synchronizer = surface->synchronizer();
    if (synchronizer == nullptr)
    {
        missingPreparedFrameResource("createRenderTarget",
                                     "frame synchronizer");
        return nullptr;
    }
    if (synchronizer->width() != width || synchronizer->height() != height)
    {
        RiveLogD(TAG_RC,
                 "Vulkan render target extent differs from requested size "
                 "(requested: %u x %u, actual: %u x %u)",
                 width,
                 height,
                 synchronizer->width(),
                 synchronizer->height());
    }

    auto renderTarget =
        impl()->makeRenderTarget(synchronizer->width(),
                                 synchronizer->height(),
                                 synchronizer->imageFormat(),
                                 synchronizer->imageUsageFlags());
    return renderTarget.release();
}

rive::gpu::RenderTarget* RenderContextVulkan::beginFrame(
    RenderSurface* nativeSurface)
{
    auto surface = static_cast<RenderSurfaceVulkan*>(nativeSurface);
    if (!ensureFrameSurface(surface))
    {
        return nullptr;
    }
    auto* renderTarget = surface->getOrCreateRenderTarget(this);
    auto vulkanTarget =
        static_cast<rive::gpu::RenderTargetVulkanImpl*>(renderTarget);
    if (vulkanTarget == nullptr)
    {
        return nullptr;
    }

    auto beganFrame = std::visit(
        Overloaded{
            [&](VulkanWindowSurface& window) {
                return beginWindowFrame(surface, window, *vulkanTarget);
            },
            [&](VulkanImageSurface& image) {
                return beginImageFrame(image, *vulkanTarget);
            },
        },
        surface->backend);
    return beganFrame ? renderTarget : nullptr;
}

bool RenderContextVulkan::flush(RenderSurface* nativeSurface)
{
    auto surface = static_cast<RenderSurfaceVulkan*>(nativeSurface);
    auto* renderTarget = surface->renderTarget();
    if (renderTarget == nullptr)
    {
        return false;
    }
    return std::visit(
        Overloaded{
            [&](VulkanWindowSurface& window) {
                return flushWindowFrame(riveContext.get(),
                                        window,
                                        renderTarget);
            },
            [&](VulkanImageSurface& image) {
                return flushImageFrame(riveContext.get(), image, renderTarget);
            },
        },
        surface->backend);
}

bool RenderContextVulkan::present(RenderSurface* nativeSurface)
{
    auto surface = static_cast<RenderSurfaceVulkan*>(nativeSurface);
    auto* renderTarget = surface->renderTarget();
    auto vulkanTarget =
        static_cast<rive::gpu::RenderTargetVulkanImpl*>(renderTarget);
    if (vulkanTarget == nullptr)
    {
        return false;
    }
    return std::visit(
        Overloaded{
            [&](VulkanWindowSurface& window) {
                return presentWindowFrame(surface, window, *vulkanTarget);
            },
            [&](VulkanImageSurface& image) {
                return presentImageFrame(image, *vulkanTarget);
            },
        },
        surface->backend);
}

bool RenderContextVulkan::readPixels(RenderSurface* nativeSurface,
                                     uint32_t width,
                                     uint32_t height,
                                     uint8_t* pixels)
{
    auto surface = static_cast<RenderSurfaceVulkan*>(nativeSurface);
    auto* renderTarget = surface->renderTarget();
    auto vulkanTarget =
        static_cast<rive::gpu::RenderTargetVulkanImpl*>(renderTarget);
    if (vulkanTarget == nullptr)
    {
        return false;
    }
    return std::visit(Overloaded{
                          [&](VulkanWindowSurface& window) {
                              return readWindowPixels(window,
                                                      *vulkanTarget,
                                                      width,
                                                      height,
                                                      pixels);
                          },
                          [&](VulkanImageSurface& image) {
                              return readImagePixels(image,
                                                     *vulkanTarget,
                                                     width,
                                                     height,
                                                     pixels);
                          },
                      },
                      surface->backend);
}

rive::gpu::RenderContextVulkanImpl* RenderContextVulkan::impl() const
{
    return riveContext->static_impl_cast<rive::gpu::RenderContextVulkanImpl>();
}

rive::gpu::VulkanContext* RenderContextVulkan::vk() const
{
    return impl()->vulkanContext();
}

bool RenderContextVulkan::ensureFrameSurface(RenderSurfaceVulkan* surface)
{
    surface->device = m_device.get();
    return std::visit(Overloaded{
                          [&](VulkanWindowSurface& window) {
                              return ensureSwapchain(window);
                          },
                          [&](VulkanImageSurface& image) {
                              return ensureHeadlessFrameSynchronizer(image);
                          },
                      },
                      surface->backend);
}

bool RenderContextVulkan::ensureSwapchain(VulkanWindowSurface& window)
{
    auto* pending = window.pending();
    if (pending == nullptr)
    {
        return true;
    }

    VkSurfaceCapabilitiesKHR windowCapabilities;
    auto result =
        m_device->getSurfaceCapabilities(window.surface, &windowCapabilities);
    if (result != VK_SUCCESS)
    {
        RiveLogE(TAG_RC,
                 "Failed to query Vulkan surface capabilities: %d",
                 result);
        return false;
    }

    auto swapchainOptions = rive_vkb::VulkanSwapchain::Options{
        .formatPreferences =
            {
                {
                    .format = VK_FORMAT_R8G8B8A8_UNORM,
                    .colorSpace = VK_COLOR_SPACE_SRGB_NONLINEAR_KHR,
                },
                {
                    .format = VK_FORMAT_B8G8R8A8_UNORM,
                    .colorSpace = VK_COLOR_SPACE_SRGB_NONLINEAR_KHR,
                },
            },
        .presentModePreferences =
            {
                VK_PRESENT_MODE_FIFO_KHR,
            },
        .imageUsageFlags = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT |
                           VK_IMAGE_USAGE_TRANSFER_SRC_BIT |
                           VK_IMAGE_USAGE_TRANSFER_DST_BIT,
        .initialFrameNumber = pending->initialFrameNumber,
    };

    if ((windowCapabilities.supportedUsageFlags &
         VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT) != 0)
    {
        swapchainOptions.imageUsageFlags |= VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT;
    }

    auto swapchain = rive_vkb::VulkanSwapchain::Create(*m_instance,
                                                       *m_device,
                                                       rive::ref_rcp(vk()),
                                                       window.surface,
                                                       swapchainOptions);
    if (swapchain == nullptr)
    {
        RiveLogE(TAG_RC, "Failed to create Vulkan swapchain");
        return false;
    }

    window.prepare(std::move(swapchain));
    return true;
}

bool RenderContextVulkan::ensureHeadlessFrameSynchronizer(
    VulkanImageSurface& image)
{
    auto* pending = image.pending();
    if (pending == nullptr)
    {
        return true;
    }

    auto frameSynchronizer = rive_vkb::VulkanHeadlessFrameSynchronizer::Create(
        *m_instance,
        *m_device,
        rive::ref_rcp(vk()),
        rive_vkb::VulkanHeadlessFrameSynchronizer::Options{
            .width = pending->width,
            .height = pending->height,
            .imageFormat = HEADLESS_IMAGE_FORMAT,
            .imageUsageFlags = HEADLESS_IMAGE_USAGE_FLAGS,
            .initialFrameNumber = pending->initialFrameNumber,
        });
    if (frameSynchronizer == nullptr)
    {
        RiveLogE(TAG_RC, "Failed to create Vulkan headless frame synchronizer");
        return false;
    }
    image.prepare(std::move(frameSynchronizer));
    return true;
}

} // namespace rive_android

#endif

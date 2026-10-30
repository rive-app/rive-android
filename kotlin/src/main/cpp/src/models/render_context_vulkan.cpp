#include "models/render_context.hpp"

#ifdef RIVE_VULKAN

#include <algorithm>
#include <android/native_window.h>
#include <cassert>
#include <cstring>
#include <fcntl.h>
#include <fstream>
#include <iterator>
#include <memory>
#include <mutex>
#include <unistd.h>
#include <utility>
#include <variant>
#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>

#include "helpers/general.hpp"
#include "helpers/rive_log.hpp"
#include "rive/gpu_texture_format.hpp"
#include "rive/renderer/ore/ore_context.hpp"
#if defined(RIVE_CANVAS) && defined(RIVE_ORE)
#include "rive/renderer/ore/ore_context_vulkan.hpp"
#endif
#include "rive/renderer/vulkan/render_context_vulkan_impl.hpp"
#include "rive/renderer/vulkan/render_target_vulkan.hpp"
#include "rive_vk_bootstrap/vulkan_device.hpp"
#include "rive_vk_bootstrap/vulkan_frame_sync_coordinator.hpp"
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
constexpr VkImageUsageFlags
    HEADLESS_IMAGE_USAGE_FLAGS = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT |
                                 VK_IMAGE_USAGE_TRANSFER_SRC_BIT |
                                 VK_IMAGE_USAGE_TRANSFER_DST_BIT;

std::mutex g_pipelineCacheDirMutex;
std::string g_pipelineCacheDir;
// Every worker shares one cache file.
std::mutex g_pipelineCacheSaveMutex;

std::vector<uint8_t> readFile(const std::string& path)
{
  std::ifstream file(path, std::ios::binary);
  return {
      std::istreambuf_iterator<char>(file),
      std::istreambuf_iterator<char>()};
}

/**
 * Writes beside the target, syncs, and renames, so readers never see a torn
 * file, even after a power loss.
 */
bool writeFileAtomically(
    const std::string& path,
    const std::vector<uint8_t>& data)
{
  auto temp = path + "." + std::to_string(gettid());
  int fd = open(temp.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
  if (fd < 0)
  {
    return false;
  }
  bool written = write(fd, data.data(), data.size()) ==
                     static_cast<ssize_t>(data.size()) &&
                 fsync(fd) == 0;
  close(fd);
  if (!written || rename(temp.c_str(), path.c_str()) != 0)
  {
    unlink(temp.c_str());
    return false;
  }
  return true;
}

bool missingPreparedFrameResource(const char* operation, const char* resource)
{
  assert(false && "Vulkan frame resource missing after prepare");
  RiveLogE(
      TAG_RC,
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
    surface->unregisterFrameSynchronizer();
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
bool invalidateSwapchainIfRecoverable(
    RenderSurfaceVulkan* surface,
    VkResult result)
{
  if (result == VK_ERROR_OUT_OF_DATE_KHR)
  {
    RiveLogD(
        TAG_RC,
        "Vulkan window swapchain is out of date; invalidating for "
        "recreation");
    invalidateSwapchain(surface);
    return true;
  }
  if (result == VK_SUBOPTIMAL_KHR)
  {
    RiveLogD(
        TAG_RC,
        "Vulkan window swapchain is suboptimal; invalidating for "
        "recreation");
    invalidateSwapchain(surface);
    return true;
  }
  return false;
}

bool beginWindowFrame(
    RenderContext& context,
    RenderSurfaceVulkan* surface,
    VulkanWindowSurface& window,
    rive::gpu::RenderTargetVulkanImpl& vulkanTarget,
    rive_vkb::VulkanFrameSyncCoordinator& coordinator)
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
      if (result == VK_ERROR_DEVICE_LOST)
      {
        context.markContextLost("Vulkan beginFrame");
      }
      RiveLogE(TAG_RC, "Failed to begin Vulkan swapchain frame: %d", result);
      return false;
    }
    coordinator.onFrameStart(synchronizer);
  }
  vulkanTarget.setTargetImageView(
      synchronizer->vkImageView(),
      synchronizer->vkImage(),
      synchronizer->lastAccess());
  return true;
}

bool beginImageFrame(
    RenderContext& context,
    VulkanImageSurface& image,
    rive::gpu::RenderTargetVulkanImpl& vulkanTarget,
    rive_vkb::VulkanFrameSyncCoordinator& coordinator)
{
  auto* synchronizer = image.synchronizer();
  if (synchronizer == nullptr)
  {
    return missingPreparedFrameResource(
        "beginFrame",
        "headless frame synchronizer");
  }
  auto* frameSynchronizer = image.headlessFrameSynchronizer();
  if (frameSynchronizer == nullptr)
  {
    return missingPreparedFrameResource(
        "beginFrame",
        "headless frame synchronizer");
  }
  if (!frameSynchronizer->isFrameStarted())
  {
    auto result = frameSynchronizer->beginFrame();
    if (result != VK_SUCCESS)
    {
      if (result == VK_ERROR_DEVICE_LOST)
      {
        context.markContextLost("Vulkan beginFrame");
      }
      RiveLogE(TAG_RC, "Failed to begin Vulkan image frame: %d", result);
      return false;
    }
    coordinator.onFrameStart(synchronizer);
  }
  vulkanTarget.setTargetImageView(
      synchronizer->vkImageView(),
      synchronizer->vkImage(),
      synchronizer->lastAccess());
  return true;
}

bool flushWindowFrame(
    rive::gpu::RenderContext* riveContext,
    VulkanWindowSurface& window,
    rive::gpu::RenderTarget* renderTarget,
    uint64_t currentFrameNumber,
    uint64_t safeFrameNumber)
{
  auto* synchronizer = window.synchronizer();
  if (synchronizer == nullptr)
  {
    return missingPreparedFrameResource("flush", "swapchain");
  }
  riveContext->flush({
      .renderTarget = renderTarget,
      .externalCommandBuffer = synchronizer->currentCommandBuffer(),
      .currentFrameNumber = currentFrameNumber,
      .safeFrameNumber = safeFrameNumber,
  });
  return true;
}

bool flushImageFrame(
    rive::gpu::RenderContext* riveContext,
    VulkanImageSurface& image,
    rive::gpu::RenderTarget* renderTarget,
    uint64_t currentFrameNumber,
    uint64_t safeFrameNumber)
{
  auto* synchronizer = image.synchronizer();
  if (synchronizer == nullptr)
  {
    return missingPreparedFrameResource("flush", "headless frame synchronizer");
  }
  riveContext->flush({
      .renderTarget = renderTarget,
      .externalCommandBuffer = synchronizer->currentCommandBuffer(),
      .currentFrameNumber = currentFrameNumber,
      .safeFrameNumber = safeFrameNumber,
  });
  return true;
}

bool presentWindowFrame(
    RenderContext& context,
    RenderSurfaceVulkan* surface,
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
    if (result == VK_ERROR_DEVICE_LOST)
    {
      context.markContextLost("Vulkan present");
    }
    RiveLogE(TAG_RC, "Failed to present Vulkan frame: %d", result);
    return false;
  }
  return true;
}

bool presentImageFrame(
    RenderContext& context,
    VulkanImageSurface& image,
    rive::gpu::RenderTargetVulkanImpl& vulkanTarget)
{
  auto* synchronizer = image.synchronizer();
  if (synchronizer == nullptr)
  {
    return missingPreparedFrameResource(
        "present",
        "headless frame synchronizer");
  }
  auto* frameSynchronizer = image.headlessFrameSynchronizer();
  if (frameSynchronizer == nullptr)
  {
    return missingPreparedFrameResource(
        "present",
        "headless frame synchronizer");
  }
  auto lastAccess = vulkanTarget.targetLastAccess();
  auto result = frameSynchronizer->endFrame(lastAccess);
  if (result != VK_SUCCESS)
  {
    if (result == VK_ERROR_DEVICE_LOST)
    {
      context.markContextLost("Vulkan present");
    }
    RiveLogE(TAG_RC, "Failed to present Vulkan frame: %d", result);
    return false;
  }
  return true;
}

class PixelReadFinishGuard
{
public:
  explicit PixelReadFinishGuard(rive_vkb::VulkanFrameSynchronizer* synchronizer)
      : m_synchronizer(synchronizer)
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
    const uint8_t* src = read.data + read.strideBytes * (read.height - 1 - y);
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

bool readWindowPixels(
    RenderContext& context,
    VulkanWindowSurface& window,
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
    if (result == VK_ERROR_DEVICE_LOST)
    {
      context.markContextLost("Vulkan readPixels");
    }
    RiveLogE(TAG_RC, "Failed to read pixels from Vulkan swapchain: %d", result);
    return false;
  }
  return true;
}

bool readImagePixels(
    RenderContext& context,
    VulkanImageSurface& image,
    rive::gpu::RenderTargetVulkanImpl& vulkanTarget,
    uint32_t width,
    uint32_t height,
    uint8_t* pixels)
{
  auto* synchronizer = image.synchronizer();
  if (synchronizer == nullptr)
  {
    return missingPreparedFrameResource(
        "readPixels",
        "headless frame synchronizer");
  }
  auto* frameSynchronizer = image.headlessFrameSynchronizer();
  if (frameSynchronizer == nullptr)
  {
    return missingPreparedFrameResource(
        "readPixels",
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
    if (result == VK_ERROR_DEVICE_LOST)
    {
      context.markContextLost("Vulkan readPixels");
    }
    RiveLogE(
        TAG_RC,
        "Failed to read pixels from Vulkan image surface: %d",
        result);
    return false;
  }
  return true;
}
} // namespace

RenderContextVulkan::RenderContextVulkan(bool enableDebugNames)
    : m_frameSyncCoordinator(
          std::make_unique<rive_vkb::VulkanFrameSyncCoordinator>())
    , m_enableDebugNames(enableDebugNames)
{}

RenderContextVulkan::~RenderContextVulkan() = default;

void RenderContextVulkan::SetPipelineCacheDir(std::string dir)
{
  std::lock_guard lock(g_pipelineCacheDirMutex);
  g_pipelineCacheDir = std::move(dir);
}

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
      .requiredExtensions = rive::make_span(
          extensionNames,
          sizeof(extensionNames) / sizeof(extensionNames[0])),
  });
  if (m_instance == nullptr)
  {
    return {
        false,
        VK_ERROR_INITIALIZATION_FAILED,
        "Failed to create Vulkan instance"};
  }

  RiveLogD(TAG_RC, "Creating Vulkan device");
  m_device = VulkanDevice::Create(
      *m_instance,
      VulkanDevice::Options{
          .printInitializationMessage = true,
      });
  if (m_device == nullptr)
  {
    return {
        false,
        VK_ERROR_INITIALIZATION_FAILED,
        "Failed to create Vulkan device"};
  }

  {
    std::lock_guard lock(g_pipelineCacheDirMutex);
    if (!g_pipelineCacheDir.empty())
    {
      m_pipelineCachePath = g_pipelineCacheDir + "/rive_vulkan_pipelines.bin";
    }
  }
  std::vector<uint8_t> pipelineCacheData;
  if (!m_pipelineCachePath.empty())
  {
    pipelineCacheData = readFile(m_pipelineCachePath);
  }

  RiveLogD(TAG_RC, "Creating Rive RenderContextVulkan");
  if (!m_enableDebugNames)
  {
    // GFXStream's ranchu driver crashes inside
    // vkSetDebugUtilsObjectNameEXT even when VK_EXT_debug_utils is enabled.
    RiveLogD(TAG_RC, "Disabling Vulkan debug names for the GFXStream emulator");
  }
  riveContext = rive::gpu::RenderContextVulkanImpl::MakeContext(
      m_instance->vkInstance(),
      m_device->vkPhysicalDevice(),
      m_device->vkDevice(),
      m_device->vulkanFeatures(),
      m_instance->getVkGetInstanceProcAddrPtr(),
      rive::gpu::RenderContextVulkanImpl::ContextOptions{
          .enableDebugNames = m_enableDebugNames,
          .pipelineCacheData = rive::make_span(
              pipelineCacheData.data(),
              pipelineCacheData.size()),
      });
  if (!riveContext)
  {
    return {
        false,
        VK_ERROR_INITIALIZATION_FAILED,
        "Failed to create Rive RenderContextVulkan"};
  }
  // A rejected file is from another driver, so any new cache replaces it.
  m_savedPipelineCacheSize = vk()->pipelineCacheSeeded()
                                 ? vk()->pipelineCacheDataSize()
                                 : 0;

  auto* renderContextImpl = impl();
  auto* vulkanContext = renderContextImpl->vulkanContext();
  auto vkGetDeviceQueue = reinterpret_cast<PFN_vkGetDeviceQueue>(
      vulkanContext->GetDeviceProcAddr(
          m_device->vkDevice(),
          "vkGetDeviceQueue"));
  if (vkGetDeviceQueue == nullptr)
  {
    RiveLogE(
        TAG_RC,
        "Failed to load vkGetDeviceQueue for Vulkan canvas support");
  }
  else
  {
    VkQueue graphicsQueue = VK_NULL_HANDLE;
    vkGetDeviceQueue(
        m_device->vkDevice(),
        m_device->graphicsQueueFamilyIndex(),
        0,
        &graphicsQueue);
    renderContextImpl->setCanvasQueue(
        graphicsQueue,
        m_device->graphicsQueueFamilyIndex());
  }

  return {true, VK_SUCCESS, "RenderContextVulkan initialized successfully"};
}

void RenderContextVulkan::precompileShaders()
{
  m_precompileShaders = true;
  const auto interlockMode = riveContext->selectInterlockMode({});
  // RGBA8 is our preferred window format and fixed headless format. Warm
  // both usage profiles before a surface exists. Pipeline keys deduplicate
  // permutations shared between these profiles and later surfaces. Prefer
  // input-attachment windows so headless-only atomic resolve pipelines do
  // not consume their compilation head start.
  for (auto usage :
       {HEADLESS_IMAGE_USAGE_FLAGS | VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT,
        HEADLESS_IMAGE_USAGE_FLAGS})
  {
    impl()->startAsyncPipelineCreation(
        interlockMode,
        HEADLESS_IMAGE_FORMAT,
        usage,
        rive::gpu::LoadAction::clear);
  }
}

bool RenderContextVulkan::pipelineCacheGrew() const
{
  return !m_pipelineCachePath.empty() && riveContext != nullptr &&
         vk()->pipelineCacheDataSize() > m_savedPipelineCacheSize;
}

bool RenderContextVulkan::pipelineCacheSaveRunning() const
{
  return m_pipelineCacheSave.valid() &&
         m_pipelineCacheSave.wait_for(std::chrono::seconds(0)) !=
             std::future_status::ready;
}

void RenderContextVulkan::savePipelineCache()
{
  size_t size = vk()->pipelineCacheDataSize();
  std::lock_guard lock(g_pipelineCacheSaveMutex);
  auto onDisk = readFile(m_pipelineCachePath);
  auto data = vk()->pipelineCacheData(
      rive::make_span(onDisk.data(), onDisk.size()));
  // Keep what is on disk rather than replace it with a failed snapshot.
  if (data.empty())
  {
    return;
  }
  if (writeFileAtomically(m_pipelineCachePath, data))
  {
    m_savedPipelineCacheSize = size;
  }
  else
  {
    LOGW("Failed to write the Vulkan pipeline cache");
  }
}

void RenderContextVulkan::destroy()
{
  if (m_device != nullptr)
  {
    m_device->waitUntilIdle();
  }
  if (m_pipelineCacheSave.valid())
  {
    m_pipelineCacheSave.wait();
  }
  if (pipelineCacheGrew())
  {
    savePipelineCache();
  }

  // Retain the Vulkan context while renderer shutdown drains deferred GPU
  // resources, so we can detect external owners before destroying the device.
  rive::rcp<rive::gpu::VulkanContext> vk;
  if (riveContext != nullptr)
  {
    vk = rive::ref_rcp(impl()->vulkanContext());
  }
  riveContext = nullptr;
  if (vk != nullptr)
  {
    const auto refCnt = vk->debugging_refcnt();
    if (refCnt != 1)
    {
      RiveLogE(
          TAG_RC,
          "%d Vulkan context reference(s) outlive renderer shutdown",
          refCnt - 1);
      assert(refCnt == 1 && "Vulkan resources must die before VkDevice");
    }
  }
  // Destroy the allocator while its VkDevice and VkInstance are still valid.
  vk = nullptr;
  m_device = nullptr;
  m_instance = nullptr;
}

RenderSurfaceVulkan* RenderContextVulkan::createWindowSurface(
    ANativeWindow* nativeWindow,
    int width,
    int height)
{
  auto destroySurfaceKHR = m_instance
                               ->loadInstanceFunc<PFN_vkDestroySurfaceKHR>(
                                   "vkDestroySurfaceKHR");
  auto surface = RenderSurfaceVulkan::MakeWindow(
      m_device.get(),
      m_frameSyncCoordinator.get(),
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
  auto createAndroidSurfaceKHR = m_instance->loadInstanceFunc<
      PFN_vkCreateAndroidSurfaceKHR>("vkCreateAndroidSurfaceKHR");
  if (createAndroidSurfaceKHR == nullptr)
  {
    RiveLogE(TAG_RC, "Failed to load vkCreateAndroidSurfaceKHR");
    return nullptr;
  }
  VkResult result = createAndroidSurfaceKHR(
      m_instance->vkInstance(),
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

RenderSurfaceVulkan* RenderContextVulkan::createImageSurface(
    int width,
    int height)
{
  return RenderSurfaceVulkan::MakeImage(
             m_device.get(),
             m_frameSyncCoordinator.get(),
             width,
             height)
      .release();
}

rive::rcp<rive::RenderImage> RenderContextVulkan::createRenderImage(
    uint32_t width,
    uint32_t height,
    std::unique_ptr<const uint8_t[]> imageDataRGBA)
{
  auto mipLevelCount = rive::math::msb(height | width);
  // Android ImageDecoder gives us only the base RGBA level; the renderer must
  // generate the remaining mips instead of reading a full mip chain.
  auto texture = riveContext->impl()->makeImageTexture(
      width,
      height,
      mipLevelCount,
      rive::GPUTextureFormat::rgba32,
      imageDataRGBA.get(),
      /*blockWidth=*/1,
      /*blockHeight=*/1,
      /*srgb=*/false,
      /*generateRemainingMips=*/true);
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
    missingPreparedFrameResource("createRenderTarget", "frame synchronizer");
    return nullptr;
  }
  if (synchronizer->width() != width || synchronizer->height() != height)
  {
    RiveLogD(
        TAG_RC,
        "Vulkan render target extent differs from requested size "
        "(requested: %u x %u, actual: %u x %u)",
        width,
        height,
        synchronizer->width(),
        synchronizer->height());
  }

  auto renderTarget = impl()->makeRenderTarget(
      synchronizer->width(),
      synchronizer->height(),
      synchronizer->imageFormat(),
      synchronizer->imageUsageFlags());
  if (m_precompileShaders && renderTarget != nullptr)
  {
    // A window can negotiate a different format or usage from our early
    // warmup. Only missing pipelines are queued, shared by all surfaces.
    impl()->startAsyncPipelineCreation(
        riveContext->selectInterlockMode({}),
        *renderTarget,
        rive::gpu::LoadAction::clear);
  }
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
  auto vulkanTarget = static_cast<rive::gpu::RenderTargetVulkanImpl*>(
      renderTarget);
  if (vulkanTarget == nullptr)
  {
    return nullptr;
  }

  auto beganFrame = std::visit(
      Overloaded{
          [&](VulkanWindowSurface& window) {
            return beginWindowFrame(
                *this,
                surface,
                window,
                *vulkanTarget,
                *m_frameSyncCoordinator);
          },
          [&](VulkanImageSurface& image) {
            return beginImageFrame(
                *this,
                image,
                *vulkanTarget,
                *m_frameSyncCoordinator);
          },
      },
      surface->backend);
  if (!beganFrame)
  {
    return nullptr;
  }

  return renderTarget;
}

#if defined(RIVE_CANVAS) && defined(RIVE_ORE)
rive::ore::Context::TargetDesc RenderContextVulkan::oreTarget(
    rive::gpu::RenderTarget* renderTarget) const
{
  return rive::ore::targetDescFor(
      *static_cast<rive::gpu::RenderTargetVulkan*>(renderTarget));
}
#endif

rive::gpu::RenderContext::FlushResources RenderContextVulkan::
    frameFlushResources(RenderSurface* nativeSurface)
{
  auto* synchronizer = static_cast<RenderSurfaceVulkan*>(nativeSurface)
                           ->synchronizer();
  if (synchronizer == nullptr)
  {
    missingPreparedFrameResource("frameFlushResources", "frame synchronizer");
    return {};
  }
  return {
      .externalCommandBuffer = synchronizer->currentCommandBuffer(),
      .currentFrameNumber = m_frameSyncCoordinator->currentFrameNumber(),
      .safeFrameNumber = m_frameSyncCoordinator->safeFrameNumber(),
  };
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
            return flushWindowFrame(
                riveContext.get(),
                window,
                renderTarget,
                m_frameSyncCoordinator->currentFrameNumber(),
                m_frameSyncCoordinator->safeFrameNumber());
          },
          [&](VulkanImageSurface& image) {
            return flushImageFrame(
                riveContext.get(),
                image,
                renderTarget,
                m_frameSyncCoordinator->currentFrameNumber(),
                m_frameSyncCoordinator->safeFrameNumber());
          },
      },
      surface->backend);
}

bool RenderContextVulkan::present(RenderSurface* nativeSurface)
{
  auto surface = static_cast<RenderSurfaceVulkan*>(nativeSurface);
  auto* renderTarget = surface->renderTarget();
  auto vulkanTarget = static_cast<rive::gpu::RenderTargetVulkanImpl*>(
      renderTarget);
  if (vulkanTarget == nullptr)
  {
    return false;
  }
  auto presented = std::visit(
      Overloaded{
          [&](VulkanWindowSurface& window) {
            return presentWindowFrame(*this, surface, window, *vulkanTarget);
          },
          [&](VulkanImageSurface& image) {
            return presentImageFrame(*this, image, *vulkanTarget);
          },
      },
      surface->backend);
  // An app is usually killed rather than destroying us, so save after the
  // first frame and then whenever new pipelines have compiled.
  auto now = std::chrono::steady_clock::now();
  if (now >= m_nextPipelineCacheSave && !pipelineCacheSaveRunning())
  {
    m_nextPipelineCacheSave = now + std::chrono::seconds(1);
    if (pipelineCacheGrew())
    {
      // Off the worker, so file IO and fsync never delay a frame.
      m_pipelineCacheSave = std::async(
          std::launch::async,
          &RenderContextVulkan::savePipelineCache,
          this);
    }
  }
  return presented;
}

bool RenderContextVulkan::readPixels(
    RenderSurface* nativeSurface,
    uint32_t width,
    uint32_t height,
    uint8_t* pixels)
{
  auto surface = static_cast<RenderSurfaceVulkan*>(nativeSurface);
  auto* renderTarget = surface->renderTarget();
  auto vulkanTarget = static_cast<rive::gpu::RenderTargetVulkanImpl*>(
      renderTarget);
  if (vulkanTarget == nullptr)
  {
    return false;
  }
  return std::visit(
      Overloaded{
          [&](VulkanWindowSurface& window) {
            return readWindowPixels(
                *this,
                window,
                *vulkanTarget,
                width,
                height,
                pixels);
          },
          [&](VulkanImageSurface& image) {
            return readImagePixels(
                *this,
                image,
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
  return std::visit(
      Overloaded{
          [&](VulkanWindowSurface& window) {
            return ensureSwapchain(surface, window);
          },
          [&](VulkanImageSurface& image) {
            return ensureHeadlessFrameSynchronizer(surface, image);
          },
      },
      surface->backend);
}

bool RenderContextVulkan::ensureSwapchain(
    RenderSurfaceVulkan* surface,
    VulkanWindowSurface& window)
{
  auto* pending = window.pending();
  if (pending == nullptr)
  {
    return true;
  }

  VkSurfaceCapabilitiesKHR windowCapabilities;
  auto result = m_device->getSurfaceCapabilities(
      window.surface,
      &windowCapabilities);
  if (result != VK_SUCCESS)
  {
    RiveLogE(TAG_RC, "Failed to query Vulkan surface capabilities: %d", result);
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

  auto swapchain = rive_vkb::VulkanSwapchain::Create(
      *m_instance,
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
  // Registration must happen after ownership moves into the surface so the
  // coordinator observes the same stable pointer through destruction.
  surface->registerFrameSynchronizer();
  return true;
}

bool RenderContextVulkan::ensureHeadlessFrameSynchronizer(
    RenderSurfaceVulkan* surface,
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
  surface->registerFrameSynchronizer();
  return true;
}

} // namespace rive_android

#endif

#include <GLES3/gl3.h>
#include <cstring>
#include <vector>

#include "helpers/egl_error.hpp"
#include "helpers/rive_log.hpp"
#include "models/render_context.hpp"
#include "models/render_surface_gl.hpp"
#include "rive/gpu_texture_format.hpp"
#include "rive/renderer/gl/render_context_gl_impl.hpp"
#include "rive/renderer/gl/render_target_gl.hpp"

namespace rive_android
{
namespace
{
std::string errorString(int32_t errorCode)
{
    return EGLErrorString(static_cast<EGLint>(errorCode));
}

EGLSurface createPBufferSurface(EGLDisplay eglDisplay, EGLContext eglContext)
{
    RiveLogD(TAG_RC, "Creating 1x1 PBuffer surface for EGL context");

    EGLint configID = 0;
    eglQueryContext(eglDisplay, eglContext, EGL_CONFIG_ID, &configID);

    EGLConfig config;
    EGLint configCount = 0;
    EGLint configAttributes[] = {EGL_CONFIG_ID, configID, EGL_NONE};
    eglChooseConfig(eglDisplay, configAttributes, &config, 1, &configCount);

    if (configCount == 1)
    {
        EGLint pBufferAttributes[] = {EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE};
        auto surface =
            eglCreatePbufferSurface(eglDisplay, config, pBufferAttributes);
        if (surface != EGL_NO_SURFACE)
        {
            RiveLogD(TAG_RC, "Successfully created PBuffer surface");
            return surface;
        }

        RiveLogE(TAG_RC,
                 "Failed to create PBuffer surface. Error: %s",
                 errorString(eglGetError()).c_str());
        return EGL_NO_SURFACE;
    }

    RiveLogE(TAG_RC, "Failed to choose EGL config for PBuffer surface");
    return EGL_NO_SURFACE;
}
} // namespace

RenderContextGL::RenderContextGL(EGLDisplay eglDisplay, EGLContext eglContext) :
    RenderContext(),
    eglDisplay(eglDisplay),
    eglContext(eglContext),
    pBuffer(createPBufferSurface(eglDisplay, eglContext))
{}

StartupResult RenderContextGL::initialize()
{
    if (pBuffer == EGL_NO_SURFACE)
    {
        auto error = eglGetError();
        RiveLogE(TAG_RC,
                 "Failed to create PBuffer surface. Error: %s",
                 errorString(eglGetError()).c_str());
        return {false, error, "Failed to create PBuffer surface"};
    }

    RiveLogD(TAG_RC, "Making EGL context current with PBuffer surface");
    auto contextCurrentSuccess =
        eglMakeCurrent(eglDisplay, pBuffer, pBuffer, eglContext);
    if (!contextCurrentSuccess)
    {
        auto error = eglGetError();
        RiveLogE(TAG_RC,
                 "Failed to make EGL context current. Error: %s",
                 errorString(eglGetError()).c_str());
        return {false, error, "Failed to make EGL context current"};
    }

    RiveLogD(TAG_RC, "Creating Rive RenderContextGL");
    riveContext = rive::gpu::RenderContextGLImpl::MakeContext();
    if (!riveContext)
    {
        auto error = eglGetError();
        RiveLogE(TAG_RC,
                 "Failed to create Rive RenderContextGL. Error: %s",
                 errorString(eglGetError()).c_str());
        return {false, error, "Failed to create Rive RenderContextGL"};
    }

    return {true, EGL_SUCCESS, "RenderContextGL initialized successfully"};
}

void RenderContextGL::destroy()
{
    RiveLogD(TAG_RC, "Releasing EGL context and surface bindings");

    eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);

    riveContext = nullptr;

    if (pBuffer != EGL_NO_SURFACE)
    {
        RiveLogD(TAG_RC, "Destroying PBuffer surface");
        eglDestroySurface(eglDisplay, pBuffer);
        pBuffer = EGL_NO_SURFACE;
        if (auto error = eglGetError(); error != EGL_SUCCESS)
        {
            RiveLogE(TAG_RC,
                     "Failed to destroy PBuffer surface. Error: %s",
                     errorString(error).c_str());
        }
    }
}

rive::rcp<rive::RenderImage> RenderContextGL::createRenderImage(
    uint32_t width,
    uint32_t height,
    std::unique_ptr<const uint8_t[]> imageDataRGBA)
{
    auto mipLevelCount = rive::math::msb(height | width);
    RiveLogD(TAG_RC, "Creating RiveRenderImage");
    // Android ImageDecoder gives us only the base RGBA level; the renderer must
    // generate the remaining mips instead of reading a full mip chain.
    auto texture =
        riveContext->impl()->makeImageTexture(width,
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

rive::gpu::RenderTarget* RenderContextGL::createRenderTarget(RenderSurface*,
                                                             uint32_t width,
                                                             uint32_t height)
{
    // GL render target creation only needs the requested dimensions. The
    // surface parameter is present for the shared RenderContext interface,
    // where Vulkan needs it to read prepared frame metadata.
    GLint actualSampleCount = 1;
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glGetIntegerv(GL_SAMPLES, &actualSampleCount);
    RiveLogD(TAG_RC,
             "Creating GL render target (sample count: %d)",
             actualSampleCount);
    return new rive::gpu::FramebufferRenderTargetGL(width,
                                                    height,
                                                    0,
                                                    actualSampleCount);
}

rive::gpu::RenderTarget* RenderContextGL::beginFrame(RenderSurface* surface)
{
    auto* glSurface = static_cast<RenderSurfaceGL*>(surface);
    auto eglSurface = glSurface->eglSurface();
    if (!eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext))
    {
        RiveLogE(
            TAG_RC,
            "Failed to make EGL context current for render target. Error: %s",
            errorString(eglGetError()).c_str());
        return nullptr;
    }
    return glSurface->getOrCreateRenderTarget(this);
}

bool RenderContextGL::flush(RenderSurface* surface)
{
    auto* renderTarget = surface->renderTarget();
    if (renderTarget == nullptr)
    {
        return false;
    }
    riveContext->flush({
        .renderTarget = renderTarget,
    });
    return true;
}

bool RenderContextGL::present(RenderSurface* surface)
{
    auto* glSurface = static_cast<RenderSurfaceGL*>(surface);
    auto eglSurface = glSurface->eglSurface();
    if (!eglSwapBuffers(eglDisplay, eglSurface))
    {
        RiveLogE(TAG_RC,
                 "Failed to swap EGL buffers in present. Error: %s",
                 errorString(eglGetError()).c_str());
        return false;
    }
    return true;
}

bool RenderContextGL::readPixels(RenderSurface*,
                                 uint32_t width,
                                 uint32_t height,
                                 uint8_t* pixels)
{
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glFinish();
    glPixelStorei(GL_PACK_ALIGNMENT, 1);
    glReadPixels(0,
                 0,
                 static_cast<GLsizei>(width),
                 static_cast<GLsizei>(height),
                 GL_RGBA,
                 GL_UNSIGNED_BYTE,
                 pixels);

    auto rowBytes = static_cast<size_t>(width) * 4;
    std::vector<uint8_t> row(rowBytes);
    auto* data = pixels;
    for (uint32_t y = 0; y < height / 2; ++y)
    {
        auto* top = data + (static_cast<size_t>(y) * rowBytes);
        auto* bottom = data + (static_cast<size_t>(height - 1 - y) * rowBytes);
        std::memcpy(row.data(), top, rowBytes);
        std::memcpy(top, bottom, rowBytes);
        std::memcpy(bottom, row.data(), rowBytes);
    }
    return true;
}

} // namespace rive_android

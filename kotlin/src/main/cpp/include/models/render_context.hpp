#pragma once
#include <GLES3/gl3.h>

#include "helpers/general.hpp"
#include "helpers/rive_log.hpp"
#include "rive/renderer/gl/render_context_gl_impl.hpp"
#include "rive/renderer/render_context.hpp"
#include "rive/renderer/rive_render_image.hpp"

#include <cstdint>
#include <EGL/egl.h>

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

/** Map of EGL error codes to their string representations. */
static const std::unordered_map<int32_t, std::string> eglErrorMessages = {
    {EGL_SUCCESS, "EGL_SUCCESS"},
    {EGL_NOT_INITIALIZED, "EGL_NOT_INITIALIZED"},
    {EGL_BAD_ACCESS, "EGL_BAD_ACCESS"},
    {EGL_BAD_ALLOC, "EGL_BAD_ALLOC"},
    {EGL_BAD_ATTRIBUTE, "EGL_BAD_ATTRIBUTE"},
    {EGL_BAD_CONTEXT, "EGL_BAD_CONTEXT"},
    {EGL_BAD_CONFIG, "EGL_BAD_CONFIG"},
    {EGL_BAD_CURRENT_SURFACE, "EGL_BAD_CURRENT_SURFACE"},
    {EGL_BAD_DISPLAY, "EGL_BAD_DISPLAY"},
    {EGL_BAD_SURFACE, "EGL_BAD_SURFACE"},
    {EGL_BAD_MATCH, "EGL_BAD_MATCH"},
    {EGL_BAD_PARAMETER, "EGL_BAD_PARAMETER"},
    {EGL_BAD_NATIVE_PIXMAP, "EGL_BAD_NATIVE_PIXMAP"},
    {EGL_BAD_NATIVE_WINDOW, "EGL_BAD_NATIVE_WINDOW"},
    {EGL_CONTEXT_LOST, "EGL_CONTEXT_LOST"},
};

static std::string errorString(int32_t errorCode)
{
    auto it = eglErrorMessages.find(errorCode);
    if (it != eglErrorMessages.end())
        return it->second;

    char buffer[64];
    auto n = std::snprintf(buffer,
                           sizeof(buffer),
                           "Unknown EGL error (0x%04x)",
                           errorCode);
    if (n < 0)
        return "Unknown EGL error";
    return {buffer, static_cast<std::size_t>(n)};
}

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
    /** Destroy the RenderContext's resources. Call on the render thread. */
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
    virtual rive::rcp<rive::RenderImage> makeImage(
        uint32_t width,
        uint32_t height,
        std::unique_ptr<const uint8_t[]> imageDataRGBA) = 0;

    /** Begin a frame by binding the context to the provided surface. */
    virtual void beginFrame(void* surface) = 0;
    /** Present the frame by swapping buffers on the provided surface. */
    virtual void present(void* surface) = 0;

    std::unique_ptr<rive::gpu::RenderContext> riveContext;
};

/** Create a 1x1 PBuffer surface to bind before Android provides a surface. */
static EGLSurface createPBufferSurface(EGLDisplay eglDisplay,
                                       EGLContext eglContext)
{
    RiveLogD(TAG_RC, "Creating 1x1 PBuffer surface for EGL context");

    EGLint configID = 0;
    eglQueryContext(eglDisplay, eglContext, EGL_CONFIG_ID, &configID);

    EGLConfig config;
    EGLint configCount = 0;
    EGLint configAttributes[] = {EGL_CONFIG_ID, configID, EGL_NONE};
    eglChooseConfig(eglDisplay, configAttributes, &config, 1, &configCount);

    // We expect only one config.
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
        else
        {
            RiveLogE(TAG_RC,
                     "Failed to create PBuffer surface. Error: %s",
                     errorString(eglGetError()).c_str());
            return EGL_NO_SURFACE;
        }
    }
    else
    {
        RiveLogE(TAG_RC, "Failed to choose EGL config for PBuffer surface");
        return EGL_NO_SURFACE;
    }
}

/** Native RenderContext implementation for EGL/OpenGL ES. */
struct RenderContextGL : RenderContext
{
    RenderContextGL(EGLDisplay eglDisplay, EGLContext eglContext) :
        RenderContext(),
        eglDisplay(eglDisplay),
        eglContext(eglContext),
        pBuffer(createPBufferSurface(eglDisplay, eglContext))
    {}

    /**
     * Initialize the RenderContextGL by making the context current with the 1x1
     * PBuffer surface and creating the Rive RenderContextGL.
     *
     * @return Whether initialization succeeded, and the error code/message if
     * not.
     */
    StartupResult initialize() override
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

    /**
     * Destroy the RenderContextGL by releasing the EGL context and destroying
     * the PBuffer surface.
     */
    void destroy() override
    {
        // Cleanup the EGL context and surface
        RiveLogD(TAG_RC, "Releasing EGL context and surface bindings");
        eglMakeCurrent(eglDisplay,
                       EGL_NO_SURFACE,
                       EGL_NO_SURFACE,
                       EGL_NO_CONTEXT);
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

    /** Make a RiveRenderImage from RenderContextImplGL's makeImageTexture. */
    rive::rcp<rive::RenderImage> makeImage(
        uint32_t width,
        uint32_t height,
        std::unique_ptr<const uint8_t[]> imageDataRGBA) override
    {
        auto mipLevelCount = rive::math::msb(height | width);
        RiveLogD(TAG_RC, "Creating RiveRenderImage");
        auto texture =
            riveContext->impl()->makeImageTexture(width,
                                                  height,
                                                  mipLevelCount,
                                                  imageDataRGBA.get());
        return rive::make_rcp<rive::RiveRenderImage>(texture);
    }

    /** Bind the EGL context to the provided surface for rendering. */
    void beginFrame(void* surface) override
    {
        auto eglSurface = static_cast<EGLSurface>(surface);
        if (!eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext))
        {
            RiveLogE(
                TAG_RC,
                "Failed to make EGL context current in beginFrame. Error: %s",
                errorString(eglGetError()).c_str());
        }
    }

    /** Swap the EGL buffers on the provided surface to present the frame. */
    void present(void* surface) override
    {
        auto eglSurface = static_cast<EGLSurface>(surface);
        if (!eglSwapBuffers(eglDisplay, eglSurface))
        {
            RiveLogE(TAG_RC,
                     "Failed to swap EGL buffers in present. Error: %s",
                     errorString(eglGetError()).c_str());
        }
    }

    EGLDisplay eglDisplay;
    EGLContext eglContext;

private:
    /** A 1x1 PBuffer to bind to the context (some devices do not support
     * surface-less bindings).
     * We must have a valid binding for `MakeContext` to succeed. */
    EGLSurface pBuffer;
};

} // namespace rive_android

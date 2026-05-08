#pragma once

#include <GLES3/gl3.h>
#include <cstdint>
#include <memory>

#include "helpers/rive_log.hpp"
#include "rive/renderer/gl/render_target_gl.hpp"

namespace rive_android
{

/**
 * Owns a GL framebuffer render target whose concrete GL resources are created
 * on first use.
 *
 * Android surface creation can happen on the UI thread, while the Rive GL
 * context is owned by the command server thread. This holder allows surface
 * creation to return synchronously without blocking the main thread on command
 * server backlog or GL work. It is returned to Kotlin as an opaque native
 * handle, but it defers GL work until getOrCreate() runs on the command server
 * after the target EGL surface has been made current.
 *
 * Creating the concrete render target is assumed to be cheap enough to perform
 * lazily during the first draw.
 *
 * This holder is uniquely owned by RiveSurface disposal ordering and is deleted
 * on the command server thread after pending draws for the same draw key have
 * been canceled.
 */
class LazyFramebufferRenderTargetGL
{
public:
    LazyFramebufferRenderTargetGL(uint32_t width, uint32_t height) :
        m_width(width), m_height(height)
    {}

    LazyFramebufferRenderTargetGL(const LazyFramebufferRenderTargetGL&) =
        delete;
    LazyFramebufferRenderTargetGL& operator=(
        const LazyFramebufferRenderTargetGL&) = delete;

    /**
     * Returns the concrete GL render target, creating it if necessary.
     *
     * Must be called on the command server thread with the intended EGL surface
     * already current. The current framebuffer's sample count is captured on
     * first creation and reused for the lifetime of this surface.
     */
    rive::gpu::RenderTargetGL* getOrCreate()
    {
        if (!m_renderTarget)
        {
            GLint actualSampleCount = 1;
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glGetIntegerv(GL_SAMPLES, &actualSampleCount);
            RiveLogD("RiveN/LazyRT",
                     "Creating render target on command server "
                     "(sample count: %d)",
                     actualSampleCount);

            m_renderTarget.reset(
                new rive::gpu::FramebufferRenderTargetGL(m_width,
                                                         m_height,
                                                         0, // Framebuffer ID
                                                         actualSampleCount));
        }
        return m_renderTarget.get();
    }

private:
    struct RenderTargetUnref
    {
        void operator()(
            rive::gpu::FramebufferRenderTargetGL* renderTarget) const
        {
            if (renderTarget != nullptr)
            {
                renderTarget->unref();
            }
        }
    };

    const uint32_t m_width;
    const uint32_t m_height;
    std::unique_ptr<rive::gpu::FramebufferRenderTargetGL, RenderTargetUnref>
        m_renderTarget;
};

} // namespace rive_android

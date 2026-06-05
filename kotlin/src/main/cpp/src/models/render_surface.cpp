#include "models/render_surface.hpp"

#include "helpers/rive_log.hpp"
#include "models/render_context.hpp"

namespace rive_android
{

constexpr static auto* TAG_SURFACE = "RiveN/RenderSurface";

RenderSurface::RenderSurface(uint32_t requestedWidth,
                             uint32_t requestedHeight) :
    m_requestedWidth(requestedWidth), m_requestedHeight(requestedHeight)
{}

rive::gpu::RenderTarget* RenderSurface::getOrCreateRenderTarget(
    RenderContext* renderContext)
{
    if (m_renderTarget == nullptr)
    {
        if (m_requestedWidth == 0 || m_requestedHeight == 0)
        {
            RiveLogE(TAG_SURFACE,
                     "Cannot create render target with invalid requested "
                     "size: %u x %u",
                     m_requestedWidth,
                     m_requestedHeight);
            return nullptr;
        }
        m_renderTarget.reset(
            renderContext->createRenderTarget(this,
                                              m_requestedWidth,
                                              m_requestedHeight));
    }
    return m_renderTarget.get();
}

rive::gpu::RenderTarget* RenderSurface::renderTarget() const
{
    return m_renderTarget.get();
}

void RenderSurface::resize(uint32_t requestedWidth, uint32_t requestedHeight)
{
    if (m_requestedWidth == requestedWidth &&
        m_requestedHeight == requestedHeight)
    {
        return;
    }

    onResize();
    m_requestedWidth = requestedWidth;
    m_requestedHeight = requestedHeight;
    resetRenderTarget();
}

void RenderSurface::resetRenderTarget() { m_renderTarget.reset(); }

void RenderSurface::RenderTargetUnref::operator()(
    rive::gpu::RenderTarget* renderTarget) const
{
    if (renderTarget != nullptr)
    {
        renderTarget->unref();
    }
}

} // namespace rive_android

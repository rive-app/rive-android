#pragma once

#include <cstdint>
#include <memory>

#include "rive/renderer/render_target.hpp"

namespace rive_android
{

class RenderContext;

/**
 * Backend-specific surface wrapper that owns its lazy Rive render target.
 *
 * Kotlin stores concrete subclasses as opaque surface pointers. The concrete
 * render target is created on first draw through the active RenderContext, then
 * dropped and recreated after resize.
 */
class RenderSurface
{
public:
    RenderSurface(uint32_t requestedWidth, uint32_t requestedHeight);
    virtual ~RenderSurface() = default;

    RenderSurface(const RenderSurface&) = delete;
    RenderSurface& operator=(const RenderSurface&) = delete;

    /**
     * Returns the concrete render target, creating it if needed.
     *
     * @param renderContext Backend render context that will draw to the target.
     * @return The concrete Rive render target, or nullptr if creation fails.
     */
    rive::gpu::RenderTarget* getOrCreateRenderTarget(
        RenderContext* renderContext);

    /**
     * @return The current concrete render target, or nullptr before creation.
     */
    rive::gpu::RenderTarget* renderTarget() const;

    /**
     * Invalidates size-dependent surface resources for a new requested size.
     *
     * Must be called on the command server thread.
     *
     * @param requestedWidth New requested target width in pixels.
     * @param requestedHeight New requested target height in pixels.
     */
    void resize(uint32_t requestedWidth, uint32_t requestedHeight);

    /** Drops the concrete render target so it is recreated on the next draw. */
    void resetRenderTarget();

protected:
    /**
     * Backend hook called during resize before the concrete target is dropped.
     */
    virtual void onResize() {}

private:
    /** Adapts Core render target ref counting for unique_ptr-based RAII. */
    struct RenderTargetUnref
    {
        void operator()(rive::gpu::RenderTarget* renderTarget) const;
    };

    uint32_t m_requestedWidth;
    uint32_t m_requestedHeight;
    std::unique_ptr<rive::gpu::RenderTarget, RenderTargetUnref> m_renderTarget;
};

} // namespace rive_android

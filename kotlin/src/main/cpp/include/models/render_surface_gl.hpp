#pragma once

#include <EGL/egl.h>
#include <cstdint>

#include "models/render_surface.hpp"

namespace rive_android
{

/**
 * Native wrapper for an EGL surface and its lazy Rive render target.
 *
 * The EGLSurface itself is created and destroyed by Kotlin in this pass. This
 * wrapper borrows the handle so GL and Vulkan share the same draw contract.
 */
class RenderSurfaceGL : public RenderSurface
{
public:
    RenderSurfaceGL(EGLSurface surface,
                    uint32_t requestedWidth,
                    uint32_t requestedHeight);

    /** @return The borrowed EGL surface handle. */
    EGLSurface eglSurface() const;

private:
    EGLSurface m_surface;
};

} // namespace rive_android

#include "models/render_surface_gl.hpp"

namespace rive_android
{

RenderSurfaceGL::RenderSurfaceGL(EGLSurface surface,
                                 uint32_t requestedWidth,
                                 uint32_t requestedHeight) :
    RenderSurface(requestedWidth, requestedHeight), m_surface(surface)
{}

EGLSurface RenderSurfaceGL::eglSurface() const { return m_surface; }

} // namespace rive_android

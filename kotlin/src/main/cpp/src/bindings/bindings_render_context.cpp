#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/native_window_jni.h>
#include <jni.h>

#include "models/lazy_framebuffer_render_target_gl.hpp"
#include "models/render_context.hpp"

namespace rive_android
{

extern "C"
{
    JNIEXPORT jlong JNICALL
    Java_app_rive_core_RenderContextGL_cppConstructor(JNIEnv*,
                                                      jobject,
                                                      jlong display,
                                                      jlong context)
    {
        auto eglDisplay = reinterpret_cast<EGLDisplay>(display);
        auto eglContext = reinterpret_cast<EGLContext>(context);

        auto* contextGL = new RenderContextGL(eglDisplay, eglContext);
        return reinterpret_cast<jlong>(contextGL);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_RenderContextGL_cppDelete(JNIEnv*, jobject, jlong ref)
    {
        auto renderContextGl = reinterpret_cast<RenderContextGL*>(ref);
        delete renderContextGl;
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_core_RenderContextGL_cppCreateRiveRenderTarget(JNIEnv*,
                                                                 jobject,
                                                                 jint width,
                                                                 jint height)
    {
        return reinterpret_cast<jlong>(
            new LazyFramebufferRenderTargetGL(static_cast<uint32_t>(width),
                                              static_cast<uint32_t>(height)));
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_RenderContextGL_cppDeleteRiveRenderTarget(
        JNIEnv*,
        jobject,
        jlong renderTargetRef)
    {
        auto renderTarget =
            reinterpret_cast<LazyFramebufferRenderTargetGL*>(renderTargetRef);
        delete renderTarget;
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_RiveSurface_cppDeleteRenderTarget(JNIEnv*,
                                                         jobject,
                                                         jlong renderTargetRef)
    {
        auto renderTarget =
            reinterpret_cast<LazyFramebufferRenderTargetGL*>(renderTargetRef);
        delete renderTarget;
    }
}

} // namespace rive_android

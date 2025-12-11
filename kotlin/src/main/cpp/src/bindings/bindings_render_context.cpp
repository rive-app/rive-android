#include <GLES3/gl3.h>

#include "models/render_context.hpp"
#include "rive/renderer/gl/render_target_gl.hpp"

#include <android/native_window_jni.h>
#include <EGL/egl.h>
#include <jni.h>

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

    JNIEXPORT void JNICALL
    Java_app_rive_core_RiveSurface_cppDeleteRenderTarget(JNIEnv*,
                                                         jobject,
                                                         jlong renderTargetRef)
    {
        auto renderTarget =
            reinterpret_cast<rive::gpu::RenderTargetGL*>(renderTargetRef);
        renderTarget->unref();
    }
}

} // namespace rive_android

#include <EGL/egl.h>
#include <android/native_window_jni.h>
#include <jni.h>

#include "models/render_context.hpp"
#include "models/render_surface.hpp"
#include "models/render_surface_gl.hpp"

namespace rive_android
{

namespace
{
void throwRenderException(JNIEnv* env, const char* message)
{
    auto exceptionClass = env->FindClass("app/rive/RiveRenderException");
    env->ThrowNew(exceptionClass, message);
}

/**
 * Store surface JNI handles as RenderSurface pointers so later base-pointer
 * recovery does not depend on concrete class inheritance layout.
 */
template <typename SurfaceT> jlong surfaceToLong(SurfaceT* surface)
{
    return reinterpret_cast<jlong>(static_cast<RenderSurface*>(surface));
}
} // namespace

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
    Java_app_rive_core_RenderContextGL_cppCreateSurface(JNIEnv*,
                                                        jobject,
                                                        jlong eglSurface,
                                                        jint width,
                                                        jint height)
    {
        return surfaceToLong(
            new RenderSurfaceGL(reinterpret_cast<EGLSurface>(eglSurface),
                                static_cast<uint32_t>(width),
                                static_cast<uint32_t>(height)));
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueueJNIBridge_cppDeleteSurface(JNIEnv*,
                                                              jobject,
                                                          jlong surfaceRef)
    {
        auto surface = reinterpret_cast<RenderSurface*>(surfaceRef);
        delete surface;
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueueJNIBridge_cppResizeSurface(JNIEnv*,
                                                          jobject,
                                                    jlong surfaceRef,
                                                    jint width,
                                                    jint height)
    {
        auto surface = reinterpret_cast<RenderSurface*>(surfaceRef);
        surface->resize(static_cast<uint32_t>(width),
                        static_cast<uint32_t>(height));
    }

#ifdef RIVE_VULKAN
    JNIEXPORT jlong JNICALL
    Java_app_rive_core_RenderContextVulkan_cppConstructor(JNIEnv*, jobject)
    {
        auto* contextVulkan = new RenderContextVulkan();
        return reinterpret_cast<jlong>(contextVulkan);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_RenderContextVulkan_cppDelete(JNIEnv*,
                                                     jobject,
                                                     jlong ref)
    {
        auto* renderContextVulkan = reinterpret_cast<RenderContextVulkan*>(ref);
        delete renderContextVulkan;
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_core_RiveSurfaceVulkan_cppCreateSurface(JNIEnv* env,
                                                          jclass,
                                                          jlong ref,
                                                          jobject jSurface,
                                                          jint width,
                                                          jint height)
    {
        auto* renderContextVulkan = reinterpret_cast<RenderContextVulkan*>(ref);
        auto* nativeWindow = ANativeWindow_fromSurface(env, jSurface);
        if (nativeWindow == nullptr)
        {
            throwRenderException(env, "Unable to create ANativeWindow");
            return 0;
        }

        auto* surface =
            renderContextVulkan->createWindowSurface(nativeWindow,
                                                     static_cast<int>(width),
                                                     static_cast<int>(height));
        ANativeWindow_release(nativeWindow);
        if (surface == nullptr)
        {
            throwRenderException(env, "Unable to create Vulkan surface");
            return 0;
        }
        return surfaceToLong(surface);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_core_RiveSurfaceVulkanImage_cppCreateImageSurface(JNIEnv* env,
                                                                    jclass,
                                                                    jlong,
                                                                    jint width,
                                                                    jint height)
    {
        auto* surface =
            RenderContextVulkan::createImageSurface(static_cast<int>(width),
                                                    static_cast<int>(height));
        if (surface == nullptr)
        {
            throwRenderException(env, "Unable to create Vulkan image surface");
            return 0;
        }
        return surfaceToLong(surface);
    }

#endif
}

} // namespace rive_android

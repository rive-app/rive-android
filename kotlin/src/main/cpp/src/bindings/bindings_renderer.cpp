#include <jni.h>
#include <android/native_window_jni.h>

#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "helpers/worker_thread.hpp"

#include "models/jni_renderer.hpp"
#include "rive/layout.hpp"
#include "rive/artboard.hpp"

#ifdef __cplusplus
extern "C"
{
#endif

    using namespace rive_android;

    // Renderer
    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_renderers_Renderer_constructor(
        JNIEnv* env,
        jobject ktRenderer,
        jboolean trace,
        jint type)
    {
        RendererType rendererType = static_cast<RendererType>(type);
        JNIRenderer* renderer =
            new JNIRenderer(ktRenderer, trace, rendererType);
        // If using a fallback renderer, reassign this value.
        int actualType = static_cast<int>(renderer->rendererType());
        if (type != actualType)
        {
            jclass ktRendererClass = env->GetObjectClass(ktRenderer);
            jmethodID setRendererType =
                env->GetMethodID(ktRendererClass, "setRendererType", "(I)V");
            JNIExceptionHandler::CallVoidMethod(env,
                                                ktRenderer,
                                                setRendererType,
                                                actualType);
            env->DeleteLocalRef(ktRendererClass);
        }
        return (jlong)renderer;
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_renderers_Renderer_cppDelete(JNIEnv*,
                                                              jobject,
                                                              jlong rendererRef)
    {
        JNIRenderer* renderer = reinterpret_cast<JNIRenderer*>(rendererRef);
        // Capture the raw pointer, post to the worker thread, return immediately:
        renderer->disposeAsync();
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_renderers_Renderer_cppStop(JNIEnv*,
                                                            jobject,
                                                            jlong rendererRef)
    {
        reinterpret_cast<JNIRenderer*>(rendererRef)->stop();
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_renderers_Renderer_cppStart(JNIEnv*,
                                                             jobject,
                                                             jlong rendererRef)
    {
        reinterpret_cast<JNIRenderer*>(rendererRef)->start();
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_renderers_Renderer_cppDoFrame(
        JNIEnv*,
        jobject,
        jlong rendererRef)
    {
        reinterpret_cast<JNIRenderer*>(rendererRef)->doFrame();
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_renderers_Renderer_cppSetSurface(
        JNIEnv* env,
        jobject,
        jobject surface,
        jlong rendererRef)
    {
        JNIRenderer* renderer = reinterpret_cast<JNIRenderer*>(rendererRef);
        if (renderer->rendererType() != RendererType::Canvas)
        {
            ANativeWindow* surfaceWindow =
                ANativeWindow_fromSurface(env, surface);
            reinterpret_cast<JNIRenderer*>(rendererRef)
                ->setSurface(surfaceWindow);
            if (surfaceWindow)
            {
                // Release this handle.
                // If the renderer grabbed a reference it won't deallocate.
                ANativeWindow_release(surfaceWindow);
            }
        }
        else
        {
            renderer->setSurface(surface);
        }
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_renderers_Renderer_cppDestroySurface(
        JNIEnv*,
        jobject,
        jlong rendererRef)
    {
        reinterpret_cast<JNIRenderer*>(rendererRef)
            ->setSurface(std::monostate{});
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_renderers_Renderer_cppSave(JNIEnv*,
                                                            jobject,
                                                            jlong rendererRef)
    {
        reinterpret_cast<JNIRenderer*>(rendererRef)
            ->getRendererOnWorkerThread()
            ->save();
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_renderers_Renderer_cppRestore(
        JNIEnv*,
        jobject,
        jlong rendererRef)
    {
        reinterpret_cast<JNIRenderer*>(rendererRef)
            ->getRendererOnWorkerThread()
            ->restore();
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_renderers_Renderer_cppAlign(
        JNIEnv* env,
        jobject thisObj,
        jlong ref,
        jobject ktFit,
        jobject ktAlignment,
        jobject targetBoundsRectF,
        jobject sourceBoundsRectF,
        jfloat scaleFactor)
    {
        JNIRenderer* jniWrapper = reinterpret_cast<JNIRenderer*>(ref);
        rive::Fit fit = GetFit(env, ktFit);
        rive::Alignment alignment = GetAlignment(env, ktAlignment);
        rive::AABB targetBounds = RectFToAABB(env, targetBoundsRectF);
        rive::AABB sourceBounds = RectFToAABB(env, sourceBoundsRectF);
        jniWrapper->getRendererOnWorkerThread()->align(fit,
                                                       alignment,
                                                       targetBounds,
                                                       sourceBounds,
                                                       scaleFactor);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_renderers_Renderer_cppTransform(
        JNIEnv* env,
        jobject thisObj,
        jlong ref,
        jfloat x,
        jfloat sy,
        jfloat sx,
        jfloat y,
        jfloat tx,
        jfloat ty)
    {
        JNIRenderer* jniWrapper = reinterpret_cast<JNIRenderer*>(ref);
        jniWrapper->getRendererOnWorkerThread()->transform(
            rive::Mat2D(x, sy, sx, y, tx, ty));
    }

    JNIEXPORT jint JNICALL
    Java_app_rive_runtime_kotlin_renderers_Renderer_cppWidth(JNIEnv*,
                                                             jobject,
                                                             jlong rendererRef)
    {
        return reinterpret_cast<JNIRenderer*>(rendererRef)->width();
    }

    JNIEXPORT jint JNICALL
    Java_app_rive_runtime_kotlin_renderers_Renderer_cppHeight(JNIEnv*,
                                                              jobject,
                                                              jlong rendererRef)
    {
        return reinterpret_cast<JNIRenderer*>(rendererRef)->height();
    }

    JNIEXPORT jfloat JNICALL
    Java_app_rive_runtime_kotlin_renderers_Renderer_cppAvgFps(JNIEnv*,
                                                              jobject,
                                                              jlong rendererRef)
    {
        return reinterpret_cast<JNIRenderer*>(rendererRef)->averageFps();
    }

#ifdef __cplusplus
}
#endif

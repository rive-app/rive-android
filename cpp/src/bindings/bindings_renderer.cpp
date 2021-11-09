#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "models/jni_renderer.hpp"
#include "models/jni_renderer_gl.hpp"
#include "rive/layout.hpp"
#include <jni.h>

#ifdef __cplusplus
extern "C"
{
#endif

    using namespace rive_android;

    // RENDERER
    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_Renderer_constructor(
        JNIEnv *env,
        jobject thisObj,
        jboolean antialias)
    {
        auto renderer = new ::JNIRenderer();
        ::JNIRenderer::antialias = (bool)antialias;
        renderer->jRendererObject = getJNIEnv()->NewGlobalRef(thisObj);

        return (jlong)renderer;
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_Renderer_cleanupJNI(
        JNIEnv *env,
        jobject thisObj,
        jlong rendererRef)
    {
        ::JNIRenderer *renderer = (::JNIRenderer *)rendererRef;
        delete renderer;
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_Renderer_cppAlign(
        JNIEnv *env,
        jobject thisObj,
        jlong ref,
        jobject jfit,
        jobject jalignment,
        jlong targetBoundsRef,
        jlong sourceBoundsRef)
    {
        ::JNIRenderer *renderer = (::JNIRenderer *)ref;

        auto fit = ::getFit(env, jfit);
        auto alignment = ::getAlignment(env, jalignment);
        rive::AABB *targetBounds = (rive::AABB *)targetBoundsRef;
        rive::AABB *sourceBounds = (rive::AABB *)sourceBoundsRef;
        renderer->align(fit, alignment, *targetBounds, *sourceBounds);
    }

    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_RendererOpenGL_constructor(JNIEnv *env, jobject thisObj)
    {
        auto renderer = new ::JNIRendererGL();
        renderer->jRendererObject = getJNIEnv()->NewGlobalRef(thisObj);

        return (jlong)renderer;
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_RendererOpenGL_startFrame(
        JNIEnv *env,
        jobject thisObj,
        jlong rendererRef)
    {
        ::JNIRendererGL *renderer = (::JNIRendererGL *)rendererRef;
        renderer->initialize(nullptr);
        renderer->startFrame();
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_RendererOpenGL_setViewport(
        JNIEnv *env,
        jobject thisObj,
        jlong rendererRef,
        jint width,
        jint height)
    {
        ::JNIRendererGL *renderer = (::JNIRendererGL *)rendererRef;

        float projection[16] = {0.0f};
        renderer->orthographicProjection(
            projection, 0.0f, width, height, 0.0f, 0.0f, 1.0f);
        renderer->modelViewProjection(projection);
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_RendererOpenGL_cleanupJNI(
        JNIEnv *env,
        jobject thisObj,
        jlong rendererRef)
    {
        ::JNIRendererGL *renderer = (::JNIRendererGL *)rendererRef;
        delete renderer;
    }
#ifdef __cplusplus
}
#endif

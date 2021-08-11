#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "models/jni_renderer.hpp"
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

#ifdef __cplusplus
}
#endif

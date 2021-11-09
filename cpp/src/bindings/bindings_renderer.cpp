#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "models/jni_renderer.hpp"
#include "models/jni_renderer_gl.hpp"
#include "models/jni_renderer_skia.hpp"
#include "rive/layout.hpp"
#include <jni.h>
#include <errno.h>
#include <stdio.h>
#include <unistd.h>
#include <thread>
#include <cassert>

#ifdef __cplusplus
extern "C"
{
#endif

    using namespace rive_android;

    // Our renderer abstractions creates paints and paths for the renderer,
    // however the abstraction model is implemented with a simple global
    // function for makeRenderPaint and makeRenderPath. See the rive-cpp
    // low_level_rendering branch for how the viewer handles this by expecting
    // one global LowLevelRenderer and then virtualizing the methods to create
    // those objects.
    //
    // A long term cleaner solution is requiring file graphics-init by passing
    // the renderer reference. Perhaps the time has come to explore that to
    // avoid things like this. It does mean that files cannot be loaded without
    // a renderer, or at least will require initialization with a no-op renderer
    // (probably ok?).
    JNIRendererGL *g_GLRenderer = nullptr;

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

    // luigi: this redirects stderr to android log (probably want to ifdef this out for release)
    void logThread()
    {
        int pipes[2];
        pipe(pipes);
        dup2(pipes[1], STDERR_FILENO);
        FILE *inputFile = fdopen(pipes[0], "r");
        char readBuffer[256];
        while (1)
        {
            fgets(readBuffer, sizeof(readBuffer), inputFile);
            __android_log_write(2, "stderr", readBuffer);
        }
    }

    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_RendererOpenGL_constructor(JNIEnv *env, jobject thisObj)
    {
        // luigi: again ifdef this out for release (or murder completely, but
        // it's nice to catch all fprintf to stderr). Bad place to put this but
        // for now we instance the renderer once and I just wanted to throw this
        // in somewhere. Basically needs to be called once at boot to spawn a
        // thread that listens to stderr and redirects to android log.
        std::thread t(logThread);
        // detach so it outlives the ref
        t.detach();

        auto renderer = new ::JNIRendererGL();
        g_GLRenderer = renderer;
        renderer->jRendererObject = getJNIEnv()->NewGlobalRef(thisObj);
        return (jlong)renderer;
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_RendererOpenGL_initializeGL(
        JNIEnv *env,
        jobject thisObj,
        jlong rendererRef)
    {
        ::JNIRendererGL *renderer = (::JNIRendererGL *)rendererRef;
        renderer->initialize(nullptr);
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_RendererOpenGL_startFrame(
        JNIEnv *env,
        jobject thisObj,
        jlong rendererRef)
    {
        ::JNIRendererGL *renderer = (::JNIRendererGL *)rendererRef;
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
        // We should probably pass width/height directly to drawArtboard so you
        // can target different areas at draw time.
        renderer->width = width;
        renderer->height = height;
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

    // Skia Renderer
    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_RendererSkia_constructor(JNIEnv *env, jobject thisObj)
    {
        // luigi: again ifdef this out for release (or murder completely, but
        // it's nice to catch all fprintf to stderr). Bad place to put this but
        // for now we instance the renderer once and I just wanted to throw this
        // in somewhere. Basically needs to be called once at boot to spawn a
        // thread that listens to stderr and redirects to android log.
        std::thread t(logThread);
        // detach so it outlives the ref
        t.detach();

        auto renderer = new ::JNIRendererSkia();
        renderer->jRendererObject = getJNIEnv()->NewGlobalRef(thisObj);
        return (jlong)renderer;
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_RendererSkia_initializeSkiaGL(
        JNIEnv *env,
        jobject thisObj,
        jlong rendererRef)
    {
        ((::JNIRendererSkia *)rendererRef)->initialize();
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_RendererOpenSkia_startFrame(
        JNIEnv *env,
        jobject thisObj,
        jlong rendererRef)
    {
        ((::JNIRendererSkia *)rendererRef)->startFrame();
    }

#ifdef __cplusplus
}

namespace rive
{
    RenderPaint *makeRenderPaint()
    {
        assert(g_GLRenderer != nullptr);
        return g_GLRenderer->makeRenderPaint();
    }

    RenderPath *makeRenderPath()
    {
        assert(g_GLRenderer != nullptr);
        return g_GLRenderer->makeRenderPath();
    }
} // namespace rive
#endif

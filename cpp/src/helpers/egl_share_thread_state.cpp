#include <thread>

#include "helpers/egl_share_thread_state.hpp"

using namespace std::chrono_literals;

namespace rive_android
{
EGLShareThreadState::EGLShareThreadState() {}

EGLShareThreadState::~EGLShareThreadState() { LOGD("EGLThreadState getting destroyed! 🧨"); }

void EGLShareThreadState::destroySurface(EGLSurface eglSurface)
{
    if (eglSurface == EGL_NO_SURFACE)
    {
        return;
    }

    mSkiaContextManager.makeCurrent(EGL_NO_SURFACE);
    eglDestroySurface(mSkiaContextManager.getDisplay(), eglSurface);
    EGL_ERR_CHECK();
}

void EGLShareThreadState::swapBuffers(EGLSurface eglSurface) const
{
    auto display = mSkiaContextManager.getDisplay();
    if (display == EGL_NO_DISPLAY || eglSurface == EGL_NO_SURFACE)
    {
        LOGE("Swapping buffers without a display/surface");
        return;
    }
    // Context mutex has been locked from doDraw()
    eglSwapBuffers(display, eglSurface);
    EGL_ERR_CHECK();
}

EGLSurface EGLShareThreadState::createEGLSurface(ANativeWindow* window)
{
    if (!window)
    {
        return EGL_NO_SURFACE;
    }
    LOGD("mSkiaContextManager.createWindowSurface()");
    return mSkiaContextManager.createWindowSurface(window);
}

sk_sp<SkSurface> EGLShareThreadState::createSkiaSurface(EGLSurface eglSurface,
                                                        int width,
                                                        int height)
{
    // Width/Height getters return negative values on error.
    // Probably a race condition with surfaces being reclaimed by the OS before
    // this function completes.
    if (width < 0 || height < 0)
    {
        LOGE("Window is unavailable.");
        return nullptr;
    }

    mSkiaContextManager.makeCurrent(eglSurface);
    LOGI("Set up window surface %dx%d", width, height);
    return mSkiaContextManager.createSkiaSurface(width, height);
}

void EGLShareThreadState::doDraw(ITracer* tracer,
                                 EGLSurface eglSurface,
                                 SkSurface* skSurface,
                                 jobject ktRenderer) const
{
    // Don't render if we have no surface
    if (eglSurface == nullptr || skSurface == nullptr)
    {
        LOGE("Has No Surface!");
        // Sleep a bit so we don't churn too fast
        std::this_thread::sleep_for(50ms);
        return;
    }

    tracer->beginSection("draw()");
    // Lock context access for this thread.

    // Bind context to this thread.
    mSkiaContextManager.makeCurrent(eglSurface);
    skSurface->getCanvas()->clear(SkColor((0x00000000)));
    auto env = getJNIEnv();
    // Kotlin callback.
    env->CallVoidMethod(ktRenderer, mKtDrawCallback);

    tracer->beginSection("flush()");
    skSurface->flushAndSubmit();
    tracer->endSection(); // flush

    tracer->beginSection("swapBuffers()");
    swapBuffers(eglSurface);
    // Unbind context.
    mSkiaContextManager.makeCurrent();

    tracer->endSection(); // swapBuffers
    tracer->endSection(); // draw()
}
} // namespace rive_android

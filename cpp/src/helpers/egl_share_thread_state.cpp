#include <thread>

#include "helpers/egl_share_thread_state.hpp"

using namespace std::chrono_literals;

namespace rive_android
{
EGLShareThreadState::EGLShareThreadState() : mSkiaContextManager(SkiaContextManager::getInstance())
{}

EGLShareThreadState::~EGLShareThreadState()
{
    LOGD("EGLThreadState getting destroyed! 🧨");
    clearSurface();
}

void EGLShareThreadState::flush() const
{
    if (!mSkSurface)
    {
        LOGE("Cannot flush() without a surface.");
        return;
    }
    mSkSurface->flushAndSubmit();
}

void EGLShareThreadState::clearSurface()
{
    if (mSurface == EGL_NO_SURFACE)
    {
        return;
    }

    std::lock_guard<std::mutex> guard(mSkiaContextManager->eglCtxMutex);
    mSkiaContextManager->makeCurrent(EGL_NO_SURFACE);
    auto srf = mSurface;
    mSurface = EGL_NO_SURFACE;
    eglDestroySurface(mSkiaContextManager->getDisplay(), srf);
    EGL_ERR_CHECK();
    mSkSurface = nullptr;
}

void EGLShareThreadState::swapBuffers() const
{
    auto display = mSkiaContextManager->getDisplay();
    if (display == EGL_NO_DISPLAY || mSurface == EGL_NO_SURFACE)
    {
        LOGE("Swapping buffers without a display/surface");
        return;
    }
    // Context mutex has been locked from doDraw()
    eglSwapBuffers(display, mSurface);
    EGL_ERR_CHECK();
}

bool EGLShareThreadState::setWindow(ANativeWindow* window)
{
    clearSurface();
    if (!window)
    {
        return false;
    }
    std::lock_guard<std::mutex> guard(mSkiaContextManager->eglCtxMutex);

    LOGD("Making mSurface?!");
    mSurface = mSkiaContextManager->getWindowSurface(window);
    mSkiaContextManager->makeCurrent(mSurface);

    ANativeWindow_release(window);

    auto width = ANativeWindow_getWidth(window);
    auto height = ANativeWindow_getHeight(window);

    // Width/Height getters return negative values on error.
    // Probably a race condition with surfaces being reclaimed by the OS before
    // this function completes.
    if (width < 0 || height < 0)
    {
        LOGE("Window is unavailable.");
        return false;
    }

    LOGI("Set up window surface %dx%d", width, height);

    mSkSurface = mSkiaContextManager->createSkiaSurface(width, height);
    LOGD("I finally got my surface!");
    mSkiaContextManager->makeCurrent();

    if (!mSkSurface)
    {
        LOGE("Unable to create a SkSurface??");
        mSurface = EGL_NO_SURFACE;
        return false;
    }

    return true;
}

void EGLShareThreadState::doDraw(ITracer* tracer, SkCanvas* canvas, jobject ktRenderer) const
{
    // Don't render if we have no surface
    if (hasNoSurface())
    {
        LOGE("Has No Surface!");
        // Sleep a bit so we don't churn too fast
        std::this_thread::sleep_for(50ms);
        return;
    }

    tracer->beginSection("draw()");
    // Lock context access for this thread.

    std::lock_guard<std::mutex> guard(mSkiaContextManager->eglCtxMutex);
    // Bind context to this thread.
    mSkiaContextManager->makeCurrent(mSurface);
    canvas->clear(SkColor((0x00000000)));
    auto env = getJNIEnv();
    // Kotlin callback.
    env->CallVoidMethod(ktRenderer, mKtDrawCallback);

    tracer->beginSection("flush()");
    flush();
    tracer->endSection(); // flush

    tracer->beginSection("swapBuffers()");
    swapBuffers();
    // Unbind context.
    mSkiaContextManager->makeCurrent();

    tracer->endSection(); // swapBuffers
    tracer->endSection(); // draw()
}
} // namespace rive_android
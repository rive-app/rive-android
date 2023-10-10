#ifndef RIVE_ANDROID_EGL_WORKER_HPP
#define RIVE_ANDROID_EGL_WORKER_HPP

#include "helpers/thread_state_egl.hpp"
#include "helpers/worker_thread.hpp"
#include "rive/refcnt.hpp"

namespace rive_android
{
class EGLWorker : public WorkerThread
{
public:
    // Returns the current worker of the requested renderer type, or the current Skia worker if the
    // requested type is not supported.
    static rive::rcp<EGLWorker> CurrentOrSkia(RendererType);

    // Returns the Rive renderer worker, or null if it is not supported.
    static rive::rcp<EGLWorker> RiveWorker();

    // Returns the current Skia renderer worker.
    static rive::rcp<EGLWorker> SkiaWorker();

    ~EGLWorker();

    // These methods work with rive::rcp<> for tracking _external_ references. They don't
    // necessarily delete this object when the external ref count goes to zero, and they may have
    // other side effects as well.
    void ref();
    void unref();

private:
    EGLWorker(const RendererType rendererType) :
        WorkerThread("EGLWorker", Affinity::None, rendererType)
    {}

    void externalRefCountDidReachZero();

    size_t m_externalRefCount = 0;
};
} // namespace rive_android

#endif // RIVE_ANDROID_EGL_WORKER_HPP

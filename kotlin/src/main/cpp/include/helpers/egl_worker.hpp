#ifndef RIVE_ANDROID_EGL_WORKER_HPP
#define RIVE_ANDROID_EGL_WORKER_HPP

#include "helpers/thread_state_egl.hpp"
#include "helpers/worker_thread.hpp"
#include "rive/refcnt.hpp"

namespace rive_android
{
class EGLWorker : public WorkerThread, public rive::RefCnt<EGLWorker>
{
public:
    static rive::rcp<EGLWorker> Current(const RendererType);

private:
    friend class rive::RefCnt<EGLWorker>;

    EGLWorker(const RendererType rendererType) :
        WorkerThread("EGLWorker", Affinity::None, rendererType)
    {}
    ~EGLWorker();
};
} // namespace rive_android

#endif // RIVE_ANDROID_EGL_WORKER_HPP
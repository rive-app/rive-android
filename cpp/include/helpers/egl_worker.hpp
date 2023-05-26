#pragma once

#include "helpers/egl_share_thread_state.hpp"
#include "helpers/worker_thread.hpp"
#include "rive/refcnt.hpp"

namespace rive_android
{
class EGLWorker : public WorkerThread<EGLShareThreadState>, public rive::RefCnt<EGLWorker>
{
public:
    static rive::rcp<EGLWorker> Current();

private:
    friend class rive::RefCnt<EGLWorker>;

    EGLWorker() : WorkerThread("EGLWorker", Affinity::None) {}
    ~EGLWorker();
};
} // namespace rive_android

#include "helpers/thread_state_skia.hpp"
#include "helpers/thread_state_pls.hpp"
#include "helpers/worker_thread.hpp"

namespace rive_android
{
std::unique_ptr<EGLThreadState> WorkerThread::MakeThreadState(const RendererType type)
{
    std::unique_ptr<EGLThreadState> threadState;
    if (type == RendererType::Skia)
    {
        threadState = std::make_unique<SkiaThreadState>();
    }
    else
    {
        threadState = std::make_unique<PLSThreadState>();
    }

    return threadState;
}
} // namespace rive_android

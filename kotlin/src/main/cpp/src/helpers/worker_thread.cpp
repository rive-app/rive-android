#include "helpers/worker_thread.hpp"

#include "helpers/thread_state_pls.hpp"

namespace rive_android
{
std::unique_ptr<DrawableThreadState> WorkerThread::MakeThreadState(
    const RendererType type)
{
    switch (type)
    {
        case RendererType::Canvas:
            RiveLogD(WorkerThread::TAG,
                     "Creating Canvas Renderer thread state");
            return std::make_unique<CanvasThreadState>();
        default:
        case RendererType::Rive:
            RiveLogD(WorkerThread::TAG, "Creating Rive Renderer thread state");
            return std::make_unique<PLSThreadState>();
    }
}
} // namespace rive_android

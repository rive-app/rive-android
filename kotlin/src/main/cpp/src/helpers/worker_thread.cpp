#include "helpers/thread_state_pls.hpp"
#include "helpers/worker_thread.hpp"

namespace rive_android
{
constexpr auto* TAG = "RiveLN/WorkerThread";

std::unique_ptr<DrawableThreadState> WorkerThread::MakeThreadState(
    const RendererType type)
{
    switch (type)
    {
        case RendererType::Canvas:
            RiveLogD(TAG, "Creating Canvas Renderer thread state");
            return std::make_unique<CanvasThreadState>();
        default:
        case RendererType::Rive:
            RiveLogD(TAG, "Creating Rive Renderer thread state");
            return std::make_unique<PLSThreadState>();
    }
}
} // namespace rive_android

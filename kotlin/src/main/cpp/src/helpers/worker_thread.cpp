#include "helpers/thread_state_skia.hpp"
#include "helpers/thread_state_pls.hpp"
#include "helpers/worker_thread.hpp"

namespace rive_android
{
std::unique_ptr<DrawableThreadState> WorkerThread::MakeThreadState(const RendererType type)
{
    switch (type)
    {
        case RendererType::Skia:
            return std::make_unique<SkiaThreadState>();
        case RendererType::Canvas:
            return std::make_unique<CanvasThreadState>();
        default:
        case RendererType::Rive:
            return std::make_unique<PLSThreadState>();
    }
}
} // namespace rive_android

#include "helpers/worker_ref.hpp"

#include "helpers/general.hpp"
#include "helpers/rive_log.hpp"
#include "helpers/thread_state_pls.hpp"

#include <thread>

using namespace rive;

namespace rive_android
{
static std::mutex s_refWorkerMutex;

static std::unique_ptr<RefWorker> s_canvasWorker;

const auto TAG = "RiveN/RefWorker";

rcp<RefWorker> RefWorker::RiveWorker()
{
    static enum class RiveRendererSupport { unknown, no, yes } s_isSupported;
    static std::unique_ptr<RefWorker> s_riveWorker;

    std::lock_guard lock(s_refWorkerMutex);

    if (s_isSupported == RiveRendererSupport::unknown)
    {
        assert(s_riveWorker == nullptr);
        RiveLogI(TAG, "Creating *Rive* RefWorker");

        std::unique_ptr<RefWorker> candidateWorker(
            new RefWorker(RendererType::Rive));
        // Check if PLS is supported.
        candidateWorker->runAndWait(
            [](rive_android::DrawableThreadState* threadState) {
                auto* plsThreadState =
                    static_cast<PLSThreadState*>(threadState);
                s_isSupported = plsThreadState->renderContext() != nullptr
                                    ? RiveRendererSupport::yes
                                    : RiveRendererSupport::no;
            });
        assert(s_isSupported != RiveRendererSupport::unknown);
        if (s_isSupported == RiveRendererSupport::yes)
        {
            // The Rive renderer is supported!
            s_riveWorker = std::move(candidateWorker);
        }
        else
        {
            RiveLogI(
                TAG,
                "Rive renderer is not supported. Falling back to Canvas renderer.");
        }
    }

    if (s_riveWorker != nullptr)
    {
        ++s_riveWorker->m_externalRefCount; // Increment the external ref count.
    }
    return rcp(s_riveWorker.get());
}

rcp<RefWorker> RefWorker::CanvasWorker()
{
    std::lock_guard lock(s_refWorkerMutex);
    if (s_canvasWorker == nullptr)
    {
        RiveLogI(TAG, "Creating *Canvas* RefWorker");
        s_canvasWorker =
            std::unique_ptr<RefWorker>(new RefWorker(RendererType::Canvas));
    }
    ++s_canvasWorker->m_externalRefCount; // Increment the external ref count.
    return rcp(s_canvasWorker.get());
}

rcp<RefWorker> RefWorker::CurrentOrFallback(RendererType rendererType)
{
    // N.B. if fallback changes, `GetFactory()` also needs to change.
    rcp<RefWorker> currentOrFallback;
    switch (rendererType)
    {
        case RendererType::None:
            assert(false);
            break;
        case RendererType::Rive:
            currentOrFallback = RiveWorker();
            break;
        case RendererType::Canvas:
            currentOrFallback = CanvasWorker();
            break;
    }
    // If we specify RendererType::Rive above, RefWorker::RiveWorker() may not
    // initialize the global static Rive worker if `s_isSupported` is not true,
    // i.e. if the render context failed to build and is uninitialized. In this
    // case, fall back to the canvas worker.
    if (currentOrFallback == nullptr)
    {
        RiveLogE(TAG, "Falling back to Canvas worker");
        currentOrFallback = CanvasWorker();
    }
    return currentOrFallback;
}

RefWorker::~RefWorker()
{
    RiveLogI(TAG,
             "Deleting the RefWorker with %s",
             RendererName(rendererType()));
    terminateThread();
}

void RefWorker::ref()
{
    std::lock_guard lock(s_refWorkerMutex);
    ++m_externalRefCount;
}

void RefWorker::unref()
{
    std::lock_guard lock(s_refWorkerMutex);
    assert(m_externalRefCount > 0);
    if (--m_externalRefCount == 0)
    {
        externalRefCountDidReachZero();
    }
}

void RefWorker::externalRefCountDidReachZero()
{
    switch (rendererType())
    {
        case RendererType::None:
        {
            assert(false);
            break;
        }
        case RendererType::Rive:
        {
            // Release the Rive worker's GPU resources, but keep the GL context
            // alive. We have simple way to release GPU resources here instead,
            // without having to pay the hefty price of destroying and
            // re-creating the entire GL context.
            run([](rive_android::DrawableThreadState* threadState) {
                auto* plsThreadState =
                    static_cast<PLSThreadState*>(threadState);
                rive::gpu::RenderContext* renderContext =
                    plsThreadState->renderContext();
                if (renderContext != nullptr)
                {
                    RiveLogI(TAG, "Releasing resources on the Rive renderer");
                    renderContext->releaseResources();
                }
            });
            break;
        }
        case RendererType::Canvas:
        {
            // Delete the entire Canvas context.
            assert(s_canvasWorker.get() == this);
            auto workerToDestroy =
                s_canvasWorker.release(); // Transfer ownership
            std::thread([workerToDestroy]() {
                // This 'delete' will call ~RefWorker -> ~WorkerThread ->
                // terminateThread(). Because this is a separate thread,
                // terminateThread() will call mThread.join() which will block
                // safely until the worker is finished.
                delete workerToDestroy;
            }).detach();
            break;
        }
    }
}
} // namespace rive_android

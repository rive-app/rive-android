#include "helpers/worker_ref.hpp"
#include "helpers/general.hpp"
#include "helpers/thread_state_pls.hpp"
#include <thread>

using namespace rive;

namespace rive_android
{
static std::mutex s_refWorkerMutex;

static std::unique_ptr<RefWorker> s_canvasWorker;

rcp<RefWorker> RefWorker::RiveWorker()
{
    static enum class RiveRendererSupport { unknown, no, yes } s_isSupported;
    static std::unique_ptr<RefWorker> s_riveWorker;

    std::lock_guard lock(s_refWorkerMutex);

    if (s_isSupported == RiveRendererSupport::unknown)
    {
        assert(s_riveWorker == nullptr);
        LOGI("Creating *Rive* RefWorker");
        std::unique_ptr<RefWorker> candidateWorker(
            new RefWorker(RendererType::Rive));
        // Check if PLS is supported.
        candidateWorker->runAndWait(
            [](rive_android::DrawableThreadState* threadState) {
                PLSThreadState* plsThreadState =
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
            LOGI("Rive renderer is not supported. Falling back on Canvas.");
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
        LOGI("Creating *Canvas* RefWorker");
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
    if (currentOrFallback == nullptr)
    {
        currentOrFallback = CanvasWorker();
    }
    return currentOrFallback;
}

RefWorker::~RefWorker()
{
    LOGI("Deleting the RefWorker with %s", RendererName(rendererType()));
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
                PLSThreadState* plsThreadState =
                    static_cast<PLSThreadState*>(threadState);
                rive::gpu::RenderContext* renderContext =
                    plsThreadState->renderContext();
                if (renderContext != nullptr)
                {
                    LOGI("Releasing resources on the Rive renderer");
                    renderContext->releaseResources();
                }
            });
            break;
        }
        case RendererType::Canvas:
        {
            // Delete the entire Canvas context.
            assert(s_canvasWorker.get() == this);
            s_canvasWorker = nullptr;
            break;
        }
    }
}
} // namespace rive_android

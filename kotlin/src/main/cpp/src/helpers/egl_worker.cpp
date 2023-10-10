#include "helpers/egl_worker.hpp"

#include "helpers/general.hpp"
#include "helpers/thread_state_pls.hpp"
#include <thread>

using namespace rive;

namespace rive_android
{
static std::mutex s_eglWorkerMutex;

rcp<EGLWorker> EGLWorker::RiveWorker()
{
    static enum class RiveRendererSupport { unknown, no, yes } s_isSupported;
    static std::unique_ptr<EGLWorker> s_riveWorker;

    std::lock_guard lock(s_eglWorkerMutex);

    if (s_isSupported == RiveRendererSupport::unknown)
    {
        assert(s_riveWorker == nullptr);
        LOGI("Creating a new EGLWorker with Rive");
        std::unique_ptr<EGLWorker> candidateWorker(new EGLWorker(RendererType::Rive));
        // Check if PLS is supported.
        candidateWorker->runAndWait([](rive_android::EGLThreadState* threadState) {
            PLSThreadState* plsThreadState = static_cast<PLSThreadState*>(threadState);
            s_isSupported = plsThreadState->plsContext() != nullptr ? RiveRendererSupport::yes
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
            LOGI("Rive renderer is not supported. Falling back on Skia.");
        }
    }

    if (s_riveWorker != nullptr)
    {
        ++s_riveWorker->m_externalRefCount; // Increment the external ref count.
    }
    return rcp(s_riveWorker.get());
}

static std::unique_ptr<EGLWorker> s_skiaWorker;

rcp<EGLWorker> EGLWorker::SkiaWorker()
{
    std::lock_guard lock(s_eglWorkerMutex);
    if (s_skiaWorker == nullptr)
    {
        LOGI("Creating a new EGLWorker with Skia");
        s_skiaWorker = std::unique_ptr<EGLWorker>(new EGLWorker(RendererType::Skia));
    }
    ++s_skiaWorker->m_externalRefCount; // Increment the external ref count.
    return rcp(s_skiaWorker.get());
}

rcp<EGLWorker> EGLWorker::CurrentOrSkia(RendererType rendererType)
{
    rcp<EGLWorker> currentOrSkia;
    switch (rendererType)
    {
        case RendererType::Rive:
            currentOrSkia = RiveWorker();
            break;
        case RendererType::Skia:
            currentOrSkia = SkiaWorker();
            break;
    }
    if (currentOrSkia == nullptr)
    {
        currentOrSkia = SkiaWorker();
    }
    return currentOrSkia;
}

static const char* renderer_name(RendererType rendererType)
{
    switch (rendererType)
    {
        case RendererType::Rive:
            return "Rive";
        case RendererType::Skia:
            return "Skia";
    }
}

EGLWorker::~EGLWorker()
{
    LOGI("Deleting the EGLWorker with %s", renderer_name(rendererType()));
    terminateThread();
}

void EGLWorker::ref()
{
    std::lock_guard lock(s_eglWorkerMutex);
    ++m_externalRefCount;
}

void EGLWorker::unref()
{
    std::lock_guard lock(s_eglWorkerMutex);
    assert(m_externalRefCount > 0);
    if (--m_externalRefCount == 0)
    {
        externalRefCountDidReachZero();
    }
}

void EGLWorker::externalRefCountDidReachZero()
{
    switch (rendererType())
    {
        case RendererType::Rive:
            // Release the Rive worker's GPU resources, but keep the GL context alive. We have
            // simple way to release GPU resources here instead, without having to pay the hefty
            // price of destroying and re-creating the entire GL context.
            run([](rive_android::EGLThreadState* threadState) {
                PLSThreadState* plsThreadState = static_cast<PLSThreadState*>(threadState);
                rive::pls::PLSRenderContext* plsContext = plsThreadState->plsContext();
                if (plsContext != nullptr)
                {
                    LOGI("Releasing GPU resources on the Rive renderer");
                    plsContext->resetGPUResources();
                }
            });
            break;
        case RendererType::Skia:
        {
            // Delete the entire Skia context.
            assert(s_skiaWorker.get() == this);
            s_skiaWorker = nullptr;
            break;
        }
    }
}
} // namespace rive_android

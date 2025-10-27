#pragma once

#include "helpers/thread_state_egl.hpp"
#include "helpers/worker_thread.hpp"
#include "rive/refcnt.hpp"

namespace rive_android
{
class RefWorker : public WorkerThread
{
public:
    // Returns the current worker of the requested renderer type, or the current
    // Canvas worker if the requested type is not supported.
    static rive::rcp<RefWorker> CurrentOrFallback(RendererType);

    // Returns the Rive renderer worker, or null if it is not supported.
    static rive::rcp<RefWorker> RiveWorker();

    // Returns the current Canvas renderer worker.
    static rive::rcp<RefWorker> CanvasWorker();

    ~RefWorker() override;

    // These methods work with rive::rcp<> for tracking _external_ references.
    // They don't necessarily delete this object when the external ref count
    // goes to zero, and they may have other side effects as well.
    void ref();
    void unref();

    static const char* RendererName(RendererType rendererType)
    {
        switch (rendererType)
        {
            case RendererType::None:
                assert(false);
                return "";
            case RendererType::Canvas:
                return "Canvas";
            case RendererType::Rive:
                return "Rive";
        }
    }

private:
    explicit RefWorker(const RendererType rendererType) :
        WorkerThread(RendererName(rendererType), Affinity::None, rendererType)
    {}

    void externalRefCountDidReachZero();

    size_t m_externalRefCount = 0;
};
} // namespace rive_android

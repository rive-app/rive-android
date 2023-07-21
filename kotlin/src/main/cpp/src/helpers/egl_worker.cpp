#include "helpers/egl_worker.hpp"

#include "helpers/general.hpp"
#include <thread>

using namespace rive;

namespace rive_android
{
static std::mutex s_eglWorkerMutex;
static EGLWorker* s_currentWorkers[2] = {nullptr, nullptr};

rcp<EGLWorker> EGLWorker::Current(const RendererType rendererType)
{
    std::lock_guard lock(s_eglWorkerMutex);
    int workerIdx = static_cast<int>(rendererType);
    if (s_currentWorkers[workerIdx] == nullptr)
    {
        LOGI("Created a new EGLWorker with type %s",
             rendererType == RendererType::Skia ? "Skia" : "Rive");
        s_currentWorkers[workerIdx] = new EGLWorker(rendererType);
    }
    else
    {
        LOGI("Referenced an existing EGLWorker.");
        s_currentWorkers[workerIdx]->ref();
    }
    return rcp(s_currentWorkers[workerIdx]);
}

EGLWorker::~EGLWorker()
{
    std::lock_guard lock(s_eglWorkerMutex);
    LOGI("Deleting the current %s EGLWorker.",
         m_RendererType == RendererType::Skia ? "Skia" : "Rive");
    int workerIdx = static_cast<int>(m_RendererType);
    assert(s_currentWorkers[workerIdx] == this);
    terminateThread();
    s_currentWorkers[workerIdx] = nullptr;
}
} // namespace rive_android

#include "helpers/egl_worker.hpp"

#include "helpers/general.hpp"
#include <thread>

using namespace rive;

namespace rive_android
{
static std::mutex s_eglWorkerMutex;
static EGLWorker* s_currentEGLWorker = nullptr;

rcp<EGLWorker> EGLWorker::Current()
{
    std::lock_guard lock(s_eglWorkerMutex);
    if (s_currentEGLWorker == nullptr)
    {
        LOGI("Created a new EGLWorker.");
        s_currentEGLWorker = new EGLWorker;
    }
    else
    {
        LOGI("Referenced an existing EGLWorker.");
        s_currentEGLWorker->ref();
    }
    return rcp(s_currentEGLWorker);
}

EGLWorker::~EGLWorker()
{
    std::lock_guard lock(s_eglWorkerMutex);
    LOGI("Deleting the current EGLWorker.");
    assert(s_currentEGLWorker == this);
    terminateThread();
    s_currentEGLWorker = nullptr;
}
} // namespace rive_android

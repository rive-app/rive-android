#include "helpers/worker_thread.hpp"

namespace rive_android
{
// Instantiate static objects.
std::weak_ptr<ThreadManager> ThreadManager::mInstance;
std::mutex ThreadManager::mMutex;

std::shared_ptr<ThreadManager> ThreadManager::GetInstance()
{
    std::lock_guard<std::mutex> lock(mMutex);
    std::shared_ptr<ThreadManager> sharedInstance = mInstance.lock();
    if (!sharedInstance)
    {
        LOGD("📦 Creating ThreadManager");
        sharedInstance.reset(new ThreadManager, [](ThreadManager* p) { delete p; });
        mInstance = sharedInstance;
    }
    else
    {
        LOGD("🫱 ThreadManager Instance (now %ld)", mInstance.use_count());
    }

    return sharedInstance;
}

WorkerThread<EGLShareThreadState>* ThreadManager::acquireWorker(const char* name)
{
    std::lock_guard<std::mutex> threadLock(mMutex);

    WorkerThread<EGLShareThreadState>* worker = nullptr;
    if (mWorkers.empty())
    {
        worker = new WorkerThread<EGLShareThreadState>(name, Affinity::Odd);
    }
    else
    {
        worker = mWorkers.top();
        mWorkers.pop();
        worker->launchThread();
    }

    worker->setIsWorking(true);

    return worker;
}

void ThreadManager::releaseThread(WorkerThread<EGLShareThreadState>* worker,
                                  std::function<void()> onRelease)
{
    std::lock_guard<std::mutex> threadLock(mMutex);
    // Thread state needs to release its resources also.
    worker->setIsWorking(false);
    worker->releaseQueue(std::move(onRelease));
}

void ThreadManager::putBack(WorkerThread<EGLShareThreadState>* worker)
{
    std::lock_guard<std::mutex> threadLock(mMutex);
    mWorkers.push(worker);
}
} // namespace rive_android
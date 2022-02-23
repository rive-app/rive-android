#include "helpers/worker_thread.hpp"

namespace rive_android
{
	// Instantiate static objects.
	ThreadManager* ThreadManager::mInstance{nullptr};
	std::mutex ThreadManager::mMutex;

	ThreadManager* ThreadManager::getInstance()
	{
		std::lock_guard<std::mutex> lock(mMutex);
		if (mInstance == nullptr)
		{
			mInstance = new ThreadManager();
		}
		return mInstance;
	}

	WorkerThread<EGLThreadState>* ThreadManager::acquireThread(const char* name)
	{
		std::lock_guard<std::mutex> threadLock(mMutex);

		WorkerThread<EGLThreadState>* thread = nullptr;
		if (mThreadPool.empty())
		{
			thread = new WorkerThread<EGLThreadState>(name, Affinity::Odd);
		}
		else
		{
			thread = mThreadPool.top();
			mThreadPool.pop();
		}

		thread->setIsWorking(true);

		return thread;
	}

	void ThreadManager::releaseThread(WorkerThread<EGLThreadState>* thread,
	                                  std::function<void()> onRelease)
	{
		std::lock_guard<std::mutex> threadLock(mMutex);
		// Thread state needs to release its resources also.
		thread->setIsWorking(false);
		thread->releaseQueue(onRelease);
		mThreadPool.push(thread);
	}
} // namespace rive_android
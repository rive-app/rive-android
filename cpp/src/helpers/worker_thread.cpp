#include "helpers/worker_thread.hpp"

namespace rive_android
{
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
} // namespace rive_android
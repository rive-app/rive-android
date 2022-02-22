/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <condition_variable>
#include <mutex>
#include <queue>
#include <stack>
#include <thread>

#include "helpers/general.hpp"
#include "helpers/egl_thread_state.hpp"
#include "settings.hpp"
#include "thread.hpp"

namespace rive_android
{
	class HotPocketState
	{
	public:
		void onSettingsChanged(const Settings* settings)
		{
			isEnabled = settings->getHotPocket();
		}

		bool isEnabled = false;
		bool isStarted = false;
	};

	template <class ThreadState> class WorkerThread
	{
	public:
		using Work = std::function<void(ThreadState*)>;

		WorkerThread(const char* name, Affinity affinity) :
		    mName(name), mAffinity(affinity)
		{
			launchThread();
			auto settingsChanged = [this](ThreadState* threadState)
			{ onSettingsChanged(threadState); };
			Settings::getInstance()->addListener(
			    [this, work = std::move(settingsChanged)]() { run(work); });
		}

		~WorkerThread()
		{
			std::lock_guard<std::mutex> threadLock(mThreadMutex);
			terminateThread();
			// Detach thread from the JVM.
			detachThread();
		}

		void run(Work work)
		{
			if (!mIsWorking)
			{
				LOGW("Can't add work while thread isn't running.");
				return;
			}
			std::lock_guard<std::mutex> workLock(mWorkMutex);
			mWorkQueue.emplace(std::move(work));
			mWorkCondition.notify_all();
		}

		void releaseQueue(std::function<void()> onRelease = nullptr)
		{
			std::lock_guard<std::mutex> workLock(mWorkMutex);
			// Prevent any other work to be added here.
			drainWorkQueue();
			// Force onto work queue, bypassing our runner function.
			mWorkQueue.emplace(std::move(
			    [=](EGLThreadState* threadState)
			    {
				    threadState->mIsStarted = false;
				    threadState->clearSurface();
				    threadState->unsetKtRendererClass();
				    if (onRelease)
				    {
					    onRelease();
				    }
			    }));
			mWorkCondition.notify_all();
		}

		void setIsWorking(bool isIt, std::function<void()> onEvent = nullptr)
		{
			if (isIt == mIsWorking)
				return;

			mIsWorking = isIt;
		}

		void reset() { launchThread(); }

	private:
		void launchThread()
		{
			std::lock_guard<std::mutex> threadLock(mThreadMutex);
			if (mThread.joinable())
			{
				terminateThread();
			}
			mThread = std::thread([this]() { threadMain(); });
		}

		void terminateThread() REQUIRES(mThreadMutex)
		{
			{
				std::lock_guard<std::mutex> workLock(mWorkMutex);
				mIsActive = false;
				mWorkCondition.notify_all();
			}
			mThread.join();
		}

		void drainWorkQueue()
		{
			while (!mWorkQueue.empty())
			{
				mWorkQueue.pop();
			}
		}

		void onSettingsChanged(ThreadState* threadState)
		{
			const Settings* settings = Settings::getInstance();
			threadState->onSettingsChanged(settings);
			setAffinity(mAffinity);
		}

		void threadMain()
		{
			setAffinity(mAffinity);
			pthread_setname_np(pthread_self(), mName.c_str());

			ThreadState threadState;

			std::lock_guard<std::mutex> lock(mWorkMutex);
			while (mIsActive)
			{
				mWorkCondition.wait(mWorkMutex,
				                    [this]() REQUIRES(mWorkMutex) {
					                    return !mWorkQueue.empty() ||
					                           !mIsActive;
				                    });
				if (!mWorkQueue.empty())
				{
					auto head = mWorkQueue.front();
					mWorkQueue.pop();

					// Drop the mutex while we execute
					mWorkMutex.unlock();
					head(&threadState);
					mWorkMutex.lock();
				}
			}
		}

		const std::string mName;
		const Affinity mAffinity;

		std::mutex mThreadMutex;
		std::thread mThread GUARDED_BY(mThreadMutex);

		bool mIsWorking = true;

		std::mutex mWorkMutex;
		bool mIsActive GUARDED_BY(mWorkMutex) = true;
		std::queue<std::function<void(ThreadState*)>>
		    mWorkQueue GUARDED_BY(mWorkMutex);
		std::condition_variable_any mWorkCondition;
	};

	class ThreadManager
	{
	private:
		ThreadManager() : mThreadPool{} {};
		~ThreadManager()
		{
			// Clean up all the threads.
			while (!mThreadPool.empty())
			{
				auto current = mThreadPool.top();
				mThreadPool.pop();
				delete current;
			}
		}

		static ThreadManager* mInstance;
		static std::mutex mMutex;

		std::stack<WorkerThread<EGLThreadState>*> mThreadPool;

	public:
		// Singleton getter.
		static ThreadManager* getInstance();
		// Singleton can't be copied/assigned.
		ThreadManager(ThreadManager const&) = delete;
		void operator=(ThreadManager const&) = delete;

		WorkerThread<EGLThreadState>*
		acquireThread(const char* name,
		              std::function<void()> onAcquire = nullptr);

		void releaseThread(WorkerThread<EGLThreadState>* thread,
		                   std::function<void()> onRelease = nullptr);
	};
} // namespace rive_android

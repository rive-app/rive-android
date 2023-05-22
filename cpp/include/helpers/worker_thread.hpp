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
#include "helpers/egl_share_thread_state.hpp"
#include "thread.hpp"

namespace rive_android
{
template <class ThreadState> class WorkerThread
{
public:
    using Work = std::function<void(ThreadState*)>;

    WorkerThread(const char* name, Affinity affinity) : mName(name), mAffinity(affinity)
    {
        launchThread();
    }

    ~WorkerThread()
    {
        if (mThread.joinable())
        {
            terminateThread();
        }
    }

    bool run(Work work)
    {
        if (!mIsWorking)
        {
            LOGW("Can't add work while thread isn't running.");
            return false;
        }
        std::lock_guard<std::mutex> workLock(mWorkMutex);
        mWorkQueue.emplace(std::move(work));
        mWorkCondition.notify_one();
        return true;
    }

    void releaseQueue(std::function<void()> onRelease = nullptr)
    {
        std::lock_guard<std::mutex> workLock(mWorkMutex);
        // Prevent any other work to be added here.
        drainWorkQueue();
        // Force onto work queue, bypassing our runner function.
        mWorkQueue.emplace(std::move([=](EGLShareThreadState* threadState) {
            threadState->mIsStarted = false;
            threadState->destroySurface();
            threadState->unsetKtRendererClass();
            if (onRelease)
            {
                onRelease();
            }
        }));
        mWorkCondition.notify_one();
    }

    void setIsWorking(bool isIt)
    {
        if (isIt == mIsWorking)
            return;

        mIsWorking = isIt;
    }

    void drainWorkQueue()
    {
        while (!mWorkQueue.empty())
        {
            mWorkQueue.pop();
        }
    }

    void terminateThread() REQUIRES(mThreadMutex)
    {
        {
            std::lock_guard<std::mutex> workLock(mWorkMutex);
            drainWorkQueue();
            mIsActive = false;
            mWorkCondition.notify_one();
        }
        mThread.join();
    }

private:
    friend class ThreadManager;
    void launchThread()
    {
        std::lock_guard<std::mutex> threadLock(mThreadMutex);
        mIsActive = true;
        if (mThread.joinable())
        {
            terminateThread();
        }
        mThread = std::thread([this]() { threadMain(); });
    }

    void threadMain()
    {
        setAffinity(mAffinity);
        pthread_setname_np(pthread_self(), mName.c_str());

        getJNIEnv(); // Attach thread to JVM.
        ThreadState threadState;

        std::lock_guard<std::mutex> lock(mWorkMutex);
        while (mIsActive)
        {
            mWorkCondition.wait(mWorkMutex, [this]() REQUIRES(mWorkMutex) {
                return !mWorkQueue.empty() || !mIsActive;
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
        detachThread();
    }

    const std::string mName;
    const Affinity mAffinity;

    std::mutex mThreadMutex;
    std::thread mThread GUARDED_BY(mThreadMutex);

    bool mIsWorking = true;

    std::mutex mWorkMutex;
    bool mIsActive GUARDED_BY(mWorkMutex) = true;
    std::queue<std::function<void(ThreadState*)>> mWorkQueue GUARDED_BY(mWorkMutex);
    std::condition_variable_any mWorkCondition;
};

class ThreadManager
{
private:
    ThreadManager() : mWorkers{} {};

    static std::weak_ptr<ThreadManager> mInstance;
    static std::mutex mMutex;

    std::stack<WorkerThread<EGLShareThreadState>*> GUARDED_BY(mMutex) mWorkers;

public:
    // Destructor is public so `shared_ptr` can dellocate.
    ~ThreadManager()
    {
        std::lock_guard<std::mutex> threadLock(mMutex);
        // Clean up all the threads.
        while (!mWorkers.empty())
        {
            auto current = mWorkers.top();
            mWorkers.pop();
            delete current;
        }
    }
    // Singleton getter.
    static std::shared_ptr<ThreadManager> getInstance();
    // Singleton can't be copied/assigned/moved.
    ThreadManager(ThreadManager const&) = delete;
    ThreadManager& operator=(ThreadManager const&) = delete;
    ThreadManager(ThreadManager&&) = delete;
    ThreadManager& operator=(ThreadManager&&) = delete;

    WorkerThread<EGLShareThreadState>* acquireWorker(const char*);

    void releaseThread(WorkerThread<EGLShareThreadState>* thread,
                       std::function<void()> onRelease = nullptr);
    void putBack(WorkerThread<EGLShareThreadState>* thread);
};
} // namespace rive_android

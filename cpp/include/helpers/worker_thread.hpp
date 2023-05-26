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

#include <cassert>
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

    ~WorkerThread() { terminateThread(); }

    bool run(Work work) REQUIRES(mThreadMutex)
    {
        {
            std::lock_guard workLock(mWorkMutex);
            if (!mIsWorking)
            {
                LOGW("Can't add work while thread isn't running.");
                return false;
            }
            assert(work != nullptr);
            assert(!mIsTerminated);
            mWorkQueue.emplace(std::move(work));
        }
        mWorkCondition.notify_one();
        return true;
    }

    void setIsWorking(bool isIt) REQUIRES(mThreadMutex)
    {
        std::lock_guard<std::mutex> workLock(mWorkMutex);
        mIsWorking = isIt;
    }

    void terminateThread() REQUIRES(mThreadMutex)
    {
        bool didSendTerminationToken = false;
        {
            std::lock_guard workLock(mWorkMutex);
            if (!mIsTerminated)
            {
                // A null function is a special token that tells the thread to terminate.
                mWorkQueue.emplace(nullptr);
                didSendTerminationToken = true;
                mIsTerminated = true;
            }
        }
        if (didSendTerminationToken)
        {
            mWorkCondition.notify_one();
            mThread.join();
        }
    }

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

    void threadMain()
    {
        setAffinity(mAffinity);
        pthread_setname_np(pthread_self(), mName.c_str());

        getJNIEnv(); // Attach thread to JVM.
        ThreadState threadState;

        std::lock_guard lock(mWorkMutex);
        for (;;)
        {
            mWorkCondition.wait(mWorkMutex,
                                [this]() REQUIRES(mWorkMutex) { return !mWorkQueue.empty(); });
            Work work = mWorkQueue.front();
            mWorkQueue.pop();

            if (!work)
            {
                // A null function is a special token that tells the thread to terminate.
                break;
            }

            // Drop the mutex while we execute
            mWorkMutex.unlock();
            work(&threadState);
            mWorkMutex.lock();
        }
        detachThread();
    }

    const std::string mName;
    const Affinity mAffinity;

    std::mutex mThreadMutex;
    std::thread mThread GUARDED_BY(mThreadMutex);

    bool mIsWorking GUARDED_BY(mWorkMutex) = true;
    bool mIsTerminated GUARDED_BY(mWorkMutex) = false;

    std::mutex mWorkMutex;
    std::queue<std::function<void(ThreadState*)>> mWorkQueue GUARDED_BY(mWorkMutex);
    std::condition_variable_any mWorkCondition;
};
} // namespace rive_android

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
    using WorkID = uint64_t;

    WorkerThread(const char* name, Affinity affinity) :
        mName(name), mAffinity(affinity), mThread(std::thread([this]() { threadMain(); }))
    {}

    ~WorkerThread() { terminateThread(); }

    WorkID run(Work work)
    {
        assert(work != nullptr); // Clients can't push the null termination token.
        uint64_t pushedWorkID;
        {
            std::lock_guard workLock(mWorkMutex);
            assert(!mIsTerminated);
            mWorkQueue.emplace(std::move(work));
            pushedWorkID = ++m_lastPushedWorkID;
        }
        m_workPushedCondition.notify_one();
        return pushedWorkID;
    }

    void waitUntilComplete(WorkID workID)
    {
        if (m_lastCompletedWorkID >= workID)
        {
            return; // Early out that doesn't require a mutex!
        }
        std::lock_guard<std::mutex> threadLock(mWorkMutex);
        while (m_lastCompletedWorkID < workID)
        {
            m_workedCompletedCondition.wait(mWorkMutex);
        }
    }

    void runAndWait(Work work) { waitUntilComplete(run(work)); }

    void terminateThread()
    {
        bool didSendTerminationToken = false;
        {
            std::lock_guard workLock(mWorkMutex);
            if (!mIsTerminated)
            {
                mWorkQueue.emplace(nullptr);
                mIsTerminated = true;
                didSendTerminationToken = true;
            }
        }
        if (didSendTerminationToken)
        {
            m_workPushedCondition.notify_one();
            mThread.join();
        }
        assert(m_lastCompletedWorkID == m_lastPushedWorkID);
    }

private:
    void threadMain()
    {
        setAffinity(mAffinity);
        pthread_setname_np(pthread_self(), mName.c_str());

        getJNIEnv(); // Attach thread to JVM.
        ThreadState threadState;

        for (;;)
        {
            Work work;
            {
                std::lock_guard lock(mWorkMutex);
                while (mWorkQueue.empty())
                {
                    m_workPushedCondition.wait(mWorkMutex);
                }
                work = mWorkQueue.front();
                mWorkQueue.pop();
            }

            if (!work)
            {
                // A null function is a special token that tells the thread to terminate.
                break;
            }

            work(&threadState);
            ++m_lastCompletedWorkID;
            m_workedCompletedCondition.notify_all();
        }
        detachThread();
    }

    const std::string mName;
    const Affinity mAffinity;
    std::thread mThread;

    WorkID m_lastPushedWorkID = 0;
    std::atomic<WorkID> m_lastCompletedWorkID = 0;
    bool mIsTerminated = false;

    std::mutex mWorkMutex;
    std::queue<std::function<void(ThreadState*)>> mWorkQueue;
    std::condition_variable_any m_workPushedCondition;
    std::condition_variable_any m_workedCompletedCondition;
};
} // namespace rive_android

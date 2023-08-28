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
#include "helpers/thread_state_egl.hpp"
#include "thread.hpp"

namespace rive_android
{
class WorkerThread
{
public:
    using Work = std::function<void(EGLThreadState*)>;
    using WorkID = uint64_t;

    WorkerThread(const char* name, Affinity affinity, const RendererType rendererType) :
        m_RendererType(rendererType),
        mName(name),
        mAffinity(affinity),
        mThread(std::thread([this]() { threadMain(); }))
    {}

    ~WorkerThread() { terminateThread(); }

    WorkID run(Work&& work)
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

    void runAndWait(Work&& work) { waitUntilComplete(run(std::move(work))); }

    bool canScheduleWork(WorkID workID) const { return m_lastCompletedWorkID >= workID; }

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

    RendererType rendererType() const { return m_RendererType; }

protected:
    const RendererType m_RendererType;

private:
    static std::unique_ptr<EGLThreadState> MakeThreadState(const RendererType type);

    void threadMain()
    {
        setAffinity(mAffinity);
        pthread_setname_np(pthread_self(), mName.c_str());

        GetJNIEnv(); // Attach thread to JVM.
        std::unique_ptr<EGLThreadState> threadState = MakeThreadState(m_RendererType);

        std::unique_lock lock(mWorkMutex);
        for (;;)
        {
            while (mWorkQueue.empty())
            {
                m_workPushedCondition.wait(mWorkMutex);
            }
            Work work = mWorkQueue.front();
            mWorkQueue.pop();

            if (!work)
            {
                // A null function is a special token that tells the thread to terminate.
                break;
            }

            lock.unlock();
            work(threadState.get());
            lock.lock();

            ++m_lastCompletedWorkID;
            m_workedCompletedCondition.notify_all();
        }
        DetachThread();
    }

    const std::string mName;
    const Affinity mAffinity;
    std::thread mThread;

    WorkID m_lastPushedWorkID = 0;
    std::atomic<WorkID> m_lastCompletedWorkID = 0;
    bool mIsTerminated = false;

    std::mutex mWorkMutex;
    std::queue<std::function<void(EGLThreadState*)>> mWorkQueue;
    std::condition_variable_any m_workPushedCondition;
    std::condition_variable_any m_workedCompletedCondition;
};
} // namespace rive_android

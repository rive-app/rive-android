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
    using Work = std::function<void(DrawableThreadState*)>;
    using WorkID = uint64_t;
    constexpr static WorkID kWorkIDAlwaysFinished = 0;

    // A worker object that starts a background thread to perform its tasks.
    WorkerThread(const char* name,
                 Affinity affinity,
                 const RendererType rendererType) :
        m_RendererType(rendererType),
        mName(name),
        mAffinity(affinity),
        mWorkMutex{}
    {
        // Don't launch the worker thread until all of our objects are fully
        // initialized.
        mThread = std::thread([this]() { threadMain(); });
    }

    virtual ~WorkerThread() { terminateThread(); }

    const std::thread::id threadID() const { return mThread.get_id(); }

    // Only accessible on the worker thread.
    DrawableThreadState* threadState() const
    {
        assert(std::this_thread::get_id() == threadID());
        assert(m_threadState != nullptr);
        return m_threadState.get();
    }

    WorkID run(Work&& work)
    {
        assert(work !=
               nullptr); // Clients can't push the null termination token.
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
    static std::unique_ptr<DrawableThreadState> MakeThreadState(
        const RendererType type);

    void threadMain()
    {
        setAffinity(mAffinity);
        pthread_setname_np(pthread_self(), mName.c_str());

        GetJNIEnv(); // Attach thread to JVM.
        m_threadState = MakeThreadState(m_RendererType);

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
                // A null function is a special token that tells the thread to
                // terminate.
                break;
            }

            lock.unlock();
            work(m_threadState.get());
            lock.lock();

            ++m_lastCompletedWorkID;
            m_workedCompletedCondition.notify_all();
        }
        m_threadState.reset();
        DetachThread();
    }

    const std::string mName;
    const Affinity mAffinity;

    WorkID m_lastPushedWorkID = kWorkIDAlwaysFinished;
    std::atomic<WorkID> m_lastCompletedWorkID = kWorkIDAlwaysFinished;
    bool mIsTerminated = false;

    std::queue<std::function<void(DrawableThreadState*)>> mWorkQueue;
    std::condition_variable_any m_workPushedCondition;
    std::condition_variable_any m_workedCompletedCondition;

    std::mutex mWorkMutex;
    std::thread mThread;
    std::unique_ptr<DrawableThreadState> m_threadState;
};
} // namespace rive_android

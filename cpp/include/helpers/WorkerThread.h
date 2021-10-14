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
#include <thread>

#include "Settings.h"
#include "Thread.h"

namespace samples {

template<class ThreadState>
class WorkerThread {
public:
    using Work = std::function<void(ThreadState *)>;

    WorkerThread(const char *name, Affinity affinity)
        : mName(name),
          mAffinity(affinity) {
        launchThread();
        auto settingsChanged = [this](ThreadState *threadState) { onSettingsChanged(threadState); };
        Settings::getInstance()->addListener(
            [this, work = std::move(settingsChanged)]() { run(work); });
    }

    ~WorkerThread() {
        std::lock_guard<std::mutex> threadLock(mThreadMutex);
        terminateThread();
    }

    void run(Work work) {
        std::lock_guard<std::mutex> workLock(mWorkMutex);
        mWorkQueue.emplace(std::move(work));
        mWorkCondition.notify_all();
    }

    void reset() {
        launchThread();
    }

private:
    void launchThread() {
        std::lock_guard<std::mutex> threadLock(mThreadMutex);
        if (mThread.joinable()) {
            terminateThread();
        }
        mThread = std::thread([this]() { threadMain(); });
    }

    void terminateThread() REQUIRES(mThreadMutex) {
        {
            std::lock_guard<std::mutex> workLock(mWorkMutex);
            mIsActive = false;
            mWorkCondition.notify_all();
        }
        mThread.join();
    }

    void onSettingsChanged(ThreadState *threadState) {
        const Settings *settings = Settings::getInstance();
        threadState->onSettingsChanged(settings);
        setAffinity(settings->getUseAffinity() ? mAffinity : Affinity::None);
    }

    void threadMain() {
        setAffinity(Settings::getInstance()->getUseAffinity() ? mAffinity : Affinity::None);
        pthread_setname_np(pthread_self(), mName.c_str());

        ThreadState threadState;

        std::lock_guard<std::mutex> lock(mWorkMutex);
        while (mIsActive) {
            mWorkCondition.wait(mWorkMutex,
                                [this]() REQUIRES(mWorkMutex) {
                                    return !mWorkQueue.empty() || !mIsActive;
                                });
            if (!mWorkQueue.empty()) {
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

    std::mutex mWorkMutex;
    bool mIsActive GUARDED_BY(mWorkMutex) = true;
    std::queue<std::function<void(ThreadState *)>> mWorkQueue GUARDED_BY(mWorkMutex);
    std::condition_variable_any mWorkCondition;
};

} // namespace samples

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
#include <memory>
#include <mutex>
#include <queue>
#include <thread>

#include <EGL/egl.h>

#include "helpers/Thread.h"
#include "helpers/WorkerThread.h"

namespace samples
{

    class Settings;

    // Running mean and variance calculation
    class Stats
    {
        double mLatestMean = 0, mLatestVar = 0;
        double mRunningMean = 0, mRunningVar = 0;
        size_t mN = 0;
        size_t mNumToAvg;

    public:
        Stats(size_t numToAvg) : mNumToAvg(numToAvg) {}

        // Add a sample.
        // When mNumToAvg samples have been calculated, store the mean and average and start again.
        void add(double x)
        {
            ++mN;
            auto prevMean = mRunningMean;
            mRunningMean = ((mN - 1) * mRunningMean + x) / mN;
            if (mN > 1)
            {
                mRunningVar = ((mN - 2) * mRunningVar) / (mN - 1) + (x - prevMean) * (x - prevMean) / mN;
            }
            if (mN == mNumToAvg)
                restart();
        }

        void restart()
        {
            mLatestMean = mRunningMean;
            mLatestVar = mRunningVar;
            mN = 0;
        }

        double mean() const { return mLatestMean; }

        double var() const { return mLatestVar; }
    };

    class Renderer
    {
        // Allows construction with std::unique_ptr from a static method, but disallows construction
        // outside of the class since no one else can construct a ConstructorTag
        struct ConstructorTag
        {
        };

    public:
        explicit Renderer(ConstructorTag) {}

        static Renderer *getInstance();

        // Sets the active window to render into
        // Takes ownership of window and will release its reference
        void setWindow(ANativeWindow *window, int32_t width, int32_t height);

        void start();

        void stop();

        float getAverageFps();

        void requestDraw();

        void setWorkload(int load);

        void setSwappyEnabled(bool enabled);

        Stats &frameTimeStats() { return mFrameTimeStats; }

    private:
        class ThreadState
        {
        public:
            ThreadState();

            ~ThreadState();

            void onSettingsChanged(const Settings *);

            void clearSurface();

            bool configHasAttribute(EGLConfig config, EGLint attribute, EGLint value);

            EGLBoolean makeCurrent(EGLSurface surface);

            EGLDisplay display = EGL_NO_DISPLAY;
            EGLConfig config = static_cast<EGLConfig>(0);
            EGLSurface surface = EGL_NO_SURFACE;
            EGLContext context = EGL_NO_CONTEXT;

            bool isStarted = false;

            std::chrono::time_point<std::chrono::steady_clock> lastUpdate = std::chrono::steady_clock::now();
            float x = 0.0f;
            float velocity = 1.6f;

            std::chrono::nanoseconds refreshPeriod = std::chrono::nanoseconds{0};
            int64_t swapIntervalNS = 0;
            int32_t width = 0;
            int32_t height = 0;
        };

        void draw(ThreadState *threadState);
        void calculateFps();

        WorkerThread<ThreadState> mWorkerThread = {"Renderer", Affinity::Odd};

        class HotPocketState
        {
        public:
            void onSettingsChanged(const Settings *);

            bool isEnabled = false;
            bool isStarted = false;
        };

        WorkerThread<HotPocketState> mHotPocketThread = {"HotPocket", Affinity::Even};

        void spin();

        float averageFps = -1.0f;

        int mWorkload = 0;

        // Mean and variance for the pipeline frame time.
        Stats mFrameTimeStats = Stats(20 /* number of samples to average over */);

        bool mSwappyEnabled = true;
    };

} // namespace samples

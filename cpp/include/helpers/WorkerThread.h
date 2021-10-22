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

#include <GLES3/gl3.h>

#include <android/native_window.h>

#include "swappy/swappyGL.h"
#include "swappy/swappyGL_extra.h"
#include "helpers/general.hpp"

#include "SkSurface.h"
#include "SkImageInfo.h"
#include "GrBackendSurface.h"
#include "GrDirectContext.h"
#include "gl/GrGLInterface.h"
#include "gl/GrGLAssembleInterface.h"

#include "Settings.h"
#include "Thread.h"

namespace samples
{

    class HotPocketState
    {
    public:
        void onSettingsChanged(const Settings *settings)
        {
            isEnabled = settings->getHotPocket();
        }

        bool isEnabled = false;
        bool isStarted = false;
    };

    class ThreadState
    {
    public:
        ThreadState()
        {
            display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
            eglInitialize(display, 0, 0);

            const EGLint configAttributes[] = {
                EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL_BLUE_SIZE, 8,
                EGL_GREEN_SIZE, 8,
                EGL_RED_SIZE, 8,
                EGL_DEPTH_SIZE, 16,
                EGL_NONE};

            EGLint numConfigs = 0;
            eglChooseConfig(
                display,
                configAttributes,
                nullptr,
                0,
                &numConfigs);
            std::vector<EGLConfig> supportedConfigs(static_cast<size_t>(numConfigs));
            eglChooseConfig(display, configAttributes, supportedConfigs.data(), numConfigs, &numConfigs);

            // Choose a config, either a match if possible or the first config otherwise

            const auto configMatches = [&](EGLConfig config)
            {
                if (!configHasAttribute(config, EGL_RED_SIZE, 8))
                    return false;
                if (!configHasAttribute(config, EGL_GREEN_SIZE, 8))
                    return false;
                if (!configHasAttribute(config, EGL_BLUE_SIZE, 8))
                    return false;
                return configHasAttribute(config, EGL_DEPTH_SIZE, 16);
            };

            const auto configIter = std::find_if(
                supportedConfigs.cbegin(), supportedConfigs.cend(),
                configMatches);

            config = (configIter != supportedConfigs.cend()) ? *configIter : supportedConfigs[0];

            const EGLint contextAttributes[] = {
                EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL_NONE};

            context = eglCreateContext(display, config, nullptr, contextAttributes);

            // glEnable(GL_CULL_FACE);
            // glDisable(GL_DEPTH_TEST);
        }

        ~ThreadState()
        {
            clearSurface();
            if (context != EGL_NO_CONTEXT)
                eglDestroyContext(display, context);
            if (display != EGL_NO_DISPLAY)
                eglTerminate(display);
        }

        void onSettingsChanged(const Settings *settings)
        {
            refreshPeriod = settings->getRefreshPeriod();
            swapIntervalNS = settings->getSwapIntervalNS();
        }

        void clearSurface()
        {
            if (surface == EGL_NO_SURFACE)
            {
                return;
            }

            makeCurrent(EGL_NO_SURFACE);
            eglDestroySurface(display, surface);
            surface = EGL_NO_SURFACE;
        }

        bool configHasAttribute(EGLConfig config, EGLint attribute, EGLint value)
        {
            EGLint outValue = 0;
            EGLBoolean result = eglGetConfigAttrib(display, config, attribute, &outValue);
            return result && (outValue == value);
        }

        EGLBoolean makeCurrent(EGLSurface surface)
        {
            return eglMakeCurrent(display, surface, surface, context);
        }

        sk_sp<GrDirectContext> createGrContext()
        {
            if (!makeCurrent(surface))
            {
                LOGE("Unable to eglMakeCurrent");
                surface = EGL_NO_SURFACE;
                return nullptr;
            }

            auto get_string =
                reinterpret_cast<PFNGLGETSTRINGPROC>(getProcAddress("glGetString"));

            if (!get_string)
            {
                LOGE("get_string() failed");
                return nullptr;
            }

            auto c_version = reinterpret_cast<const char *>(get_string(GL_VERSION));
            if (c_version == NULL)
            {
                LOGE("c_version failed");
                return nullptr;
            }

            auto get_proc = [](void *context, const char name[]) -> GrGLFuncPtr
            {
                return reinterpret_cast<GrGLFuncPtr>(
                    reinterpret_cast<ThreadState *>(context)->getProcAddress(name));
            };
            std::string version(c_version);
            auto interface = version.find("OpenGL ES") == std::string::npos
                                 ? GrGLMakeAssembledGLInterface(this, get_proc)
                                 : GrGLMakeAssembledGLESInterface(this, get_proc);
            if (!interface)
            {
                LOGE("Failed to find the interface version!?");
                return nullptr;
            }
            mSkContext = GrDirectContext::MakeGL(interface);
            return mSkContext;
        }

        sk_sp<GrDirectContext> getGrContext()
        {
            if (mSkContext)
            {
                return mSkContext;
            }

            return createGrContext();
        }

        sk_sp<SkSurface> createSkSurface()
        {
            static GrGLFramebufferInfo fbInfo = {};
            fbInfo.fFBOID = 0u;
            fbInfo.fFormat = GL_RGBA8;

            static GrBackendRenderTarget backendRenderTarget(
                width, height,
                1, 8,
                fbInfo);
            static SkSurfaceProps surfaceProps(0, kUnknown_SkPixelGeometry);

            mSkSurface = SkSurface::MakeFromBackendRenderTarget(
                getGrContext().get(),
                backendRenderTarget,
                kBottomLeft_GrSurfaceOrigin,
                kRGBA_8888_SkColorType,
                SkColorSpace::MakeSRGB(),
                &surfaceProps,
                nullptr,
                nullptr);

            if (!mSkSurface)
            {
                LOGE("Failed to get GPU Surface?!");
                return nullptr;
            }

            return mSkSurface;
        }

        sk_sp<SkSurface> getSkSurface()
        {
            if (mSkSurface)
            {
                return mSkSurface;
            }

            return createSkSurface();
        }

        void *getProcAddress(const char *name) const
        {
            if (name == nullptr)
            {
                return nullptr;
            }

            auto symbol = eglGetProcAddress(name);
            if (symbol == NULL)
            {
                LOGE("Couldn't fetch symbol name for: %s", name);
            }

            return reinterpret_cast<void *>(symbol);
        }

        bool hasNoSurface() const {
            return surface == EGL_NO_SURFACE || mSkSurface == nullptr;
        }

        EGLDisplay display = EGL_NO_DISPLAY;
        EGLConfig config = static_cast<EGLConfig>(0);
        EGLSurface surface = EGL_NO_SURFACE;
        EGLContext context = EGL_NO_CONTEXT;

        sk_sp<GrDirectContext> mSkContext = nullptr;
        sk_sp<SkSurface> mSkSurface = nullptr;

        bool isStarted = false;

        std::chrono::time_point<std::chrono::steady_clock> lastUpdate = std::chrono::steady_clock::now();

        std::chrono::nanoseconds refreshPeriod = std::chrono::nanoseconds{0};
        int64_t swapIntervalNS = 0;
        int32_t width = 0;
        int32_t height = 0;
    };

    template <class ThreadState>
    class WorkerThread
    {
    public:
        using Work = std::function<void(ThreadState *)>;

        WorkerThread(const char *name, Affinity affinity)
            : mName(name),
              mAffinity(affinity)
        {
            launchThread();
            auto settingsChanged = [this](ThreadState *threadState)
            { onSettingsChanged(threadState); };
            Settings::getInstance()->addListener(
                [this, work = std::move(settingsChanged)]()
                { run(work); });
        }

        ~WorkerThread()
        {
            std::lock_guard<std::mutex> threadLock(mThreadMutex);
            terminateThread();
        }

        void run(Work work)
        {
            std::lock_guard<std::mutex> workLock(mWorkMutex);
            mWorkQueue.emplace(std::move(work));
            mWorkCondition.notify_all();
        }

        void reset()
        {
            launchThread();
        }

    private:
        void launchThread()
        {
            std::lock_guard<std::mutex> threadLock(mThreadMutex);
            if (mThread.joinable())
            {
                terminateThread();
            }
            mThread = std::thread([this]()
                                  { threadMain(); });
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

        void onSettingsChanged(ThreadState *threadState)
        {
            const Settings *settings = Settings::getInstance();
            threadState->onSettingsChanged(settings);
            setAffinity(settings->getUseAffinity() ? mAffinity : Affinity::None);
        }

        void threadMain()
        {
            setAffinity(Settings::getInstance()->getUseAffinity() ? mAffinity : Affinity::None);
            pthread_setname_np(pthread_self(), mName.c_str());

            ThreadState threadState;

            std::lock_guard<std::mutex> lock(mWorkMutex);
            while (mIsActive)
            {
                mWorkCondition.wait(mWorkMutex,
                                    [this]() REQUIRES(mWorkMutex)
                                    {
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
        }

        const std::string mName;
        const Affinity mAffinity;

        std::mutex mThreadMutex;
        std::thread mThread GUARDED_BY(mThreadMutex);

        std::mutex mWorkMutex;
        bool mIsActive GUARDED_BY(mWorkMutex) = true;
        std::queue<std::function<void(ThreadState *)> > mWorkQueue GUARDED_BY(mWorkMutex);
        std::condition_variable_any mWorkCondition;
    };

} // namespace samples

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

#include "helpers/Renderer.h"
#include "helpers/general.hpp"

#include <vector>

#include <GLES3/gl3.h>

#include <android/native_window.h>

#include "swappy/swappyGL.h"
#include "swappy/swappyGL_extra.h"

using namespace std::chrono_literals;

namespace samples
{

    Renderer *Renderer::getInstance()
    {
        static std::unique_ptr<Renderer> sRenderer = std::make_unique<Renderer>(ConstructorTag{});
        return sRenderer.get();
    }

    void Renderer::setWindow(ANativeWindow *window, int32_t width, int32_t height)
    {
        mWorkerThread.run([=](ThreadState *threadState)
                          {
                              threadState->clearSurface();

                              LOGE("Creating window surface %dx%d", width, height);

                              if (!window)
                              {
                                  SwappyGL_setWindow(nullptr);
                                  return;
                              }

                              threadState->surface =
                                  eglCreateWindowSurface(threadState->display, threadState->config, window, NULL);
                              ANativeWindow_release(window);
                              if (!threadState->makeCurrent(threadState->surface))
                              {
                                  LOGE("Unable to eglMakeCurrent");
                                  threadState->surface = EGL_NO_SURFACE;
                                  return;
                              }
                              SwappyGL_setWindow(window);

                              threadState->width = width;
                              threadState->height = height;
                          });
    }

    void Renderer::start()
    {

        mSwappyEnabled = SwappyGL_isEnabled();
        LOGI("=== Swappy is enabled? %d\n", mSwappyEnabled);

        mWorkerThread.run([this](ThreadState *threadState)
                          {
                              threadState->isStarted = true;
                              // Reset time to avoid super-large update of position
                              threadState->lastUpdate = std::chrono::steady_clock::now();
                              requestDraw();
                          });

        mHotPocketThread.run([this](HotPocketState *hotPocketState)
                             { hotPocketState->isStarted = true; });
        spin();
    }

    void Renderer::stop()
    {
        mWorkerThread.run([=](ThreadState *threadState)
                          { threadState->isStarted = false; });
        mHotPocketThread.run([](HotPocketState *hotPocketState)
                             { hotPocketState->isStarted = false; });
    }

    float Renderer::getAverageFps()
    {
        return averageFps;
    }

    void Renderer::requestDraw()
    {
        mWorkerThread.run(
            [=](ThreadState *threadState)
            {
                if (threadState->isStarted)
                    draw(threadState);
            });
    }

    Renderer::ThreadState::ThreadState()
    {
        display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        eglInitialize(display, 0, 0);

        const EGLint configAttributes[] = {
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_BLUE_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_RED_SIZE, 8,
            EGL_NONE};

        EGLint numConfigs = 0;
        eglChooseConfig(display, configAttributes, nullptr, 0, &numConfigs);
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
            return configHasAttribute(config, EGL_DEPTH_SIZE, 0);
        };

        const auto configIter = std::find_if(supportedConfigs.cbegin(), supportedConfigs.cend(),
                                             configMatches);

        config = (configIter != supportedConfigs.cend()) ? *configIter : supportedConfigs[0];

        const EGLint contextAttributes[] = {
            EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL_NONE};

        context = eglCreateContext(display, config, nullptr, contextAttributes);

        glEnable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);
    }

    Renderer::ThreadState::~ThreadState()
    {
        clearSurface();
        if (context != EGL_NO_CONTEXT)
            eglDestroyContext(display, context);
        if (display != EGL_NO_DISPLAY)
            eglTerminate(display);
    }

    void Renderer::ThreadState::onSettingsChanged(const Settings *settings)
    {
        refreshPeriod = settings->getRefreshPeriod();
        swapIntervalNS = settings->getSwapIntervalNS();
    }

    void Renderer::ThreadState::clearSurface()
    {
        if (surface == EGL_NO_SURFACE)
            return;

        makeCurrent(EGL_NO_SURFACE);
        eglDestroySurface(display, surface);
        surface = EGL_NO_SURFACE;
    }

    bool Renderer::ThreadState::configHasAttribute(EGLConfig config, EGLint attribute, EGLint value)
    {
        EGLint outValue = 0;
        EGLBoolean result = eglGetConfigAttrib(display, config, attribute, &outValue);
        return result && (outValue == value);
    }

    EGLBoolean Renderer::ThreadState::makeCurrent(EGLSurface surface)
    {
        return eglMakeCurrent(display, surface, surface, context);
    }

    void Renderer::HotPocketState::onSettingsChanged(const Settings *settings)
    {
        isEnabled = settings->getHotPocket();
    }

    void Renderer::spin()
    {
        mHotPocketThread.run([this](HotPocketState *hotPocketState)
                             {
                                 if (!hotPocketState->isEnabled || !hotPocketState->isStarted)
                                     return;
                                 for (int i = 1; i < 1000; ++i)
                                 {
                                     int value = i;
                                     while (value != 1)
                                     {
                                         if (value == 0)
                                         {
                                             LOGI("This will never run, but hopefully the compiler doesn't notice");
                                         }
                                         if (value % 2 == 0)
                                         {
                                             value /= 2;
                                         }
                                         else
                                         {
                                             value = 3 * value + 1;
                                         }
                                     }
                                 }
                                 spin();
                             });
    }

    // should be called once per draw as this function maintains the time delta between calls
    void Renderer::calculateFps()
    {
        static constexpr int FPS_SAMPLES = 10;
        static std::chrono::steady_clock::time_point prev = std::chrono::steady_clock::now();
        static float fpsSum = 0;
        static int fpsCount = 0;

        std::chrono::steady_clock::time_point now = std::chrono::steady_clock::now();
        fpsSum += 1.0f / ((now - prev).count() / 1e9f);
        fpsCount++;
        if (fpsCount == FPS_SAMPLES)
        {
            averageFps = fpsSum / fpsCount;
            fpsSum = 0;
            fpsCount = 0;
        }
        prev = now;
    }

    void Renderer::setWorkload(int load)
    {
        mWorkload = load;
    }

    void Renderer::setSwappyEnabled(bool enabled)
    {
        mSwappyEnabled = enabled && SwappyGL_isEnabled();
        if (mSwappyEnabled != enabled)
        {
            LOGW("Warning: Swappy couldn't be enabled, check the device API version");
        }
    }

    void Renderer::draw(ThreadState *threadState)
    {
        // Don't render if we have no surface
        if (threadState->surface == EGL_NO_SURFACE)
        {
            // Sleep a bit so we don't churn too fast
            std::this_thread::sleep_for(50ms);
            requestDraw();
            return;
        }

        if (mSwappyEnabled)
            SwappyGL_recordFrameStart(threadState->display, threadState->surface);

        calculateFps();

        float deltaSeconds = threadState->swapIntervalNS / 1e9f;
        if (threadState->lastUpdate - std::chrono::steady_clock::now() <= 100ms)
        {
            deltaSeconds = (threadState->lastUpdate - std::chrono::steady_clock::now()).count() / 1e9f;
        }
        threadState->lastUpdate = std::chrono::steady_clock::now();

        threadState->x += threadState->velocity * deltaSeconds;

        if (threadState->x > 0.8f)
        {
            threadState->velocity *= -1.0f;
            threadState->x = 1.6f - threadState->x;
        }
        else if (threadState->x < -0.8f)
        {
            threadState->velocity *= -1.0f;
            threadState->x = -1.6f - threadState->x;
        }

        // Just fill the screen with a color.
        glClearColor(1.0f, 0.3f, 0.3f, 1.0f);
        glClear(GL_DEPTH_BUFFER_BIT | GL_COLOR_BUFFER_BIT);

        const float aspectRatio = static_cast<float>(threadState->width) / threadState->height;

        if (mSwappyEnabled)
        {
            SwappyGL_swap(threadState->display, threadState->surface);
        }
        else
        {
            eglSwapBuffers(threadState->display, threadState->surface);
        }

        // If we're still started, request another frame
        requestDraw();
    }

} // namespace samples

#pragma once

#include <android/native_window.h>
#include <GLES3/gl3.h>
#include <jni.h>

#include "models/worker_impl.hpp"
#include "helpers/worker_ref.hpp"

namespace rive_android
{
class JNIRenderer
{
public:
    explicit JNIRenderer(jobject ktRenderer,
                         bool trace = false,
                         RendererType rendererType = RendererType::Rive);

    ~JNIRenderer();

    void scheduleDispose();

    void setSurface(SurfaceVariant);

    rive::Renderer* getRendererOnWorkerThread() const;

    void start();

    void stop();

    void doFrame();

    float averageFps() const { return m_averageFps; }

    RendererType rendererType() const { return m_worker->rendererType(); }

    int width() const
    {
        if (rendererType() == RendererType::Canvas)
        {
            // Sanity checks: make sure we are on worker thread
            assert(m_workerImpl);
            assert(m_workerThreadID != std::thread::id{});

            if (m_workerThreadID == std::thread::id{} || !m_workerImpl)
            {
                return INVALID_DIMENSION;
            }
            auto renderer =
                static_cast<CanvasRenderer*>(getRendererOnWorkerThread());
            return renderer ? renderer->width() : INVALID_DIMENSION;
        }
        else if (auto window = std::get_if<ANativeWindow*>(&m_surface))
        {
            return ANativeWindow_getWidth(*window);
        }
        return INVALID_DIMENSION;
    }
    int height() const
    {
        if (rendererType() == RendererType::Canvas)
        {
            // Sanity checks: make sure we are on worker thread
            assert(m_workerImpl);
            assert(m_workerThreadID != std::thread::id{});

            auto renderer =
                static_cast<CanvasRenderer*>(getRendererOnWorkerThread());
            return renderer ? renderer->height() : INVALID_DIMENSION;
        }
        else if (auto window = std::get_if<ANativeWindow*>(&m_surface))
        {
            return ANativeWindow_getHeight(*window);
        }
        return INVALID_DIMENSION;
    }

private:
    static constexpr uint8_t kMaxScheduledFrames = 2;
    static constexpr int INVALID_DIMENSION = -1;

    rive::rcp<RefWorker> m_worker;
    jobject m_ktRenderer;

    SurfaceVariant m_surface = std::monostate{};

    std::thread::id m_workerThreadID;
    std::unique_ptr<WorkerImpl> m_workerImpl;
    std::atomic_uint8_t m_numScheduledFrames = 0;

    /* Helpers for FPS calculations.*/
    std::chrono::steady_clock::time_point m_fpsLastFrameTime;
    std::atomic<float> m_averageFps = -1.0f;
    float m_fpsSum = 0.0f;
    int m_fpsCount = 0;

    std::atomic<bool> m_isDisposeScheduled = false;

    std::unique_ptr<ITracer> m_tracer;

    static std::unique_ptr<ITracer> makeTracer(bool trace);

    void calculateFps(std::chrono::high_resolution_clock::time_point frameTime);

    void acquireSurface(SurfaceVariant& surface)
    {
        if (auto** window = std::get_if<ANativeWindow*>(&surface))
        {
            ANativeWindow_acquire(*window);
            m_surface = *window;
        }
        else if (auto* ktSurface = std::get_if<jobject>(&surface))
        {
            m_surface = GetJNIEnv()->NewGlobalRef(*ktSurface);
        }
        else
        {
            m_surface = surface;
        }
    }

    static void releaseSurface(SurfaceVariant* surface)
    {
        if (auto** window = std::get_if<ANativeWindow*>(surface))
        {
            ANativeWindow_release(*window);
        }
        else if (auto* ktSurface = std::get_if<jobject>(surface))
        {
            GetJNIEnv()->DeleteGlobalRef(*ktSurface);
        }
        // Nothing to do if it's not one of these two types.
    }
};
} // namespace rive_android

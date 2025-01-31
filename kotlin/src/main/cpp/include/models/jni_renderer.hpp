#ifndef _RIVE_ANDROID_JNI_RENDERER_HPP_
#define _RIVE_ANDROID_JNI_RENDERER_HPP_
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
                         const RendererType rendererType = RendererType::Rive);

    ~JNIRenderer();

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
            auto renderer =
                static_cast<CanvasRenderer*>(getRendererOnWorkerThread());
            return renderer->width();
        }
        else if (auto window = std::get_if<ANativeWindow*>(&m_surface))
        {
            return ANativeWindow_getWidth(*window);
        }
        return -1;
    }
    int height() const
    {
        if (rendererType() == RendererType::Canvas)
        {
            auto renderer =
                static_cast<CanvasRenderer*>(getRendererOnWorkerThread());
            return renderer->height();
        }
        else if (auto window = std::get_if<ANativeWindow*>(&m_surface))
        {
            return ANativeWindow_getHeight(*window);
        }
        return -1;
    }

private:
    static constexpr uint8_t kMaxScheduledFrames = 2;

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

    ITracer* m_tracer;

    ITracer* getTracer(bool trace) const;

    void calculateFps(std::chrono::high_resolution_clock::time_point frameTime);

    void acquireSurface(SurfaceVariant& surface)
    {
        if (ANativeWindow** window = std::get_if<ANativeWindow*>(&surface))
        {
            ANativeWindow_acquire(*window);
            m_surface = *window;
        }
        else if (jobject* ktSurface = std::get_if<jobject>(&surface))
        {
            m_surface = GetJNIEnv()->NewGlobalRef(*ktSurface);
        }
        else
        {
            m_surface = surface;
        }
    }

    void releaseSurface(SurfaceVariant* surface)
    {
        if (ANativeWindow** window = std::get_if<ANativeWindow*>(surface))
        {
            ANativeWindow_release(*window);
        }
        else if (jobject* ktSurface = std::get_if<jobject>(surface))
        {
            GetJNIEnv()->DeleteGlobalRef(*ktSurface);
        }
        // Nothing to do if it's not one of these two types.
    }
};
} // namespace rive_android
#endif // _RIVE_ANDROID_JNI_RENDERER_HPP_

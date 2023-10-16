#ifndef _RIVE_ANDROID_JNI_RENDERER_HPP_
#define _RIVE_ANDROID_JNI_RENDERER_HPP_

#include <GLES3/gl3.h>
#include <jni.h>

#include "helpers/tracer.hpp"
#include "helpers/thread_state_egl.hpp"
#include "helpers/egl_worker.hpp"
#include "models/worker_impl.hpp"

#include "rive/renderer.hpp"

namespace rive_android
{
class JNIRenderer
{
public:
    JNIRenderer(jobject ktObject,
                bool trace = false,
                const RendererType rendererType = RendererType::Skia);

    ~JNIRenderer();

    void setWindow(ANativeWindow* window);

    rive::Renderer* getRendererOnWorkerThread() const;

    void start();

    void stop();

    void doFrame();

    float averageFps() const { return m_averageFps; }

    int width() const { return m_window ? ANativeWindow_getWidth(m_window) : -1; }
    int height() const { return m_window ? ANativeWindow_getHeight(m_window) : -1; }

private:
    static constexpr uint8_t kMaxScheduledFrames = 2;

    rive::rcp<EGLWorker> m_worker;
    jobject m_ktRenderer;

    ANativeWindow* m_window = nullptr;

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
};
} // namespace rive_android
#endif // _RIVE_ANDROID_JNI_RENDERER_HPP_

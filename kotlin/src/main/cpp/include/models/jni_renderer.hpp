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

    void start(long long timeNs);

    void stop();

    void doFrame(long long frameTimeNs);

    float averageFps() const { return mAverageFps; }

    int width() const { return m_window ? ANativeWindow_getWidth(m_window) : -1; }
    int height() const { return m_window ? ANativeWindow_getHeight(m_window) : -1; }

private:
    rive::rcp<EGLWorker> mWorker;

    jobject m_ktRenderer;

    ITracer* m_tracer;

    ANativeWindow* m_window = nullptr;

    std::thread::id m_workerThreadID;
    std::unique_ptr<WorkerImpl> m_workerImpl;

    /* Helpers for FPS calculations.*/
    std::chrono::steady_clock::time_point mLastFrameTime;
    float mAverageFps = -1.0f;
    float mFpsSum = 0;
    int mFpsCount = 0;

    EGLWorker::WorkID m_workIDForLastFrame = 0;

    ITracer* getTracer(bool trace) const;

    void calculateFps();
};
} // namespace rive_android
#endif // _RIVE_ANDROID_JNI_RENDERER_HPP_

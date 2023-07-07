#ifndef _RIVE_ANDROID_JAVA_RENDERER_SKIA_HPP_
#define _RIVE_ANDROID_JAVA_RENDERER_SKIA_HPP_

#include <GLES3/gl3.h>
#include <jni.h>

#include "skia_renderer.hpp"

#include "helpers/tracer.hpp"
#include "helpers/egl_share_thread_state.hpp"
#include "helpers/egl_worker.hpp"

namespace rive_android
{
class JNIRendererSkia
{
public:
    JNIRendererSkia(jobject ktObject, bool trace = false);

    ~JNIRendererSkia();

    void setWindow(ANativeWindow* window);

    rive::Renderer* getRendererOnWorkerThread() const;

    void start(long timeNs);
    void stop();

    void doFrame(long frameTimeNs);

    float averageFps() const { return mAverageFps; }

    int width() const { return m_window ? ANativeWindow_getWidth(m_window) : -1; }
    int height() const { return m_window ? ANativeWindow_getHeight(m_window) : -1; }

private:
    class WorkerSideImpl;

    rive::rcp<EGLWorker> mWorker = EGLWorker::Current();

    jobject m_ktRenderer;

    ITracer* m_tracer;

    ANativeWindow* m_window = nullptr;

    std::thread::id m_workerThreadID;
    std::unique_ptr<WorkerSideImpl> m_workerSideImpl;

    /* Helpers for FPS calculations.*/
    std::chrono::steady_clock::time_point mLastFrameTime;
    float mAverageFps = -1.0f;
    float mFpsSum = 0;
    int mFpsCount = 0;

    EGLWorker::WorkID m_workIDForLastFrame = 0;

    ITracer* getTracer(bool trace) const;

    void calculateFps();

    void draw(EGLShareThreadState* threadState);
};
} // namespace rive_android
#endif

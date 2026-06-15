#pragma once

#include <GLES3/gl3.h>
#include <android/native_window.h>
#include <chrono>
#include <jni.h>
#include <variant>

#include "helpers/worker_ref.hpp"
#include "models/worker_impl.hpp"

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
    static constexpr int kContextEventLost = 0;
    static constexpr int kContextEventRecovered = 1;
    static constexpr std::chrono::milliseconds kRecoveryBackoffInitial{50};
    static constexpr std::chrono::milliseconds kRecoveryBackoffMax{1000};

    /* Render loop state sum type (limiting recovery-specific data to that
     * state). `HealthyState` runs normal draw/flush. `RecoveringState`
     * suppresses drawing and uses frame ticks for backoff- based
     * context-recovery attempts. */
    struct HealthyState
    {};
    struct RecoveringState
    {
        EGLResult lastResult = EGLResult::Ok();
        std::chrono::steady_clock::time_point nextAttempt =
            std::chrono::steady_clock::time_point::min();
        std::chrono::milliseconds backoff = kRecoveryBackoffInitial;
    };
    using RenderLoopState = std::variant<HealthyState, RecoveringState>;
    RenderLoopState m_renderLoopState = HealthyState{};

    rive::rcp<RefWorker> m_worker;
    jobject m_ktRenderer;

    // The current surface from Android
    // Important: only assign in this worker thread lambda to preserve work
    // item ordering.
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

    void calculateFps(std::chrono::steady_clock::time_point frameTime);
    bool hasSurface() const
    {
        return !std::holds_alternative<std::monostate>(m_surface);
    }
    bool isRecovering() const
    {
        return std::holds_alternative<RecoveringState>(m_renderLoopState);
    }

    /**
     * Emits a render-context lifecycle event to the Kotlin renderer callback.
     *
     * @param eventType Native event discriminator (`lost` or `recovered`).
     * @param result EGL operation + error details for this event.
     */
    void notifyRenderContextEvent(int eventType, const EGLResult& result) const;

    /**
     * Transitions the render loop into recovering state after a fatal EGL
     * failure, notifies the app once, and tears down the current worker
     * instance.
     *
     * @param threadState Worker-thread drawable state used for teardown.
     * @param failure Fatal EGL failure that triggered recovery.
     * @param now Current monotonic time used to seed retry scheduling.
     */
    void enterRecoveringState(DrawableThreadState* threadState,
                              const EGLResult& failure,
                              std::chrono::steady_clock::time_point now);

    /**
     * Runs one recovery tick when in recovering state, subject to backoff and
     * surface availability.
     *
     * @param threadState Worker-thread drawable state used for EGL recovery.
     * @param now Current monotonic time used for backoff checks.
     * @return `true` when renderer is healthy after this call; `false` when it
     *         remains in recovering state.
     */
    bool attemptRecovery(DrawableThreadState* threadState,
                         std::chrono::steady_clock::time_point now);

    /* Acquire a reference to the given surface, normalizing for each variant
     * type.
     *
     * - Rive Renderer `ANativeWindow*`: increment native ref count
     * - Canvas Renderer `jobject` Surface: promote to a global JNI ref
     * - `std::monostate` sentinel: no change necessary
     */
    static SurfaceVariant acquireSurface(SurfaceVariant surface)
    {
        if (auto** window = std::get_if<ANativeWindow*>(&surface))
        {
            ANativeWindow_acquire(*window);
            return *window;
        }
        if (auto* ktSurface = std::get_if<jobject>(&surface))
        {
            return GetJNIEnv()->NewGlobalRef(*ktSurface);
        }
        return std::monostate{};
    }

    /* Release a reference to the given surface, normalizing for each variant
     * type.
     *
     * - Rive Renderer `ANativeWindow*`: decrement native ref count
     * - Canvas Renderer `jobject` Surface: delete global JNI ref
     * - `std::monostate` sentinel: no change necessary
     */
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

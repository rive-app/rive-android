#include "models/jni_renderer.hpp"

#include <algorithm>

#include "helpers/jni_exception_handler.hpp"
#include "helpers/jni_resource.hpp"
#include "helpers/rive_log.hpp"

using namespace std::chrono_literals;

namespace rive_android
{
constexpr auto* TAG = "RiveLN/JNIRenderer";

JNIRenderer::JNIRenderer(
    jobject ktRenderer,
    bool trace /* = false */,
    const RendererType rendererType /* = RendererType::Canvas */) :
    m_worker(RefWorker::CurrentOrFallback(rendererType)),
    // Grab a Global Ref to prevent Garbage Collection to clean up the object
    //  from under us since the destructor will be called from the render thread
    //  rather than the UI thread.
    m_ktRenderer(GetJNIEnv()->NewGlobalRef(ktRenderer)),
    m_tracer(makeTracer(trace))
{
    RiveLogD(TAG, "Creating JNIRenderer.");
}

JNIRenderer::~JNIRenderer()
{
    // This destructor is just a final sanity check: all heavy lifting is done
    // in the lambda inside `scheduleDisposal()`
    // We assert here to ensure that the asynchronous disposal path was taken.
    assert(m_isDisposeScheduled &&
           "JNIRenderer was deleted directly instead of scheduling disposal!");
    RiveLogD(TAG, "Deleting JNIRenderer.");
}

void JNIRenderer::scheduleDispose()
{
    bool isDisposed = m_isDisposeScheduled.exchange(true);
    assert(!isDisposed);

    // The lambda captures `this` to operate on the JNIRenderer instance.
    // This task is queued on the worker thread. The UI thread does not wait.
    m_worker->run([this](DrawableThreadState* threadState) {
        if (m_workerImpl)
        {
            m_workerImpl->stop();
            m_workerImpl->destroy(threadState);
        }

        auto env = GetJNIEnv();
        auto ktClass = env->GetObjectClass(m_ktRenderer);
        auto disposeDeps =
            env->GetMethodID(ktClass, "disposeDependencies", "()V");
        env->CallVoidMethod(m_ktRenderer, disposeDeps);
        env->DeleteGlobalRef(m_ktRenderer);
        releaseSurface(&m_surface);

        RiveLogD(TAG, "Worker thread: Deleting JNIRenderer.");
        delete this;
    });
}

void JNIRenderer::setSurface(SurfaceVariant surface)
{
    if (m_isDisposeScheduled)
    {
        RiveLogW(TAG, "setSurface() called after scheduleDisposal()");
        return;
    }

    // Acquire a reference to the surface
    // Released in the below worker thread lambda when the surface is replaced
    SurfaceVariant acquiredSurface = acquireSurface(surface);

    bool detachingSurface =
        std::holds_alternative<std::monostate>(acquiredSurface);
    if (detachingSurface)
    {
        RiveLogD(TAG, "Main thread: Surface destroy enqueued.");
    }

    m_worker->run([this, acquiredSurface, detachingSurface](
                      DrawableThreadState* threadState) mutable {
        RiveLogD(TAG, "Worker thread: Setting surface.");

        m_workerThreadID = std::this_thread::get_id();

        // Destroy the old surface
        SurfaceVariant oldSurface = m_surface;
        if (m_workerImpl)
        {
            m_workerImpl->destroy(threadState);
            m_workerImpl.reset();
        }
        // Release the above main thread reference to the old surface
        releaseSurface(&oldSurface);

        // Important: Only assign m_surface in this worker thread lambda to
        // preserve work item ordering of the "current surface".
        m_surface = acquiredSurface;

        // Create the new worker with the new surface
        if (hasSurface() && !isRecovering())
        {
            m_workerImpl = WorkerImpl::Make(m_surface,
                                            threadState,
                                            m_worker->rendererType());
        }
        if (detachingSurface)
        {
            RiveLogD(TAG, "Worker thread: Surface destroy complete.");
        }
    });
}

rive::Renderer* JNIRenderer::getRendererOnWorkerThread() const
{
    assert(std::this_thread::get_id() == m_workerThreadID);
    if (std::this_thread::get_id() != m_workerThreadID)
        return nullptr;
    if (!m_workerImpl)
        return nullptr;
    return m_workerImpl->renderer();
}

void JNIRenderer::start()
{
    RiveLogD(TAG, "Main thread: start()");
    if (m_isDisposeScheduled)
    {
        RiveLogW(TAG, "start() called after scheduleDisposal()");
        return;
    }
    m_worker->run([this](DrawableThreadState* threadState) {
        RiveLogD(TAG, "Worker thread: start()");
        if (!m_workerImpl)
        {
            RiveLogW(
                TAG,
                "Worker thread: start() called before setSurface() or while recovering.");
            return;
        }
        auto now = std::chrono::steady_clock::now();
        m_fpsLastFrameTime = now;
        m_workerImpl->start(m_ktRenderer, now);
    });
}

void JNIRenderer::stop()
{
    RiveLogD(TAG, "Main thread: stop()");
    if (m_isDisposeScheduled)
    {
        RiveLogW(TAG, "stop() called after scheduleDisposal()");
        return;
    }
    m_worker->run([this](DrawableThreadState* threadState) {
        RiveLogD(TAG, "Worker thread: stop()");
        if (!m_workerImpl)
        {
            RiveLogW(TAG, "Worker thread: stop() called before setSurface()");
            return;
        }
        m_workerImpl->stop();
    });
}

void JNIRenderer::doFrame()
{
    if (m_isDisposeScheduled)
    {
        RiveLogW(TAG, "doFrame() called after scheduleDisposal()");
        return;
    }

    if (m_numScheduledFrames >= kMaxScheduledFrames)
    {
        auto numScheduled =
            m_numScheduledFrames.load(std::memory_order_relaxed);
        RiveLogV(
            TAG,
            "Main thread: doFrame() called too many times in a row; skipping. (Scheduled: %d vs. Max: %d)",
            numScheduled,
            kMaxScheduledFrames);
        return;
    }

    m_worker->run([this](DrawableThreadState* threadState) {
        // RAII guard that balances `m_numScheduledFrames++` from the caller
        // path. The counter tracks queued frame work, not draw success, so it
        // must be decremented on every exit path (including early returns).
        struct FrameCountGuard
        {
            std::atomic_uint8_t& counter;
            ~FrameCountGuard() { counter--; }
        } frameCountGuard{m_numScheduledFrames};

        auto now = std::chrono::steady_clock::now();

        if (isRecovering())
        {
            // In recovering mode, normal draw/flush is suspended. Use this
            // frame tick to drive one recovery attempt instead.
            attemptRecovery(threadState, now);
            return;
        }

        if (!m_workerImpl)
        {
            RiveLogW(TAG,
                     "Worker thread: doFrame() called before setSurface()");
            return;
        }

        auto frameResult = m_workerImpl->doFrame(m_tracer.get(),
                                                 threadState,
                                                 m_ktRenderer,
                                                 now);
        if (!frameResult.eglResult.isSuccess())
        {
            RiveLogW(TAG,
                     "Frame aborted due to EGL error: %s",
                     frameResult.eglResult.summary().c_str());
            if (frameResult.eglResult.isFatal())
            {
                enterRecoveringState(threadState, frameResult.eglResult, now);
            }
            return;
        }

        if (frameResult.didDraw)
        {
            calculateFps(now);
        }
    });
    m_numScheduledFrames++;
}

void JNIRenderer::notifyRenderContextEvent(int eventType,
                                           const EGLResult& result) const
{
    auto env = GetJNIEnv();
    auto jRendererClass = GetObjectClass(env, m_ktRenderer);
    if (jRendererClass.get() == nullptr)
    {
        RiveLogE(TAG, "Failed to get renderer class for context event.");
        JNIExceptionHandler::ClearAndLogErrors(
            env,
            TAG,
            "notifyRenderContextEvent: GetObjectClass failed");
        return;
    }

    auto jCallbackMID = env->GetMethodID(jRendererClass.get(),
                                         "onNativeRenderContextEvent",
                                         "(IILjava/lang/String;)V");
    if (jCallbackMID == nullptr)
    {
        JNIExceptionHandler::ClearAndLogErrors(
            env,
            TAG,
            "notifyRenderContextEvent: jCallbackMID lookup failed");
        return;
    }

    auto jOperationName =
        MakeJString(env, EGLResult::FailureOperationName(result.operation));
    env->CallVoidMethod(m_ktRenderer,
                        jCallbackMID,
                        static_cast<jint>(eventType),
                        static_cast<jint>(result.error),
                        jOperationName.get());
    JNIExceptionHandler::ClearAndLogErrors(
        env,
        TAG,
        "notifyRenderContextEvent: jCallbackMID invocation failed");
}

void JNIRenderer::enterRecoveringState(
    DrawableThreadState* threadState,
    const EGLResult& failure,
    std::chrono::steady_clock::time_point now)
{
    if (isRecovering())
    {
        assert(false &&
               "enterRecoveringState called while already in recovering state");
        return;
    }

    RiveLogW(TAG,
             "Entering recovery state after fatal EGL error: %s",
             failure.summary().c_str());

    RecoveringState recovering;
    recovering.lastResult = failure;
    recovering.backoff = kRecoveryBackoffInitial;
    recovering.nextAttempt = now + recovering.backoff;

    m_renderLoopState = recovering;

    notifyRenderContextEvent(kContextEventLost, failure);

    if (m_workerImpl != nullptr)
    {
        m_workerImpl->stop();
        m_workerImpl->destroy(threadState);
        m_workerImpl.reset();
    }
}

bool JNIRenderer::attemptRecovery(DrawableThreadState* threadState,
                                  std::chrono::steady_clock::time_point now)
{
    auto* recoveryState = std::get_if<RecoveringState>(&m_renderLoopState);
    if (recoveryState == nullptr)
    {
        // Not in recovering state: treat as already healthy/no-op.
        return true;
    }
    if (now < recoveryState->nextAttempt)
    {
        // Still backing off; keep recovering state and try again on a later
        // frame tick.
        return false;
    }
    if (!hasSurface())
    {
        // No surface attached, so no recovery attempt right now.
        recoveryState->nextAttempt = now + recoveryState->backoff;
        return false;
    }

    EGLResult recoverResult = threadState->recoverAfterContextLoss();
    if (!recoverResult.isSuccess())
    {
        // EGL state rebuild failed. Stay in recovering mode and exponentially
        // back off to avoid hammering EGL/driver code every frame.
        recoveryState->lastResult = recoverResult;
        recoveryState->backoff =
            std::min(recoveryState->backoff * 2, kRecoveryBackoffMax);
        recoveryState->nextAttempt = now + recoveryState->backoff;
        RiveLogW(TAG,
                 "Recovery attempt failed: %s. Retrying in %lldms",
                 recoverResult.summary().c_str(),
                 static_cast<long long>(recoveryState->backoff.count()));
        return false;
    }

    m_workerImpl =
        WorkerImpl::Make(m_surface, threadState, m_worker->rendererType());
    if (m_workerImpl == nullptr)
    {
        // EGL recovered, but renderer resources failed to rebind. Keep
        // recovering mode active and retry with backoff until WorkerImpl is
        // recreated.
        recoveryState->backoff =
            std::min(recoveryState->backoff * 2, kRecoveryBackoffMax);
        recoveryState->nextAttempt = now + recoveryState->backoff;
        RiveLogW(
            TAG,
            "Recovery created EGL state but failed to recreate WorkerImpl. Retrying in %lldms",
            static_cast<long long>(recoveryState->backoff.count()));
        return false;
    }

    // Recovery succeeded end-to-end: resume renderer lifecycle, emit the
    // recovered event, and transition back to healthy rendering.
    m_workerImpl->start(m_ktRenderer, now);
    EGLResult recovered = EGLResult::Recovered();
    notifyRenderContextEvent(kContextEventRecovered, recovered);
    m_renderLoopState = HealthyState{};
    RiveLogI(TAG, "Successfully recovered EGL context and resumed rendering.");
    return true;
}

std::unique_ptr<ITracer> JNIRenderer::makeTracer(bool trace)
{
    if (!trace)
    {
        return std::make_unique<NoopTracer>();
    }

    bool traceAvailable = android_get_device_api_level() >= 23;
    if (traceAvailable)
    {
        return std::make_unique<Tracer>();
    }
    else
    {
        RiveLogE(TAG,
                 "JNIRenderer cannot enable tracing on API <23. API "
                 "version is %d.",
                 android_get_device_api_level());
        return std::make_unique<NoopTracer>();
    }
}

/**
 * Calculate FPS over an average of 10 samples
 */
void JNIRenderer::calculateFps(std::chrono::steady_clock::time_point frameTime)
{
    m_tracer->beginSection("calculateFps()");
    static constexpr int FPS_SAMPLES = 10;

    float elapsed =
        std::chrono::duration<float>(frameTime - m_fpsLastFrameTime).count();

    m_fpsSum += 1.0f / elapsed;
    m_fpsCount++;
    if (m_fpsCount == FPS_SAMPLES)
    {
        m_averageFps = m_fpsSum / static_cast<float>(FPS_SAMPLES);
        m_fpsSum = 0;
        m_fpsCount = 0;
    }
    m_fpsLastFrameTime = frameTime;
    m_tracer->endSection();
}
} // namespace rive_android

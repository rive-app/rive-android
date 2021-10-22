#include <cmath>
#include <string>

#include <jni.h>
#include <android/native_window_jni.h>

#include "models/jni_renderer_skia.hpp"

#include "helpers/general.hpp"
#include "helpers/Settings.h"

#include "swappy/swappyGL.h"
#include "swappy/swappyGL_extra.h"

using std::chrono::nanoseconds;
using namespace samples;
using namespace rive_android;

namespace
{
  std::string to_string(jstring jstr, JNIEnv *env)
  {
    const char *utf = env->GetStringUTFChars(jstr, nullptr);
    std::string str(utf);
    env->ReleaseStringUTFChars(jstr, utf);
    return str;
  }

} // anonymous namespace

#ifdef __cplusplus
extern "C"
{
#endif
  void startFrameCallback(void *, int, int64_t)
  {
  }

  void postWaitCallback(void *, int64_t cpu, int64_t gpu)
  {
    // TODO:
    // auto renderer = Renderer::getInstance();
    // double frameTime = std::max(cpu, gpu);
    // renderer->frameTimeStats().add(frameTime);
  }

  void swapIntervalChangedCallback(void *)
  {
    uint64_t swap_ns = SwappyGL_getSwapIntervalNS();
    LOGI("Swappy changed swap interval to %.2fms", swap_ns / 1e6f);
  }

  /** Test using an external thread provider */
  static int threadStart(SwappyThreadId *thread_id, void *(*thread_func)(void *), void *user_data)
  {
    return ThreadManager::Instance().Start(thread_id, thread_func, user_data);
  }
  static void threadJoin(SwappyThreadId thread_id)
  {
    ThreadManager::Instance().Join(thread_id);
  }
  static bool threadJoinable(SwappyThreadId thread_id)
  {
    return ThreadManager::Instance().Joinable(thread_id);
  }
  static SwappyThreadFunctions sThreadFunctions = {
      threadStart, threadJoin, threadJoinable};

  JNIEXPORT void JNICALL
  Java_app_rive_runtime_example_SwappyView_nInit(
      JNIEnv *env, jobject swappyView,
      jobject activity, jlong initialSwapIntervalNS)
  {
    // Should never happen
    if (Swappy_version() != SWAPPY_PACKED_VERSION)
    {
      LOGE("Inconsistent Swappy versions");
    }

    Swappy_setThreadFunctions(&sThreadFunctions);
    SwappyGL_init(env, activity);
    SwappyGL_setSwapIntervalNS(initialSwapIntervalNS);

    SwappyTracer tracers;
    tracers.preWait = nullptr;
    tracers.postWait = postWaitCallback;
    tracers.preSwapBuffers = nullptr;
    tracers.postSwapBuffers = nullptr;
    tracers.startFrame = startFrameCallback;
    tracers.userData = nullptr;
    tracers.swapIntervalChanged = swapIntervalChangedCallback;

    SwappyGL_injectTracer(&tracers);
  }

  JNIEXPORT void JNICALL
  Java_app_rive_runtime_example_SwappyView_nSetViewport(
      JNIEnv *env, jobject,
      jlong rendererAddr, jint width, jint height)
  {
    auto skiaRenderer = (JNIRendererSkia *)rendererAddr;
    skiaRenderer->setViewport(width, height);
  }

  JNIEXPORT void JNICALL
  Java_app_rive_runtime_example_SwappyView_nClearSurface(JNIEnv *, jobject)
  {
    // TODO:
    // Renderer::getInstance()->setWindow(nullptr, 0, 0);
  }

  JNIEXPORT void JNICALL
  Java_app_rive_runtime_example_SwappyView_nSetSurface(
      JNIEnv *env, jobject,
      jobject surface, jlong rendererRef)
  {
    ANativeWindow *surfaceWindow = ANativeWindow_fromSurface(env, surface);
    auto skiaRenderer = (JNIRendererSkia *)rendererRef;
    skiaRenderer->setWindow(surfaceWindow);
    // Now we can initialize the Skia GL Context
    skiaRenderer->initialize();
    skiaRenderer->startFrame();
    // Clear stats when we come back from the settings activity.
    SwappyGL_enableStats(false);
    // SwappyGL_enableStats(true);
  }

  JNIEXPORT void JNICALL
  Java_app_rive_runtime_example_SwappyView_nStop(JNIEnv *, jobject)
  {
    // TODO:
    // Renderer::getInstance()->stop();
  }

  JNIEXPORT void JNICALL
  Java_app_rive_runtime_example_SwappyView_nSetPreference(
      JNIEnv *env, jobject,
      jstring key, jstring value)
  {
    Settings::getInstance()->setPreference(to_string(key, env), to_string(value, env));
  }

  JNIEXPORT void JNICALL
  Java_app_rive_runtime_example_SwappyView_nSetAutoSwapInterval(
      JNIEnv *env, jobject,
      jboolean enabled)
  {
    SwappyGL_setAutoSwapInterval(enabled);
  }

  JNIEXPORT void JNICALL
  Java_app_rive_runtime_example_SwappyView_nSetAutoPipeline(
      JNIEnv *env, jobject,
      jboolean enabled)
  {
    SwappyGL_setAutoPipelineMode(enabled);
  }

  JNIEXPORT float JNICALL
  Java_app_rive_runtime_example_SwappyView_nGetAverageFps(JNIEnv *, jobject)
  {
    // TODO:
    // return Renderer::getInstance()->getAverageFps();
    return 0.0f;
  }

  JNIEXPORT float JNICALL
  Java_app_rive_runtime_example_SwappyView_nGetRefreshPeriodNS(JNIEnv *, jobject)
  {
    return SwappyGL_getRefreshPeriodNanos();
  }

  JNIEXPORT float JNICALL
  Java_app_rive_runtime_example_SwappyView_nGetSwapIntervalNS(JNIEnv *, jobject)
  {
    return SwappyGL_getSwapIntervalNS();
  }

  JNIEXPORT float JNICALL
  Java_app_rive_runtime_example_SwappyView_nGetPipelineFrameTimeNS(JNIEnv *, jobject)
  {
    // TODO:
    // return Renderer::getInstance()->frameTimeStats().mean();
    return 0.0f;
  }

  JNIEXPORT float JNICALL
  Java_app_rive_runtime_example_SwappyView_nGetPipelineFrameTimeStdDevNS(JNIEnv *, jobject)
  {
    // TODO:
    // return sqrt(Renderer::getInstance()->frameTimeStats().var());
    return 0.0f;
  }

  JNIEXPORT void JNICALL
  Java_app_rive_runtime_example_SwappyView_nSetWorkload(
      JNIEnv *, jobject,
      jint load)
  {
    // TODO: explore this
    // It's an interesting heuristic for segmenting based on workload
    // Renderer::getInstance()->setWorkload(load);
  }

  JNIEXPORT void JNICALL
  Java_app_rive_runtime_example_SwappyView_nSetBufferStuffingFixWait(
      JNIEnv *, jobject,
      jint n_frames)
  {
    SwappyGL_setBufferStuffingFixWait(n_frames);
  }

  JNIEXPORT void JNICALL
  Java_app_rive_runtime_example_SwappyView_nEnableSwappy(
      JNIEnv *, jobject,
      jboolean enabled)
  {
    // TODO:
    // Renderer::getInstance()->setSwappyEnabled(enabled);
  }

  JNIEXPORT int JNICALL
  Java_app_rive_runtime_example_SwappyView_nGetSwappyStats(
      JNIEnv *, jobject,
      jint stat,
      jint bin)
  {
    static bool enabled = false;
    if (!enabled)
    {
      // SwappyGL_enableStats(true);
      enabled = true;
    }

    // stats are read one by one, query once per stat
    static SwappyStats stats;
    static int stat_idx = -1;

    if (stat_idx != stat)
    {
      SwappyGL_getStats(&stats);
      stat_idx = stat;
    }

    int value = 0;

    if (stats.totalFrames)
    {
      switch (stat)
      {
      case 0:
        value = stats.idleFrames[bin];
        break;
      case 1:
        value = stats.lateFrames[bin];
        break;
      case 2:
        value = stats.offsetFromPreviousFrame[bin];
        break;
      case 3:
        value = stats.latencyFrames[bin];
        break;
      default:
        return stats.totalFrames;
      }
      value = std::round(value * 100.0f / stats.totalFrames);
    }

    return value;
  }

  JNIEXPORT jlong JNICALL
  Java_app_rive_runtime_example_SwappyView_nGetSwappyVersion(JNIEnv *, jobject)
  {
    return SWAPPY_MAJOR_VERSION * 10000L + SWAPPY_MINOR_VERSION * 100L + SWAPPY_BUGFIX_VERSION;
  }

#ifdef __cplusplus
}
#endif

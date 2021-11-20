#include <cmath>
#include <string>

#include <jni.h>

#include "models/jni_renderer_skia.hpp"

#include "helpers/general.hpp"
#include "helpers/Settings.h"

#include "swappy/swappyGL.h"
#include "swappy/swappyGL_extra.h"

using std::chrono::nanoseconds;
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
  Java_app_rive_runtime_kotlin_renderers_RendererMetrics_nInit(
      JNIEnv *env, jobject view,
      jobject activity, jlong initialSwapIntervalNS)
  {
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
  Java_app_rive_runtime_kotlin_RiveSurfaceHolder_cppInit(
      JNIEnv *env, jobject view,
      jobject activity, jlong initialSwapIntervalNS)
  {
    // Source: https://android.googlesource.com/platform/frameworks/opt/gamesdk/+/refs/heads/master/samples/bouncyball/app/src/main/cpp/Orbit.cpp#85
    // Should never happen
    if (Swappy_version() != SWAPPY_PACKED_VERSION)
    {
      LOGE("Inconsistent Swappy versions");
    }

    Swappy_setThreadFunctions(&sThreadFunctions);
    SwappyGL_init(env, activity);
    SwappyGL_setSwapIntervalNS(initialSwapIntervalNS);
  }

  JNIEXPORT void JNICALL
  Java_app_rive_runtime_kotlin_RiveSurfaceHolder_cppSetPreference(
      JNIEnv *env, jobject,
      jstring key, jstring value)
  {
    Settings::getInstance()->setPreference(to_string(key, env), to_string(value, env));
  }

  JNIEXPORT void JNICALL
  Java_app_rive_runtime_kotlin_RiveSurfaceHolder_cppSetAutoSwapInterval(
      JNIEnv *env, jobject,
      jboolean enabled)
  {
    SwappyGL_setAutoSwapInterval(enabled);
  }

  JNIEXPORT void JNICALL
  Java_app_rive_runtime_kotlin_RiveSurfaceHolder_cppSetAutoPipeline(
      JNIEnv *env, jobject,
      jboolean enabled)
  {
    SwappyGL_setAutoPipelineMode(enabled);
  }

  JNIEXPORT float JNICALL
  Java_app_rive_runtime_kotlin_RiveSurfaceHolder_cppGetAverageFps(
      JNIEnv *, jobject,
      jlong rendererAddr)
  {
    JNIRendererSkia *renderer = (JNIRendererSkia *)rendererAddr;
    return renderer->averageFps();
  }

  JNIEXPORT float JNICALL
  Java_app_rive_runtime_kotlin_RiveSurfaceHolder_cppGetRefreshPeriodNS(JNIEnv *, jobject)
  {
    return SwappyGL_getRefreshPeriodNanos();
  }

  JNIEXPORT float JNICALL
  Java_app_rive_runtime_kotlin_RiveSurfaceHolder_cppGetSwapIntervalNS(JNIEnv *, jobject)
  {
    return SwappyGL_getSwapIntervalNS();
  }

  JNIEXPORT float JNICALL
  Java_app_rive_runtime_kotlin_RiveSurfaceHolder_cppGetPipelineFrameTimeNS(JNIEnv *, jobject)
  {
    // TODO:
    // return Renderer::getInstance()->frameTimeStats().mean();
    return 0.0f;
  }

  JNIEXPORT float JNICALL
  Java_app_rive_runtime_kotlin_RiveSurfaceHolder_cppGetPipelineFrameTimeStdDevNS(JNIEnv *, jobject)
  {
    // TODO:
    // return sqrt(Renderer::getInstance()->frameTimeStats().var());
    return 0.0f;
  }

  JNIEXPORT void JNICALL
  Java_app_rive_runtime_kotlin_RiveSurfaceHolder_cppSetWorkload(
      JNIEnv *, jobject,
      jint load)
  {
    // TODO: explore this
    // It's an interesting heuristic for segmenting based on workload
    // Renderer::getInstance()->setWorkload(load);
  }

  JNIEXPORT void JNICALL
  Java_app_rive_runtime_kotlin_RiveSurfaceHolder_cppSetBufferStuffingFixWait(
      JNIEnv *, jobject,
      jint n_frames)
  {
    SwappyGL_setBufferStuffingFixWait(n_frames);
  }

  JNIEXPORT void JNICALL
  Java_app_rive_runtime_kotlin_RiveSurfaceHolder_cppEnableSwappy(
      JNIEnv *, jobject,
      jboolean enabled)
  {
    // TODO:
    // Renderer::getInstance()->setSwappyEnabled(enabled);
  }

  JNIEXPORT int JNICALL
  Java_app_rive_runtime_kotlin_RiveSurfaceHolder_cppGetSwappyStats(
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

#include <string>
#include <jni.h>

#include "models/jni_renderer_skia.hpp"

using namespace rive_android;

#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT float JNICALL Java_app_rive_runtime_kotlin_RiveTextureView_cppGetAverageFps(
    JNIEnv*, jobject, jlong rendererAddr) {
    return reinterpret_cast<JNIRendererSkia*>(rendererAddr)->averageFps();
}

/** TODO: explore these helpers, might be useful for a few metrics

    std::string to_string(jstring jstr, JNIEnv* env)
    {
        const char* utf = env->GetStringUTFChars(jstr, nullptr);
        std::string str(utf);
        env->ReleaseStringUTFChars(jstr, utf);
        return str;
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_RiveTextureView_cppSetPreference(JNIEnv* env,
                                                                  jobject,
                                                                  jstring key,
                                                                  jstring value)
    {
        Settings::getInstance()->setPreference(to_string(key, env),
                                               to_string(value, env));
    }

    JNIEXPORT float JNICALL
    Java_app_rive_runtime_kotlin_RiveTextureView_cppGetPipelineFrameTimeNS(
        JNIEnv*, jobject)
    {
        return Renderer::getInstance()->frameTimeStats().mean();
    }

    JNIEXPORT float JNICALL
    Java_app_rive_runtime_kotlin_RiveTextureView_cppGetPipelineFrameTimeStdDevNS(
        JNIEnv*, jobject)
    {
        return sqrt(Renderer::getInstance()->frameTimeStats().var());
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_RiveTextureView_cppSetWorkload(JNIEnv*,
                                                                jobject,
                                                                jint load)
    {
        // It's an interesting heuristic for segmenting based on workload
        // Renderer::getInstance()->setWorkload(load);
    }
*/
#ifdef __cplusplus
}
#endif

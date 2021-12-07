#include <cmath>
#include <string>

#include <jni.h>

#include "models/jni_renderer_skia.hpp"

#include "helpers/general.hpp"
#include "helpers/settings.hpp"

using std::chrono::nanoseconds;
using namespace rive_android;

namespace
{
	std::string to_string(jstring jstr, JNIEnv* env)
	{
		const char* utf = env->GetStringUTFChars(jstr, nullptr);
		std::string str(utf);
		env->ReleaseStringUTFChars(jstr, utf);
		return str;
	}

} // anonymous namespace

#ifdef __cplusplus
extern "C"
{
#endif
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
	Java_app_rive_runtime_kotlin_RiveTextureView_cppGetAverageFps(
	    JNIEnv*, jobject, jlong rendererAddr)
	{
		return reinterpret_cast<JNIRendererSkia*>(rendererAddr)->averageFps();
	}

	JNIEXPORT float JNICALL
	Java_app_rive_runtime_kotlin_RiveTextureView_cppGetPipelineFrameTimeNS(
	    JNIEnv*, jobject)
	{
		// TODO:
		// return Renderer::getInstance()->frameTimeStats().mean();
		return 0.0f;
	}

	JNIEXPORT float JNICALL
	Java_app_rive_runtime_kotlin_RiveTextureView_cppGetPipelineFrameTimeStdDevNS(
	    JNIEnv*, jobject)
	{
		// TODO:
		// return sqrt(Renderer::getInstance()->frameTimeStats().var());
		return 0.0f;
	}

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_RiveTextureView_cppSetWorkload(JNIEnv*,
	                                                            jobject,
	                                                            jint load)
	{
		// TODO: explore this
		// It's an interesting heuristic for segmenting based on workload
		// Renderer::getInstance()->setWorkload(load);
	}
#ifdef __cplusplus
}
#endif

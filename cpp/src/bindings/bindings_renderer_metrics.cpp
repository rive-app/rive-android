#include <jni.h>

#include "helpers/general.hpp"

#include "swappy/swappyGL.h"
#include "swappy/swappyGL_extra.h"

void startFrameCallback(void*, int, int64_t) {}

void postWaitCallback(void*, int64_t cpu, int64_t gpu)
{
	// TODO:
	// auto renderer = Renderer::getInstance();
	// double frameTime = std::max(cpu, gpu);
	// renderer->frameTimeStats().add(frameTime);
}

void swapIntervalChangedCallback(void*)
{
	uint64_t swap_ns = SwappyGL_getSwapIntervalNS();
	LOGI("Swappy changed swap interval to %.2fms", swap_ns / 1e6f);
}

JNIEXPORT void JNICALL
Java_app_rive_runtime_kotlin_renderers_RendererMetrics_cppInitTracer(
    JNIEnv* env, jobject view, jobject activity, jlong initialSwapIntervalNS)
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
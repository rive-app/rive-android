#include <jni.h>
#include <android/native_window_jni.h>

#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "helpers/worker_thread.hpp"

#include "bindings/bindings_renderer_skia.hpp"
#include "models/jni_renderer_skia.hpp"
#include "rive/layout.hpp"
#include "rive/artboard.hpp"

#ifdef __cplusplus
extern "C"
{
#endif

	using namespace rive_android;

	// Skia Renderer
	JNIEXPORT jlong JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererSkia_constructor(
	    JNIEnv* env, jobject ktRendererSkia, jboolean trace)
	{
		::JNIRendererSkia* renderer =
		    new JNIRendererSkia(ktRendererSkia, trace);
		return (jlong)renderer;
	}
	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererSkia_cleanupJNI(
	    JNIEnv*, jobject, jlong rendererRef)
	{
		auto renderer = reinterpret_cast<JNIRendererSkia*>(rendererRef);
		auto thread = renderer->workerThread();
		ThreadManager::getInstance()->releaseThread(
		    thread,
		    [=]()
		    {
			    delete renderer;
		    });
	}

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppStop(
	    JNIEnv*, jobject, jlong rendererRef)
	{
		reinterpret_cast<JNIRendererSkia*>(rendererRef)->stop();
	}

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppStart(
	    JNIEnv*, jobject, jlong rendererRef)
	{
		reinterpret_cast<JNIRendererSkia*>(rendererRef)->start();
	}

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppDoFrame(
	    JNIEnv*, jobject, jlong rendererRef, jlong frameTimeNs)
	{
		reinterpret_cast<JNIRendererSkia*>(rendererRef)->doFrame(frameTimeNs);
	}

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppSetSurface(
	    JNIEnv* env, jobject, jobject surface, jlong rendererRef)
	{
		ANativeWindow* surfaceWindow = ANativeWindow_fromSurface(env, surface);
		reinterpret_cast<JNIRendererSkia*>(rendererRef)
		    ->setWindow(surfaceWindow);
	}

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppClearSurface(
	    JNIEnv*, jobject, jlong rendererRef)
	{
		reinterpret_cast<JNIRendererSkia*>(rendererRef)->setWindow(nullptr);
	}

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppSave(
	    JNIEnv*, jobject, jlong rendererRef)
	{
		reinterpret_cast<JNIRendererSkia*>(rendererRef)->skRenderer()->save();
	}

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppRestore(
	    JNIEnv*, jobject, jlong rendererRef)
	{
		reinterpret_cast<JNIRendererSkia*>(rendererRef)
		    ->skRenderer()
		    ->restore();
	}

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppAlign(
	    JNIEnv* env,
	    jobject thisObj,
	    jlong ref,
	    jobject ktFit,
	    jobject ktAlignment,
	    jlong targetBoundsRef,
	    jlong sourceBoundsRef)
	{
		JNIRendererSkia* jniWrapper = (JNIRendererSkia*)ref;
		rive::Fit fit = getFit(env, ktFit);
		rive::Alignment alignment = getAlignment(env, ktAlignment);
		rive::AABB* targetBounds = (rive::AABB*)targetBoundsRef;
		rive::AABB* sourceBounds = (rive::AABB*)sourceBoundsRef;
		jniWrapper->skRenderer()->align(
		    fit, alignment, *targetBounds, *sourceBounds);
	}

	JNIEXPORT jint JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppWidth(
	    JNIEnv*, jobject, jlong rendererRef)
	{
		return reinterpret_cast<JNIRendererSkia*>(rendererRef)->width();
	}

	JNIEXPORT jint JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppHeight(
	    JNIEnv*, jobject, jlong rendererRef)
	{
		return reinterpret_cast<JNIRendererSkia*>(rendererRef)->height();
	}

	JNIEXPORT jfloat JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppAvgFps(
	    JNIEnv*, jobject, jlong rendererRef)
	{
		return reinterpret_cast<JNIRendererSkia*>(rendererRef)->averageFps();
	}

#ifdef __cplusplus
}
#endif

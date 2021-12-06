#ifndef _RIVE_ANDROID_BINDINGS_RENDERER_SKIA_HPP_
#define _RIVE_ANDROID_BINDINGS_RENDERER_SKIA_HPP_

#include <jni.h>
#include <cassert>
#include "models/jni_renderer_skia.hpp"

#ifdef __cplusplus
extern "C"
{
#endif
	// Our renderer abstractions creates paints and paths for the renderer,
	// however the abstraction model is implemented with a simple global
	// function for makeRenderPaint and makeRenderPath. See the rive-cpp
	// low_level_rendering branch for how the viewer handles this by expecting
	// one global LowLevelRenderer and then virtualizing the methods to create
	// those objects.
	//
	// A long term cleaner solution is requiring file graphics-init by passing
	// the renderer reference. Perhaps the time has come to explore that to
	// avoid things like this. It does mean that files cannot be loaded without
	// a renderer, or at least will require initialization with a no-op renderer
	// (probably ok?).
	rive_android::IJNIRenderer* g_JNIRenderer = nullptr;

	// Skia Renderer
	JNIEXPORT jlong JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererSkia_constructor(
	    JNIEnv* env, jobject ktRendererSkia, jboolean trace);

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererSkia_cleanupJNI(
	    JNIEnv*, jobject, jlong rendererRef);

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppStop(
	    JNIEnv*, jobject, jlong rendererRef);

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppStart(
	    JNIEnv*, jobject, jlong rendererRef);

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppSetSurface(
	    JNIEnv* env, jobject, jobject surface, jlong rendererRef);

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppClearSurface(
	    JNIEnv*, jobject, jlong rendererRef);

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppSave(
	    JNIEnv*, jobject, jlong rendererRef);

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppRestore(
	    JNIEnv*, jobject, jlong rendererRef);

	JNIEXPORT jint JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppWidth(
	    JNIEnv*, jobject, jlong rendererRef);

	JNIEXPORT jint JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppHeight(
	    JNIEnv*, jobject, jlong rendererRef);
#ifdef __cplusplus
}
#endif

// end #ifndef _RIVE_ANDROID_BINDINGS_RENDERER_SKIA_HPP_
#endif
#ifndef _RIVE_ANDROID_BINDINGS_RENDERER_SKIA_HPP_
#define _RIVE_ANDROID_BINDINGS_RENDERER_SKIA_HPP_

#include <jni.h>
#include <cassert>
#include "models/jni_renderer_skia.hpp"

#ifdef __cplusplus
extern "C"
{
#endif

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
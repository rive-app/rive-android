#ifndef _RIVE_ANDROID_BINDINGS_RENDERER_SKIA_HPP_
#define _RIVE_ANDROID_BINDINGS_RENDERER_SKIA_HPP_

#include <jni.h>
#include <cassert>
#include "models/jni_renderer_skia.hpp"

#ifdef __cplusplus
extern "C" {
#endif

// Skia Renderer
JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_renderers_RendererSkia_constructor(JNIEnv*,
                                                                                        jobject,
                                                                                        jboolean);

JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_renderers_RendererSkia_cleanupJNI(JNIEnv*,
                                                                                      jobject,
                                                                                      jlong);

JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppStop(JNIEnv*,
                                                                                   jobject,
                                                                                   jlong);

JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppStart(JNIEnv*,
                                                                                    jobject,
                                                                                    jlong);

JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppSetSurface(JNIEnv*,
                                                                                         jobject,
                                                                                         jobject,
                                                                                         jlong);

JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppClearSurface(JNIEnv*,
                                                                                           jobject,
                                                                                           jlong);

JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppSave(JNIEnv*,
                                                                                   jobject,
                                                                                   jlong);

JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppRestore(JNIEnv*,
                                                                                      jobject,
                                                                                      jlong);

JNIEXPORT jint JNICALL Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppWidth(JNIEnv*,
                                                                                    jobject,
                                                                                    jlong);

JNIEXPORT jint JNICALL Java_app_rive_runtime_kotlin_renderers_RendererSkia_cppHeight(JNIEnv*,
                                                                                     jobject,
                                                                                     jlong);
#ifdef __cplusplus
}
#endif

// end #ifndef _RIVE_ANDROID_BINDINGS_RENDERER_SKIA_HPP_
#endif
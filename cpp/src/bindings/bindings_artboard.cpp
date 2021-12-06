#include "jni_refs.hpp"
#include "skia_renderer.hpp"
#include "helpers/general.hpp"
#include "models/i_jni_renderer.hpp"
#include "models/jni_renderer_skia.hpp"
#include "rive/artboard.hpp"
#include "rive/animation/linear_animation_instance.hpp"
#include <jni.h>
#include <stdio.h>

#ifdef __cplusplus
extern "C"
{
#endif

	using namespace rive_android;

	// ARTBOARD

	JNIEXPORT jstring JNICALL
	Java_app_rive_runtime_kotlin_core_Artboard_cppName(JNIEnv* env,
	                                                   jobject thisObj,
	                                                   jlong ref)
	{
		rive::Artboard* artboard = (rive::Artboard*)ref;

		return env->NewStringUTF(artboard->name().c_str());
	}

	JNIEXPORT jlong JNICALL
	Java_app_rive_runtime_kotlin_core_Artboard_cppFirstAnimation(
	    JNIEnv* env, jobject thisObj, jlong ref)
	{
		rive::Artboard* artboard = (rive::Artboard*)ref;

		return (jlong)artboard->firstAnimation();
	}

	JNIEXPORT jlong JNICALL
	Java_app_rive_runtime_kotlin_core_Artboard_cppFirstStateMachine(
	    JNIEnv* env, jobject thisObj, jlong ref)
	{
		rive::Artboard* artboard = (rive::Artboard*)ref;

		return (jlong)artboard->firstStateMachine();
	}

	JNIEXPORT jlong JNICALL
	Java_app_rive_runtime_kotlin_core_Artboard_cppAnimationByIndex(
	    JNIEnv* env, jobject thisObj, jlong ref, jint index)
	{
		rive::Artboard* artboard = (rive::Artboard*)ref;
		return (jlong)artboard->animation(index);
	}

	JNIEXPORT jlong JNICALL
	Java_app_rive_runtime_kotlin_core_Artboard_cppAnimationByName(
	    JNIEnv* env, jobject thisObj, jlong ref, jstring name)
	{
		rive::Artboard* artboard = (rive::Artboard*)ref;

		return (jlong)artboard->animation(jstring2string(env, name));
	}

	JNIEXPORT jint JNICALL
	Java_app_rive_runtime_kotlin_core_Artboard_cppAnimationCount(
	    JNIEnv* env, jobject thisObj, jlong ref)
	{
		rive::Artboard* artboard = (rive::Artboard*)ref;

		return (jint)artboard->animationCount();
	}

	JNIEXPORT jlong JNICALL
	Java_app_rive_runtime_kotlin_core_Artboard_cppStateMachineByIndex(
	    JNIEnv* env, jobject thisObj, jlong ref, jint index)
	{
		rive::Artboard* artboard = (rive::Artboard*)ref;
		return (jlong)artboard->stateMachine(index);
	}

	JNIEXPORT jlong JNICALL
	Java_app_rive_runtime_kotlin_core_Artboard_cppStateMachineByName(
	    JNIEnv* env, jobject thisObj, jlong ref, jstring name)
	{
		rive::Artboard* artboard = (rive::Artboard*)ref;

		return (jlong)artboard->stateMachine(jstring2string(env, name));
	}

	JNIEXPORT jint JNICALL
	Java_app_rive_runtime_kotlin_core_Artboard_cppStateMachineCount(
	    JNIEnv* env, jobject thisObj, jlong ref)
	{
		rive::Artboard* artboard = (rive::Artboard*)ref;

		return (jint)artboard->stateMachineCount();
	}

	JNIEXPORT jboolean JNICALL
	Java_app_rive_runtime_kotlin_core_Artboard_cppAdvance(JNIEnv* env,
	                                                      jobject thisObj,
	                                                      jlong ref,
	                                                      jfloat elapsedTime)
	{
		rive::Artboard* artboard = (rive::Artboard*)ref;
		return artboard->advance(elapsedTime);
	}

	JNIEXPORT jlong JNICALL
	Java_app_rive_runtime_kotlin_core_Artboard_cppBounds(JNIEnv* env,
	                                                     jobject thisObj,
	                                                     jlong ref)
	{
		rive::Artboard* artboard = (rive::Artboard*)ref;
		// TODO: garbage collection?
		auto bounds = new rive::AABB(artboard->bounds());
		return (jlong)bounds;
	}

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_core_Artboard_cppDrawSkia(JNIEnv* env,
	                                                       jobject,
	                                                       jlong artboardRef,
	                                                       jlong rendererRef)
	{
		// TODO: consolidate this to work with an abstracted JNI Renderer.
		rive::Artboard* artboard = (rive::Artboard*)artboardRef;
		JNIRendererSkia* jniWrapper = (JNIRendererSkia*)rendererRef;
		rive::SkiaRenderer* renderer = jniWrapper->skRenderer();
		artboard->draw(renderer);
	}

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_core_Artboard_cppDrawSkiaAligned(
	    JNIEnv* env,
	    jobject,
	    jlong artboardRef,
	    jlong rendererRef,
	    jobject ktFit,
	    jobject ktAlignment)
	{
		// TODO: consolidate this to work with an abstracted JNI Renderer.
		rive::Artboard* artboard = (rive::Artboard*)artboardRef;
		JNIRendererSkia* jniWrapper = (JNIRendererSkia*)rendererRef;
		rive::SkiaRenderer* renderer = jniWrapper->skRenderer();

		rive::Fit fit = getFit(env, ktFit);
		rive::Alignment alignment = getAlignment(env, ktAlignment);

		renderer->save();
		renderer->align(
		    fit,
		    alignment,
		    rive::AABB(0, 0, jniWrapper->width(), jniWrapper->height()),
		    artboard->bounds());
		artboard->draw(renderer);
		renderer->restore();
	}

	JNIEXPORT jboolean JNICALL
	Java_app_rive_runtime_kotlin_core_Artboard_cppIsInstance(JNIEnv* env,
	                                                         jobject thisObj,
	                                                         jlong ref)
	{
		rive::Artboard* artboard = (rive::Artboard*)ref;

		return artboard->isInstance();
	}

	JNIEXPORT jlong JNICALL
	Java_app_rive_runtime_kotlin_core_Artboard_cppInstance(JNIEnv* env,
	                                                       jobject thisObj,
	                                                       jlong ref)
	{
		rive::Artboard* artboard = (rive::Artboard*)ref;

		return (jlong)artboard->instance();
	}

	JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppDelete(
	    JNIEnv* env, jobject thisObj, jlong ref)
	{
		rive::Artboard* artboard = (rive::Artboard*)ref;
		delete artboard;
	}

#ifdef __cplusplus
}
#endif

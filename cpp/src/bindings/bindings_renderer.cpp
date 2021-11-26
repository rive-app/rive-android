#include <jni.h>
#include <android/native_window_jni.h>

#include "jni_refs.hpp"
#include "helpers/general.hpp"

#include "bindings/bindings_renderer.hpp"
#include "models/jni_renderer_gl.hpp"
#include "models/jni_renderer_skia.hpp"
#include "rive/layout.hpp"
#include "rive/artboard.hpp"

#ifdef __cplusplus
extern "C"
{
#endif

	using namespace rive_android;

	// RENDERER
	JNIEXPORT jlong JNICALL
	Java_app_rive_runtime_kotlin_renderers_Renderer_constructor(
	    JNIEnv* env, jobject thisObj, jboolean antialias)
	{
		auto renderer = new ::JNIRenderer();
		g_JNIRenderer = renderer;
		::JNIRenderer::antialias = (bool)antialias;
		renderer->jRendererObject = getJNIEnv()->NewGlobalRef(thisObj);

		return (jlong)renderer;
	}

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_renderers_Renderer_cleanupJNI(
	    JNIEnv* env, jobject thisObj, jlong rendererRef)
	{
		::JNIRenderer* renderer = (::JNIRenderer*)rendererRef;
		delete renderer;
	}

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_renderers_Renderer_cppAlign(
	    JNIEnv* env,
	    jobject thisObj,
	    jlong ref,
	    jobject jfit,
	    jobject jalignment,
	    jlong targetBoundsRef,
	    jlong sourceBoundsRef)
	{
		::JNIRenderer* renderer = (::JNIRenderer*)ref;

		auto fit = ::getFit(env, jfit);
		auto alignment = ::getAlignment(env, jalignment);
		rive::AABB* targetBounds = (rive::AABB*)targetBoundsRef;
		rive::AABB* sourceBounds = (rive::AABB*)sourceBoundsRef;
		renderer->align(fit, alignment, *targetBounds, *sourceBounds);
	}

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_renderers_Renderer_cppDraw(JNIEnv* env,
	                                                        jobject thisObj,
	                                                        jlong artboardRef,
	                                                        jlong rendererRef)
	{
		auto artboard = (rive::Artboard*)artboardRef;
		auto renderer = (::JNIRenderer*)rendererRef;
		artboard->draw(renderer);
	}

	JNIEXPORT jlong JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererOpenGL_constructor(
	    JNIEnv* env, jobject thisObj)
	{
		auto renderer = new ::JNIRendererGL();
		g_JNIRenderer = renderer;
		renderer->jRendererObject = getJNIEnv()->NewGlobalRef(thisObj);
		return (jlong)renderer;
	}

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererOpenGL_initializeGL(
	    JNIEnv* env, jobject thisObj, jlong rendererRef)
	{
		((::JNIRendererGL*)rendererRef)->initialize();
	}

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererOpenGL_startFrame(
	    JNIEnv* env, jobject thisObj, jlong rendererRef)
	{
		((::JNIRendererGL*)rendererRef)->startFrame();
	}

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererOpenGL_setViewport(
	    JNIEnv* env,
	    jobject thisObj,
	    jlong rendererRef,
	    jint width,
	    jint height)
	{
		::JNIRendererGL* renderer = (::JNIRendererGL*)rendererRef;
		// We should probably pass width/height directly to drawArtboard so you
		// can target different areas at draw time.
		renderer->width = width;
		renderer->height = height;
		float projection[16] = {0.0f};
		renderer->orthographicProjection(
		    projection, 0.0f, width, height, 0.0f, 0.0f, 1.0f);
		renderer->modelViewProjection(projection);
	}

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererOpenGL_cppDraw(
	    JNIEnv* env, jobject thisObj, jlong artboardRef, jlong rendererRef)
	{
		rive::Artboard* artboard = (rive::Artboard*)artboardRef;
		auto renderer = (::JNIRendererGL*)rendererRef;
		renderer->save();
		renderer->align(rive::Fit::contain,
		                rive::Alignment::center,
		                rive::AABB(0, 0, renderer->width, renderer->height),
		                artboard->bounds());
		artboard->draw(renderer);
		renderer->restore();
	}

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererOpenGL_cleanupJNI(
	    JNIEnv*, jobject, jlong rendererRef)
	{
		auto* renderer = (JNIRendererGL*)rendererRef;
		delete renderer;
	}

	// Skia Renderer
	JNIEXPORT jlong JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererSkia_constructor(
	    JNIEnv* env, jobject ktRendererSkia, jboolean trace)
	{
		auto renderer = new JNIRendererSkia(ktRendererSkia, trace);
		g_JNIRenderer = renderer;
		return (jlong)renderer;
	}

	JNIEXPORT void JNICALL
	Java_app_rive_runtime_kotlin_renderers_RendererSkia_cleanupJNI(
	    JNIEnv*, jobject, jlong rendererRef)
	{
		delete reinterpret_cast<JNIRendererSkia*>(rendererRef);
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
		reinterpret_cast<JNIRendererSkia*>(rendererRef)->startFrame();
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

#ifdef __cplusplus
}
#endif

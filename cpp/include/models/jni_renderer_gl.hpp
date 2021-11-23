#ifndef _RIVE_ANDROID_JAVA_RENDERER_GL_HPP_
#define _RIVE_ANDROID_JAVA_RENDERER_GL_HPP_

#include "opengl/opengl_renderer.hpp"
#include "opengl/opengl_render_paint.hpp"
#include "opengl/opengl_render_path.hpp"
#include <jni.h>

namespace rive_android
{
	class JNIRendererGL : public rive::OpenGLRenderer,
	                      virtual public IJNIRenderer
	{
	public:
		jobject jRendererObject;
		int width, height;

		~JNIRendererGL() { getJNIEnv()->DeleteGlobalRef(jRendererObject); }

		void startFrame() override
		{
			glClearColor(0.05f, 0.05f, 0.05f, 1.0f);
			glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
			OpenGLRenderer::startFrame();
		}

		// Interface passthrough.
		void initialize() override { OpenGLRenderer::initialize(nullptr); }

		rive::RenderPaint* makeRenderPaint() override
		{
			return OpenGLRenderer::makeRenderPaint();
		}
		rive::RenderPath* makeRenderPath() override
		{
			return OpenGLRenderer::makeRenderPath();
		}
	};
} // namespace rive_android
#endif
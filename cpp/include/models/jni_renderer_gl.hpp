#ifndef _RIVE_ANDROID_JAVA_RENDERER_GL_HPP_
#define _RIVE_ANDROID_JAVA_RENDERER_GL_HPP_

#include "renderer/opengl_renderer.hpp"
#include "renderer/opengl_render_paint.hpp"
#include "renderer/opengl_render_path.hpp"
#include <jni.h>

namespace rive_android
{
	class JNIRendererGL : public rive::OpenGLRenderer
	{
	public:
		jobject jRendererObject;
		int width, height;

		~JNIRendererGL()
		{
			getJNIEnv()->DeleteGlobalRef(jRendererObject);
		}

		void startFrame() override
		{
			glClearColor(0.05f, 0.05f, 0.05f, 1.0f);
			glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
			OpenGLRenderer::startFrame();
		}
} // namespace rive_android
#endif
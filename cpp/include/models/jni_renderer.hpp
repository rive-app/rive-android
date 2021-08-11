#ifndef _RIVE_ANDROID_JAVA_RENDERER_HPP_
#define _RIVE_ANDROID_JAVA_RENDERER_HPP_

#include "rive/renderer.hpp"
#include <jni.h>

namespace rive_android
{
	class JNIRenderer : public rive::Renderer
	{
	public:
		jobject jRendererObject;
		static bool antialias;
		~JNIRenderer();

		void save() override;
		void restore() override;
		void transform(const rive::Mat2D &transform) override;
		void translate(float x, float y);
		void drawPath(rive::RenderPath *path, rive::RenderPaint *paint) override;
		void clipPath(rive::RenderPath *path) override;
	};

} // namespace rive_android
#endif
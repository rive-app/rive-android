#ifndef _RIVE_ANDROID_JAVA_RENDERER_HPP_
#define _RIVE_ANDROID_JAVA_RENDERER_HPP_

#include "rive/renderer.hpp"
#include "models/render_path.hpp"
#include "models/render_paint.hpp"
#include <jni.h>

namespace rive_android
{
	/**
	 * Interface for a generic JNI Renderer.
	 */
	class IJNIRenderer
	{
	public:
		virtual ~IJNIRenderer() {}
		virtual rive::RenderPaint* makeRenderPaint() = 0;
		virtual rive::RenderPath* makeRenderPath() = 0;
		virtual void initialize() = 0;
	};

	class JNIRenderer : public rive::Renderer, virtual public IJNIRenderer
	{
	public:
		jobject jRendererObject;
		static bool antialias;
		~JNIRenderer();

		void save() override;
		void restore() override;
		void transform(const rive::Mat2D& transform) override;
		void translate(float x, float y);
		void drawPath(rive::RenderPath* path,
		              rive::RenderPaint* paint) override;
		void clipPath(rive::RenderPath* path) override;

		void initialize() override {}

		rive::RenderPaint* makeRenderPaint() override
		{
			return new JNIRenderPaint();
		}
		rive::RenderPath* makeRenderPath() override
		{
			return new JNIRenderPath();
		}
	};

} // namespace rive_android
#endif
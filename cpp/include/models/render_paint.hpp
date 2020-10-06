#ifndef _RIVE_ANDROID_RENDER_PAINT_HPP_
#define _RIVE_ANDROID_RENDER_PAINT_HPP_

#include "models/gradient_builder.hpp"

// From rive-cpp
#include "renderer.hpp"
//

namespace rive_android
{
	class JNIRenderPaint : public rive::RenderPaint
	{
	private:
		JNIGradientBuilder *gradientBuilder;
		void porterDuffBlendMode(rive::BlendMode value);

	public:
		jobject jObject;
		jclass jClass;

		JNIRenderPaint();
		void color(unsigned int value) override;
		void style(rive::RenderPaintStyle value) override;
		void thickness(float value) override;
		void join(rive::StrokeJoin value) override;
		void cap(rive::StrokeCap value) override;
		void blendMode(rive::BlendMode value) override;
		void linearGradient(float sx, float sy, float ex, float ey) override;
		void radialGradient(float sx, float sy, float ex, float ey) override;
		void addStop(unsigned int color, float stop) override;
		void completeGradient() override;
	};

} // namespace rive_android
#endif
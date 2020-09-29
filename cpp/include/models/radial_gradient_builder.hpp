#ifndef _RIVE_ANDROID_RADIAL_GRADIENT_BUILDER_HPP_
#define _RIVE_ANDROID_RADIAL_GRADIENT_BUILDER_HPP_

#include "jni_refs.hpp"
#include "models/gradient_builder.hpp"

namespace rive_android
{
	class JNIRadialGradientBuilder : public JNIGradientBuilder
	{
	public:
		JNIRadialGradientBuilder(float sx, float sy, float ex, float ey) : JNIGradientBuilder(sx, sy, ex, ey)
		{
		}
		void apply(jobject paint) override;
	};

} // namespace rive_android
#endif
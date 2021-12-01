#ifndef _RIVE_ANDROID_GRADIENT_BUILDER_HPP_
#define _RIVE_ANDROID_GRADIENT_BUILDER_HPP_

#include <vector>

namespace rive_android
{
	class JNIGradientBuilder
	{
	public:
		std::vector<int> colors;
		std::vector<float> stops;
		float sx, sy, ex, ey;
		virtual ~JNIGradientBuilder() {}
		JNIGradientBuilder(float sx, float sy, float ex, float ey) :
		    sx(sx), sy(sy), ex(ex), ey(ey)
		{
		}

		virtual void apply(jobject paint) = 0;
	};

} // namespace rive_android
#endif
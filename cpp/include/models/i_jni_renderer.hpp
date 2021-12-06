#ifndef _RIVE_ANDROID_JAVA_RENDERER_HPP_
#define _RIVE_ANDROID_JAVA_RENDERER_HPP_

#include "rive/renderer.hpp"
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

} // namespace rive_android
#endif
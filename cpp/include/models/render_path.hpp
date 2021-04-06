#ifndef _RIVE_ANDROID_RENDER_PATH_HPP_
#define _RIVE_ANDROID_RENDER_PATH_HPP_

#include "helpers/general.hpp"
#include "renderer.hpp" // for rive::RenderPath

namespace rive_android
{
	class JNIRenderPath : public rive::RenderPath
	{
	public:
		jobject jObject;
		jclass jClass;
		JNIRenderPath();
		~JNIRenderPath();

		void reset() override;
		void fillRule(rive::FillRule value) override;
		void addRenderPath(rive::RenderPath *path, const rive::Mat2D &transform) override;

		void moveTo(float x, float y) override;
		void lineTo(float x, float y) override;
		void cubicTo(
			float ox, float oy, float ix, float iy, float x, float y) override;
		void close() override;
	};

} // namespace rive_android
#endif

// void SkiaRenderPath::addRenderPath(RenderPath* path, const Mat2D& transform)
// {
// 	m_Path.addPath(
// 	    reinterpret_cast<SkiaRenderPath*>(path)->m_Path,
// 	    ToSkia::convert(transform));
// }
#ifndef _RIVE_ANDROID_DIMENSIONS_RENDERER_HPP_
#define _RIVE_ANDROID_DIMENSIONS_RENDERER_HPP_

#include "rive/renderer.hpp"
#include <jni.h>

namespace rive_android
{

	class DimensionsHelper : public rive::Renderer
	{
	private:
		float m_Width;
		float m_Height;
		float m_ScaleX;
		float m_ScaleY;
		void save() override {}
		void restore() override {}
		void transform(const rive::Mat2D& transform) override
		{
			m_ScaleX = transform[0];
			m_ScaleY = transform[3];
		}
		void drawPath(rive::RenderPath* path, rive::RenderPaint* paint) override
		{
		}

		void clipPath(rive::RenderPath* path) override {}

		void drawImage(const rive::RenderImage* image,
		               rive::BlendMode value,
		               float opacity) override
		{
		}

		void drawImageMesh(const rive::RenderImage* image,
		                   rive::rcp<rive::RenderBuffer> vertices_f32,
		                   rive::rcp<rive::RenderBuffer> uvCoords_f32,
		                   rive::rcp<rive::RenderBuffer> indices_u16,
		                   rive::BlendMode blendMode,
		                   float opacity) override
		{
		}

	public:
		DimensionsHelper() :
		    m_Width(0.0f), m_Height(0.0f), m_ScaleX(1.0f), m_ScaleY(1.0f)
		{
		}
		~DimensionsHelper(){};

		float width() const { return m_Width; }
		float height() const { return m_Height; }
		float scaleX() const { return m_ScaleX; }
		float scaleY() const { return m_ScaleY; }

		void computeDimensions(rive::Fit fit,
		                       rive::Alignment alignment,
		                       const rive::AABB& frame,
		                       const rive::AABB& content,
		                       rive::AABB& output);
	};
} // namespace rive_android
#endif

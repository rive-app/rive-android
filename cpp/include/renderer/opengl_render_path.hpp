#ifndef _RIVE_OPENGL_RENDER_PATH_HPP_
#define _RIVE_OPENGL_RENDER_PATH_HPP_

#include "opengl.h"
#include "rive/contour_render_path.hpp"
#include "rive/math/mat2d.hpp"

namespace rive
{
	class OpenGLRenderer;
	class OpenGLRenderPath : public ContourRenderPath
	{
	private:
		FillRule m_FillRule;
		GLuint m_ContourBuffer = 0;

	public:
		OpenGLRenderPath();
		~OpenGLRenderPath();
		void fillRule(FillRule value) override;
		FillRule fillRule() const { return m_FillRule; }

		void stencil(OpenGLRenderer* renderer, const Mat2D& transform);
		void cover(OpenGLRenderer* renderer,
		           const Mat2D& transform,
		           const Mat2D& localTransform = Mat2D::identity());
		void renderStroke(ContourStroke* stroke,
		                  OpenGLRenderer* renderer,
		                  const Mat2D& transform,
		                  const Mat2D& localTransform = Mat2D::identity());
	};
} // namespace rive
#endif
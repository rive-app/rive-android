#ifndef _RIVE_OPENGL_RENDERER_HPP_
#define _RIVE_OPENGL_RENDERER_HPP_

#include "rive/math/mat2d.hpp"
#include "low_level_renderer.hpp"
#include "opengl.h"
#include <vector>

namespace rive
{
	class OpenGLRenderer : public LowLevelRenderer
	{
	private:
		Mat2D m_Projection;
		GLuint m_VertexShader = 0, m_FragmentShader = 0;
		GLuint m_Program = 0;
		GLuint m_IndexBuffer = 0;
		GLint m_ProjectionUniformIndex = -1;
		GLint m_TransformUniformIndex = -1;
		GLint m_FillTypeUniformIndex = -1;
		GLint m_StopCountUniformIndex = -1;
		GLint m_StopColorsUniformIndex = -1;
		GLint m_ColorUniformIndex = -1;
		GLint m_StopsUniformIndex = -1;
		GLint m_GradientPositionUniformIndex = -1;
		GLint m_ShapeTransformUniformIndex = -1;
		GLuint m_VertexArray = 0;
		GLuint m_BlitBuffer = 0;
		bool m_IsClipping = false;

		/// Indices for the max sized contour, prepended with 2 triangles for
		/// bounding boxes.
		std::vector<unsigned short> m_Indices;

	public:
		const GLuint indexBuffer() const { return m_IndexBuffer; }
		const bool isClipping() const { return m_IsClipping; }
		OpenGLRenderer();
		~OpenGLRenderer();
		void drawPath(RenderPath *path, RenderPaint *paint) override;

		void startFrame() override;
		void endFrame() override;

		RenderPaint *makeRenderPaint() override;
		RenderPath *makeRenderPath() override;

		bool initialize(void *data) override;

		void updateIndexBuffer(std::size_t contourLength);

		GLint transformUniformIndex() const { return m_TransformUniformIndex; }

		GLint fillTypeUniformIndex() const { return m_FillTypeUniformIndex; }
		GLint stopCountUniformIndex() const { return m_StopCountUniformIndex; }
		GLint stopColorsUniformIndex() const
		{
			return m_StopColorsUniformIndex;
		}
		GLint colorUniformIndex() const { return m_ColorUniformIndex; }
		GLint stopsUniformIndex() const { return m_StopsUniformIndex; }
		GLint shapeTransformUniformIndex() const
		{
			return m_ShapeTransformUniformIndex;
		}
		GLint gradientPositionUniformIndex() const
		{
			return m_GradientPositionUniformIndex;
		}

		GLuint program() const { return m_Program; }
		virtual const char *shaderHeader() const { return nullptr; };
	};

} // namespace rive
#endif
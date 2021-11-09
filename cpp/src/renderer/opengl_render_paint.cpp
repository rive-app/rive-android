#include "renderer/opengl_render_paint.hpp"
#include "renderer/opengl_renderer.hpp"
#include "renderer/opengl_render_path.hpp"

#include "rive/shapes/paint/color.hpp"
#include "rive/contour_stroke.hpp"

using namespace rive;

void fillColorBuffer(float *buffer, unsigned int value)
{
	buffer[0] = colorRed(value) / 255.0f;
	buffer[1] = colorGreen(value) / 255.0f;
	buffer[2] = colorBlue(value) / 255.0f;
	buffer[3] = colorAlpha(value) / 255.0f;
}

void OpenGLRenderPaint::style(RenderPaintStyle style)
{
	m_PaintStyle = style;
	delete m_Stroke;
	if (m_PaintStyle == RenderPaintStyle::stroke)
	{
		m_Stroke = new ContourStroke();
		m_StrokeDirty = true;
		if (m_StrokeBuffer != 0)
		{
			glDeleteBuffers(1, &m_StrokeBuffer);
		}
		glGenBuffers(1, &m_StrokeBuffer);
	}
	else
	{
		m_Stroke = nullptr;
		m_StrokeDirty = false;
	}
}

void OpenGLRenderPaint::color(unsigned int value)
{
	fillColorBuffer(m_Color, value);
}

void OpenGLRenderPaint::thickness(float value) { m_StrokeThickness = value; }

void OpenGLRenderPaint::join(StrokeJoin value) { m_StrokeJoin = value; }

void OpenGLRenderPaint::cap(StrokeCap value) { m_StrokeCap = value; }

void OpenGLRenderPaint::blendMode(BlendMode value) {}

void OpenGLRenderPaint::linearGradient(float sx, float sy, float ex, float ey)
{
	if (m_Gradient == nullptr)
	{
		m_Gradient = new OpenGLGradient(1);
	}
	m_Gradient->position(sx, sy, ex, ey);
}

void OpenGLRenderPaint::radialGradient(float sx, float sy, float ex, float ey)
{
	if (m_Gradient == nullptr)
	{
		m_Gradient = new OpenGLGradient(2);
	}
	m_Gradient->position(sx, sy, ex, ey);
}

void OpenGLRenderPaint::addStop(unsigned int color, float stop)
{
	m_Gradient->addStop(color, stop);
}

void OpenGLRenderPaint::completeGradient() {}

void OpenGLRenderPaint::invalidateStroke()
{
	if (m_Stroke != nullptr)
	{
		m_StrokeDirty = true;
	}
}

OpenGLRenderPaint::~OpenGLRenderPaint()
{
	if (m_StrokeBuffer != 0)
	{
		glDeleteBuffers(1, &m_StrokeBuffer);
	}
	delete m_Gradient;
	delete m_Stroke;
}

bool OpenGLRenderPaint::doesDraw() const
{
	if (m_Color[3] == 0.0f)
	{
		return false;
	}
	if (m_Gradient == nullptr)
	{
		return true;
	}
	if (m_Gradient->m_IsVisible)
	{
		return true;
	}
	return false;
	// return m_Color[3] > 0.0f &&
	//        (m_Gradient == nullptr || m_Gradient->m_IsVisible);
}

void OpenGLRenderPaint::draw(OpenGLRenderer *renderer,
														 const Mat2D &transform,
														 OpenGLRenderPath *path)
{
	uint32_t type = 0;
	if (m_Gradient != nullptr)
	{
		type = m_Gradient->m_Type;
		m_Gradient->bind(renderer);
	}

	glUniform1i(renderer->fillTypeUniformIndex(), type);
	glUniform4fv(renderer->colorUniformIndex(), 1, m_Color);

	if (m_Stroke != nullptr)
	{
		if (m_StrokeDirty)
		{
			static Mat2D identity;
			m_Stroke->reset();
			path->extrudeStroke(m_Stroke,
													m_StrokeJoin,
													m_StrokeCap,
													m_StrokeThickness / 2.0f,
													identity);
			m_StrokeDirty = false;
		}

		const std::vector<Vec2D> &strip = m_Stroke->triangleStrip();
		auto size = strip.size();
		if (size == 0)
		{
			return;
		}

		glBindBuffer(GL_ARRAY_BUFFER, m_StrokeBuffer);
		glBufferData(GL_ARRAY_BUFFER,
								 size * 2 * sizeof(float),
								 &strip[0][0],
								 GL_DYNAMIC_DRAW);

		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 2 * 4, (void *)0);

		m_Stroke->resetRenderOffset();
		path->renderStroke(m_Stroke, renderer, transform);
	}
	else
	{
		path->cover(renderer, transform);
	}
}

OpenGLGradient::OpenGLGradient(int type) : m_Type(type) {}

void OpenGLGradient::position(float sx, float sy, float ex, float ey)
{
	m_Colors.clear();
	m_Stops.clear();
	m_IsVisible = false;
	m_Position[0] = sx;
	m_Position[1] = sy;
	m_Position[2] = ex;
	m_Position[3] = ey;
}

void OpenGLGradient::bind(OpenGLRenderer *renderer)
{
	auto numberOfStops = m_Stops.size();

	glUniform1i(renderer->stopCountUniformIndex(), numberOfStops);
	glUniform4fv(
			renderer->stopColorsUniformIndex(), numberOfStops, &m_Colors[0]);
	glUniform1fv(renderer->stopsUniformIndex(), numberOfStops, &m_Stops[0]);
	glUniform4fv(renderer->gradientPositionUniformIndex(), 1, &m_Position[0]);
}

void OpenGLGradient::addStop(unsigned int color, float stop)
{
	auto index = m_Colors.size();
	m_Colors.resize(index + 4);

	fillColorBuffer(&m_Colors[index], color);
	if (m_Colors[index + 3] > 0.0f)
	{
		m_IsVisible = true;
	}
	m_Stops.push_back(stop);
}
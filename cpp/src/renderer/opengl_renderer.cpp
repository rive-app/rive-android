#include "renderer/opengl_renderer.hpp"
#include "renderer/opengl_render_path.hpp"
#include "renderer/opengl_render_paint.hpp"
#include <cassert>

using namespace rive;

const char *vertexShaderSource = R"""(#version 300 es

		layout(location = 0) in vec2 position;

out vec2 pos;

uniform mat4 projection;
uniform mat4 transform;
uniform mat4 localTransform;

void main()
{
	gl_Position = projection * transform * vec4(position, 0.0, 1.0);
	pos = (localTransform * vec4(position, 0.0, 1.0)).xy;
}
)""";

const char* fragmentShaderSource = R"""(#version 300 es

#ifdef GL_ES
precision highp float;
#endif

uniform vec4 color;
uniform vec4 position;
uniform int count;
uniform vec4 colors[16];
uniform float stops[16];
uniform int fillType;
in vec2 pos;
out vec4 fragColor;

void main()
{
	if (fillType == 0)
	{
		// solid
		fragColor = color; // vec4(color.rgb * color.a, color.a);
	}
	else if (fillType == 1)
	{
		// linear

		vec2 start = position.xy;
		vec2 end = position.zw;

		vec2 toEnd = end - start;
		float lengthSquared = toEnd.x * toEnd.x + toEnd.y * toEnd.y;
		float f = dot(pos - start, toEnd) / lengthSquared;
		fragColor =
				mix(colors[0], colors[1], smoothstep(stops[0], stops[1], f));
		for (int i = 1; i < 15; ++i)
		{
			if (i >= count - 1)
			{
				break;
			}
			fragColor = mix(fragColor,
											colors[i + 1],
											smoothstep(stops[i], stops[i + 1], f));
		}
		// float alpha = fragColor.w;
		// fragColor = vec4(fragColor.xyz * alpha, alpha);
	}
	else if (fillType == 2)
	{
		// radial

		vec2 start = position.xy;
		vec2 end = position.zw;

		float f = distance(start, pos) / distance(start, end);
		fragColor =
				mix(colors[0], colors[1], smoothstep(stops[0], stops[1], f));
		for (int i = 1; i < 15; ++i)
		{
			if (i >= count - 1)
			{
				break;
			}
			fragColor = mix(fragColor,
											colors[i + 1],
											smoothstep(stops[i], stops[i + 1], f));
		}
		// float alpha = fragColor.w;
		// fragColor = vec4(fragColor.xyz * alpha, alpha);
	}
}
)""";


GLuint createAndCompileShader(GLuint type, const char* source);

OpenGLRenderer::OpenGLRenderer() {}
OpenGLRenderer::~OpenGLRenderer()
{
	glDeleteProgram(m_Program);
	glDeleteShader(m_VertexShader);
	glDeleteShader(m_FragmentShader);
	glDeleteBuffers(1, &m_IndexBuffer);
	glDeleteBuffers(1, &m_BlitBuffer);
	glDeleteVertexArrays(1, &m_VertexArray);
}

bool OpenGLRenderer::initialize(void *data)
{
	fprintf(stderr, "------INITIALIZING OPENGL------\n");
	assert(m_VertexShader == 0 && m_FragmentShader == 0 && m_Program == 0);

	m_VertexShader =
			createAndCompileShader(GL_VERTEX_SHADER, vertexShaderSource);
	if (m_VertexShader == 0)
	{
		return false;
	}

	m_FragmentShader =
			createAndCompileShader(GL_FRAGMENT_SHADER, fragmentShaderSource);
	if (m_FragmentShader == 0)
	{
		return false;
	}

	m_Program = glCreateProgram();
	glAttachShader(m_Program, m_VertexShader);
	glAttachShader(m_Program, m_FragmentShader);
	glLinkProgram(m_Program);
	GLint isLinked = 0;
	glGetProgramiv(m_Program, GL_LINK_STATUS, (int *)&isLinked);
	if (isLinked == GL_FALSE)
	{
		GLint maxLength = 0;
		glGetProgramiv(m_Program, GL_INFO_LOG_LENGTH, &maxLength);

		std::vector<GLchar> infoLog(maxLength);
		glGetProgramInfoLog(m_Program, maxLength, &maxLength, &infoLog[0]);
		fprintf(stderr, "Failed to link program %s\n", &infoLog[0]);
		return false;
	}

	// Create index buffer which we'll grow and populate as necessary.
	glGenBuffers(1, &m_IndexBuffer);
	glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, m_IndexBuffer);

	// Create vertex buffer for blitting to full viewport coordinates.
	float blitBuffer[8] = {
			-1.0f,
			1.0f,

			1.0f,
			1.0f,

			1.0f,
			-1.0f,

			-1.0f,
			-1.0f,
	};
	glGenBuffers(1, &m_BlitBuffer);
	glBindBuffer(GL_ARRAY_BUFFER, m_BlitBuffer);
	glBufferData(
			GL_ARRAY_BUFFER, 8 * sizeof(float), &blitBuffer[0], GL_STATIC_DRAW);

	// Two triangles for bounds.
	m_Indices.emplace_back(0);
	m_Indices.emplace_back(1);
	m_Indices.emplace_back(2);
	m_Indices.emplace_back(2);
	m_Indices.emplace_back(3);
	m_Indices.emplace_back(0);

	glBufferData(GL_ELEMENT_ARRAY_BUFFER,
							 m_Indices.size() * sizeof(unsigned short),
							 &m_Indices[0],
							 GL_STATIC_DRAW);

	glGenVertexArrays(1, &m_VertexArray);
	glBindVertexArray(m_VertexArray);

	glUseProgram(m_Program);

	m_ProjectionUniformIndex = glGetUniformLocation(m_Program, "projection");
	m_TransformUniformIndex = glGetUniformLocation(m_Program, "transform");

	m_FillTypeUniformIndex = glGetUniformLocation(m_Program, "fillType");
	m_StopCountUniformIndex = glGetUniformLocation(m_Program, "count");
	m_StopColorsUniformIndex = glGetUniformLocation(m_Program, "colors");
	m_StopsUniformIndex = glGetUniformLocation(m_Program, "stops");
	m_ColorUniformIndex = glGetUniformLocation(m_Program, "color");
	m_GradientPositionUniformIndex =
			glGetUniformLocation(m_Program, "position");
	m_ShapeTransformUniformIndex =
			glGetUniformLocation(m_Program, "localTransform");

	float projection[16] = {0.0f};
	orthographicProjection(projection, 0.0f, 800, 800, 0.0f, 0.0f, 1.0f);
	modelViewProjection(projection);

	return true;
}

void OpenGLRenderer::drawPath(RenderPath *path, RenderPaint *paint)
{
	auto glPaint = static_cast<OpenGLRenderPaint *>(paint);
	// if (glPaint->style() == RenderPaintStyle::stroke || !glPaint->doesDraw())

	if (!glPaint->doesDraw())
	{
		return;
	}
	bool needsStencil = glPaint->style() == RenderPaintStyle::fill;

	glColorMask(false, false, false, false);
	// Set fill type to 0 so we don't perform any gradient fragment calcs.
	glUniform1i(fillTypeUniformIndex(), 0);

	if (isClippingDirty())
	{
		if (m_IsClipping)
		{
			// Clear previous clip.
			glStencilMask(0xFF);
			glClear(GL_STENCIL_BUFFER_BIT);

			// TODO: instead of clearing the entire buffer, as we clip we could
			// compute the combined clipping area set and clear that here.
		}
		auto clipLength = m_ClipPaths.size();
		if (clipLength > 0)
		{
			m_IsClipping = true;
			SubPath &firstClipPath = m_ClipPaths[0];

			glStencilMask(0xFF);
			glStencilFunc(GL_ALWAYS, 0x0, 0xFF);

			glStencilOpSeparate(GL_FRONT, GL_KEEP, GL_KEEP, GL_INCR_WRAP);
			glStencilOpSeparate(GL_BACK, GL_KEEP, GL_KEEP, GL_DECR_WRAP);
			static_cast<OpenGLRenderPath *>(firstClipPath.path())
					->stencil(this, firstClipPath.transform());

			// Fail when not equal to 0 and replace with 0x80 (mark high bit as
			// included in clip). Require stencil mask (write mask) of 0xFF and
			// stencil func mask of 0x7F such that the comparison looks for 0
			// but write 0x80.
			glStencilMask(0xFF);
			glStencilFunc(GL_NOTEQUAL, 0x80, 0x7F);
			glStencilOp(GL_ZERO, GL_ZERO, GL_REPLACE);

			glBindBuffer(GL_ARRAY_BUFFER, m_BlitBuffer);
			glEnableVertexAttribArray(0);
			glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 2 * 4, (void *)0);
			glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, m_IndexBuffer);

			float m4[16] = {1.0,
											0.0,
											0.0,
											0.0,

											0.0,
											1.0,
											0.0,
											0.0,

											0.0,
											0.0,
											1.0,
											0.0,

											0.0,
											0.0,
											0.0,
											1.0};

			glUniformMatrix4fv(transformUniformIndex(), 1, GL_FALSE, m4);
			glUniformMatrix4fv(m_ProjectionUniformIndex, 1, GL_FALSE, m4);

			// Draw bounds.
			glDrawElements(GL_TRIANGLES, 2 * 3, GL_UNSIGNED_SHORT, (void *)(0));

			glUniformMatrix4fv(
					m_ProjectionUniformIndex, 1, GL_FALSE, m_ModelViewProjection);
			for (int i = 1; i < clipLength; i++)
			// for (int i = 1; i < 0; i++)
			{

				// When already clipping we want to write only to the last/lower
				// 7 bits as our high 8th bit is used to mark clipping
				// inclusion.
				glStencilMask(0x7F);
				// Pass only if that 8th bit is set. This allows us to write our
				// new winding into the lower 7 bits.
				glStencilFunc(GL_EQUAL, 0x80, 0x80);
				SubPath &nextClipPath = m_ClipPaths[i];

				glStencilOpSeparate(GL_FRONT, GL_KEEP, GL_KEEP, GL_INCR_WRAP);
				glStencilOpSeparate(GL_BACK, GL_KEEP, GL_KEEP, GL_DECR_WRAP);
				static_cast<OpenGLRenderPath *>(nextClipPath.path())
						->stencil(this, nextClipPath.transform());

				// Fail when not equal to 0 and replace with 0x80 (mark high bit
				// as included in clip). Require stencil mask (write mask) of
				// 0xFF and stencil func mask of 0x7F such that the comparison
				// looks for 0 but write 0x80.
				glStencilMask(0xFF);
				glStencilFunc(GL_NOTEQUAL, 0x80, 0x7F);
				glStencilOp(GL_ZERO, GL_ZERO, GL_REPLACE);

				glBindBuffer(GL_ARRAY_BUFFER, m_BlitBuffer);
				glEnableVertexAttribArray(0);
				glVertexAttribPointer(
						0, 2, GL_FLOAT, GL_FALSE, 2 * 4, (void *)0);

				glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, m_IndexBuffer);

				glUniformMatrix4fv(transformUniformIndex(), 1, GL_FALSE, m4);
				glUniformMatrix4fv(m_ProjectionUniformIndex, 1, GL_FALSE, m4);
				// Draw bounds.
				glDrawElements(
						GL_TRIANGLES, 2 * 3, GL_UNSIGNED_SHORT, (void *)(0));

				glUniformMatrix4fv(m_ProjectionUniformIndex,
													 1,
													 GL_FALSE,
													 m_ModelViewProjection);
			}
		}
		else
		{
			m_IsClipping = false;
		}
	}
	auto glPath = static_cast<OpenGLRenderPath *>(path);

	if (needsStencil)
	{
		// Set up stencil buffer.
		if (m_IsClipping)
		{
			glStencilMask(0x7F);
			glStencilFunc(GL_EQUAL, 0x80, 0x80);
		}
		else
		{
			glStencilMask(0xFF);
			glStencilFunc(GL_ALWAYS, 0x0, 0xFF);
		}

		glStencilOpSeparate(GL_FRONT, GL_KEEP, GL_KEEP, GL_INCR_WRAP);
		glStencilOpSeparate(GL_BACK, GL_KEEP, GL_KEEP, GL_DECR_WRAP);

		auto xform = transform();
		glPath->stencil(this, xform);

		glColorMask(true, true, true, true);
		glStencilFunc(GL_NOTEQUAL, 0, m_IsClipping ? 0x7F : 0xFF);
		glStencilOp(GL_ZERO, GL_ZERO, GL_ZERO);
	}
	else
	{
		if (m_IsClipping)
		{
			glStencilMask(0x7F);
			glStencilFunc(GL_EQUAL, 0x80, 0x80);
		}
		else
		{
			glStencilMask(0xFF);
			glStencilFunc(GL_ALWAYS, 0x0, 0xFF);
		}
		glStencilOpSeparate(GL_FRONT, GL_KEEP, GL_KEEP, GL_KEEP);
		glStencilOpSeparate(GL_BACK, GL_KEEP, GL_KEEP, GL_KEEP);
		glColorMask(true, true, true, true);
		// glStencilFunc(GL_ALWAYS, 0x0, 0xFF);
		glStencilOp(GL_ZERO, GL_ZERO, GL_ZERO);
	}
	glPaint->draw(this, transform(), glPath);

	// glPath->cover(this, transform());
}

void OpenGLRenderer::startFrame()
{
	glClearColor(0.5f, 0.5f, 0.5f, 1.0f);
	LowLevelRenderer::startFrame();
	glUseProgram(m_Program);
	glEnableVertexAttribArray(0);
	glUniformMatrix4fv(
			m_ProjectionUniformIndex, 1, GL_FALSE, m_ModelViewProjection);
	glEnable(GL_STENCIL_TEST);
	glEnable(GL_BLEND);
	glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
}

void OpenGLRenderer::endFrame() {}

RenderPaint *OpenGLRenderer::makeRenderPaint()
{
	return new OpenGLRenderPaint();
}

RenderPath *OpenGLRenderer::makeRenderPath() { return new OpenGLRenderPath(); }

void OpenGLRenderer::updateIndexBuffer(std::size_t contourLength)
{
	if (contourLength < 2)
	{
		return;
	}
	auto edgeCount = (m_Indices.size() - 6) / 3;
	auto targetEdgeCount = contourLength - 2;
	if (edgeCount < targetEdgeCount)
	{
		while (edgeCount < targetEdgeCount)
		{
			m_Indices.push_back(3);
			m_Indices.push_back(edgeCount + 4);
			m_Indices.push_back(edgeCount + 5);
			edgeCount++;
		}

		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, m_IndexBuffer);
		glBufferData(GL_ELEMENT_ARRAY_BUFFER,
								 m_Indices.size() * sizeof(unsigned short),
								 &m_Indices[0],
								 GL_STATIC_DRAW);
	}
}

GLuint createAndCompileShader(GLuint type, const char *source)
{
	GLuint shader = glCreateShader(type);
	glShaderSource(shader, 1, &source, nullptr);
	glCompileShader(shader);
	GLint isCompiled = 0;
	glGetShaderiv(shader, GL_COMPILE_STATUS, &isCompiled);
	if (isCompiled == GL_FALSE)
	{
		GLint maxLength = 0;
		glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &maxLength);

		std::vector<GLchar> infoLog(maxLength);
		glGetShaderInfoLog(shader, maxLength, &maxLength, &infoLog[0]);
		fprintf(stderr, "Failed to compile shader %s\n", &infoLog[0]);
		glDeleteShader(shader);

		return 0;
	}

	return shader;
}
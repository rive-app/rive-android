#include "renderer/opengl_render_path.hpp"
#include "renderer/opengl_renderer.hpp"
#include "renderer/opengl.h"
#include "rive/contour_stroke.hpp"

using namespace rive;

OpenGLRenderPath::OpenGLRenderPath() { glGenBuffers(1, &m_ContourBuffer); }

OpenGLRenderPath::~OpenGLRenderPath() { glDeleteBuffers(1, &m_ContourBuffer); }
void OpenGLRenderPath::fillRule(FillRule value) { m_FillRule = value; }

void OpenGLRenderPath::stencil(OpenGLRenderer* renderer, const Mat2D& transform)
{
	if (isContainer())
	{
		for (auto& subPath : m_SubPaths)
		{
			Mat2D pathTransform;
			// Mat2D::multiply(pathTransform, transform, subPath.transform());
			Mat2D::multiply(pathTransform, transform, subPath.transform());
			reinterpret_cast<OpenGLRenderPath*>(subPath.path())
			    ->stencil(renderer, pathTransform);
		}
		return;
	}

	// glUseProgram(renderer->program());
	std::size_t vertexCount;

	if (isDirty())
	{
		computeContour();
		vertexCount = m_ContourVertices.size();
		// We only want the indices to go from the off contour point (bounds'
		// last point). First 4 points are bounds.
		renderer->updateIndexBuffer(vertexCount - 3);

		glBindBuffer(GL_ARRAY_BUFFER, m_ContourBuffer);
		glBufferData(GL_ARRAY_BUFFER,
		             vertexCount * 2 * sizeof(float),
		             &m_ContourVertices[0][0],
		             GL_DYNAMIC_DRAW);
	}
	else
	{
		glBindBuffer(GL_ARRAY_BUFFER, m_ContourBuffer);
		vertexCount = m_ContourVertices.size();
	}

	// 4 vertices of bounds and one for the repeated start (repeated on close so
	// we don't need to modulate indices and share them across all paths with
	// different contours).
	if (vertexCount < 5)
	{
		return;
	}

	auto triangleCount = vertexCount - 5;
	// printf("VCOUNT: %i E: %i\n", vertexCount, triangleCount);
	// printf("X: %f %f\n", transform[0], transform[1]);
	// printf("Y: %f %f\n", transform[2], transform[3]);
	// printf("T: %f %f\n", transform[4], transform[5]);

	float m4[16] = {transform[0],
	                transform[1],
	                0.0,
	                0.0,
	                transform[2],
	                transform[3],
	                0.0,
	                0.0,
	                0.0,
	                0.0,
	                1.0,
	                0.0,
	                transform[4],
	                transform[5],
	                0.0,
	                1.0};

	glUniformMatrix4fv(renderer->transformUniformIndex(), 1, GL_FALSE, m4);

	glEnableVertexAttribArray(0);
	glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 2 * 4, (void*)0);

	// glDisable(GL_CULL_FACE);
	// glDisable(GL_DEPTH_TEST);
	// glEnable(GL_BLEND);
	// glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

	glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, renderer->indexBuffer());
	// Index buffer offset is always after first 6 (2 triangles for bounds).

	// Draw the triangulated contour (triangle fans from the bottom left of the
	// AABB) into the stencil buffer.
	glDrawElements(GL_TRIANGLES,
	               triangleCount * 3,
	               GL_UNSIGNED_SHORT,
	               (void*)(6 * sizeof(unsigned short)));

	// GLenum err;
	// while ((err = glGetError()) != GL_NO_ERROR)
	// {
	// 	// Process/log the error.
	// 	fprintf(stderr, "ERRR:: %i\n", err);
	// }
	// glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
	// static unsigned short indices[3] = {0, 2, 2};
	// glDrawElements(GL_TRIANGLES, 3, GL_UNSIGNED_SHORT, &indices[0]);
}

void OpenGLRenderPath::cover(OpenGLRenderer* renderer,
                             const Mat2D& transform,
                             const Mat2D& localTransform)
{
	if (isContainer())
	{
		for (auto& subPath : m_SubPaths)
		{
			const Mat2D& subPathTransform = subPath.transform();
			Mat2D pathTransform;
			Mat2D::multiply(pathTransform, transform, subPathTransform);
			reinterpret_cast<OpenGLRenderPath*>(subPath.path())
			    ->cover(renderer, pathTransform, subPathTransform);
		}
		return;
	}

	glBindBuffer(GL_ARRAY_BUFFER, m_ContourBuffer);
	auto vertexCount = m_ContourVertices.size();

	if (vertexCount < 5)
	{
		return;
	}

	{
		float m4[16] = {transform[0],
		                transform[1],
		                0.0,
		                0.0,
		                transform[2],
		                transform[3],
		                0.0,
		                0.0,
		                0.0,
		                0.0,
		                1.0,
		                0.0,
		                transform[4],
		                transform[5],
		                0.0,
		                1.0};

		glUniformMatrix4fv(renderer->transformUniformIndex(), 1, GL_FALSE, m4);
	}
	{
		float m4[16] = {localTransform[0],
		                localTransform[1],
		                0.0,
		                0.0,
		                localTransform[2],
		                localTransform[3],
		                0.0,
		                0.0,
		                0.0,
		                0.0,
		                1.0,
		                0.0,
		                localTransform[4],
		                localTransform[5],
		                0.0,
		                1.0};

		glUniformMatrix4fv(
		    renderer->shapeTransformUniformIndex(), 1, GL_FALSE, m4);
	}

	glEnableVertexAttribArray(0);
	glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 2 * 4, (void*)0);

	glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, renderer->indexBuffer());

	// Draw bounds.
	glDrawElements(GL_TRIANGLES, 2 * 3, GL_UNSIGNED_SHORT, (void*)(0));
}

void OpenGLRenderPath::renderStroke(ContourStroke* stroke,
                                    OpenGLRenderer* renderer,
                                    const Mat2D& transform,
                                    const Mat2D& localTransform)
{
	if (isContainer())
	{
		for (auto& subPath : m_SubPaths)
		{
			reinterpret_cast<OpenGLRenderPath*>(subPath.path())
			    ->renderStroke(stroke, renderer, transform, localTransform);
		}
		return;
	}

	{
		float m4[16] = {transform[0],
		                transform[1],
		                0.0,
		                0.0,
		                transform[2],
		                transform[3],
		                0.0,
		                0.0,
		                0.0,
		                0.0,
		                1.0,
		                0.0,
		                transform[4],
		                transform[5],
		                0.0,
		                1.0};

		glUniformMatrix4fv(renderer->transformUniformIndex(), 1, GL_FALSE, m4);
	}
	{
		float m4[16] = {localTransform[0],
		                localTransform[1],
		                0.0,
		                0.0,
		                localTransform[2],
		                localTransform[3],
		                0.0,
		                0.0,
		                0.0,
		                0.0,
		                1.0,
		                0.0,
		                localTransform[4],
		                localTransform[5],
		                0.0,
		                1.0};

		glUniformMatrix4fv(
		    renderer->shapeTransformUniformIndex(), 1, GL_FALSE, m4);
	}

	std::size_t start, end;
	stroke->nextRenderOffset(start, end);

	glDrawArrays(GL_TRIANGLE_STRIP, start, end - start);
}
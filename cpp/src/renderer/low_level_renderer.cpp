#include "renderer/low_level_renderer.hpp"
#include <cstring>
#include <cassert>

using namespace rive;

SubPath::SubPath(RenderPath* path, const Mat2D& transform) :
    m_Path(path), m_Transform(transform)
{
}

RenderPath* SubPath::path() { return m_Path; }
const Mat2D& SubPath::transform() { return m_Transform; }

LowLevelRenderer::LowLevelRenderer() { m_Stack.emplace_back(RenderState()); }

void LowLevelRenderer::modelViewProjection(float value[16])
{
	std::memcpy(m_ModelViewProjection, value, sizeof(m_ModelViewProjection));
}

void LowLevelRenderer::orthographicProjection(float dst[16],
                                              float left,
                                              float right,
                                              float bottom,
                                              float top,
                                              float near,
                                              float far)
{
	dst[0] = 2.0f / (right - left);
	dst[1] = 0.0f;
	dst[2] = 0.0f;
	dst[3] = 0.0f;

	dst[4] = 0.0f;
	dst[5] = 2.0f / (top - bottom);
	dst[6] = 0.0f;
	dst[7] = 0.0f;

	dst[8] = 0.0f;
	dst[9] = 0.0f;
	dst[10] = 2.0f / (near - far);
	dst[11] = 0.0f;

	dst[12] = (right + left) / (left - right);
	dst[13] = (top + bottom) / (bottom - top);
	dst[14] = (far + near) / (near - far);
	dst[15] = 1.0f;
}

void LowLevelRenderer::save() { m_Stack.push_back(m_Stack.back()); }

void LowLevelRenderer::restore()
{
	assert(m_Stack.size() > 1);
	RenderState& state = m_Stack.back();
	m_Stack.pop_back();

	// We can only add clipping paths so if they're still the same, nothing has
	// changed.
	m_IsClippingDirty =
	    state.clipPaths.size() != m_Stack.back().clipPaths.size();
}

void LowLevelRenderer::transform(const Mat2D& transform)
{
	Mat2D& stackMat = m_Stack.back().transform;
	Mat2D::multiply(stackMat, stackMat, transform);
}
const Mat2D& LowLevelRenderer::transform() { return m_Stack.back().transform; }

void LowLevelRenderer::clipPath(RenderPath* path)
{
	RenderState& state = m_Stack.back();
	state.clipPaths.emplace_back(SubPath(path, state.transform));
	m_IsClippingDirty = true;
}

void LowLevelRenderer::startFrame()
{
	assert(m_Stack.size() == 1);
	m_ClipPaths.clear();
	m_IsClippingDirty = false;
}

bool LowLevelRenderer::isClippingDirty()
{
	if (!m_IsClippingDirty)
	{
		return false;
	}

	m_IsClippingDirty = false;
	RenderState& state = m_Stack.back();
	auto currentClipLength = m_ClipPaths.size();
	if (currentClipLength == state.clipPaths.size())
	{
		// Same length so now check if they're all the same.
		bool allSame = true;
		for (std::size_t i = 0; i < currentClipLength; i++)
		{
			if (state.clipPaths[i].path() != m_ClipPaths[i].path())
			{
				allSame = false;
				break;
			}
		}
		if (allSame)
		{
			return false;
		}
	}
	m_ClipPaths = state.clipPaths;

	return true;
}
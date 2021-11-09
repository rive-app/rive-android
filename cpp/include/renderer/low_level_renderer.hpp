#ifndef _RIVE_LOW_LEVEL_RENDERER_HPP_
#define _RIVE_LOW_LEVEL_RENDERER_HPP_

#include "rive/renderer.hpp"
#include "rive/math/mat2d.hpp"
#include <stdint.h>
#include <list>
#include <vector>

namespace rive
{
	class SubPath
	{
	private:
		RenderPath* m_Path;
		Mat2D m_Transform;

	public:
		SubPath(RenderPath* path, const Mat2D& transform);

		RenderPath* path();
		const Mat2D& transform();
	};

	struct RenderState
	{
		Mat2D transform;
		std::vector<SubPath> clipPaths;
	};

	///
	/// Low level implementation of a generalized rive::Renderer. It's
	/// specifically tailored for use with low level graphics apis like Metal,
	/// OpenGL, Vulkan, D3D, etc.
	///
	class LowLevelRenderer : public Renderer
	{
	protected:
		float m_ModelViewProjection[16] = {0.0f};
		std::list<RenderState> m_Stack;
		bool m_IsClippingDirty = false;
		std::vector<SubPath> m_ClipPaths;

	public:
		LowLevelRenderer();

		///
		/// Checks if clipping is dirty and clears the clipping flag. Hard
		/// expectation for whoever checks this to also apply it. That's why
		/// it's not marked const.
		///
		bool isClippingDirty();

		virtual void startFrame();
		virtual void endFrame() = 0;

		virtual RenderPaint* makeRenderPaint() = 0;
		virtual RenderPath* makeRenderPath() = 0;
		virtual bool initialize(void* data) = 0;
		bool initialize() { return initialize(nullptr); }

		void modelViewProjection(float value[16]);

		virtual void orthographicProjection(float dst[16],
		                                    float left,
		                                    float right,
		                                    float bottom,
		                                    float top,
		                                    float near,
		                                    float far);

		void save() override;
		void restore() override;
		void transform(const Mat2D& transform) override;
		const Mat2D& transform();
		void clipPath(RenderPath* path) override;
	};

} // namespace rive
#endif
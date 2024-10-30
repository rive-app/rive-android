#include "models/dimensions_helper.hpp"
#include "rive/math/mat2d.hpp"

using namespace rive_android;

rive::AABB DimensionsHelper::computeDimensions(rive::Fit fit,
                                               rive::Alignment alignment,
                                               const rive::AABB& frame,
                                               const rive::AABB& content,
                                               float scaleFactor)
{
    align(fit, alignment, frame, content, scaleFactor);
    m_Width = m_ScaleX * content.width();
    m_Height = m_ScaleY * content.height();
    // todo sort out the minX & minY respecting alignment
    return {0, 0, m_Width, m_Height};
}

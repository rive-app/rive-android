#include "models/dimensions_helper.hpp"
#include "rive/math/mat2d.hpp"

using namespace rive_android;

void DimensionsHelper::computeDimensions(rive::Fit fit, rive::Alignment alignment, const rive::AABB &frame, const rive::AABB &content, rive::AABB &output)
{

    align(fit, alignment, frame, content);
    m_Width = m_ScaleX * content.width();
    m_Height = m_ScaleY * content.height();
    // todo sort out the minX & minY respecting alignment
    output.minX = 0.0;
    output.maxX = m_Width;
    output.minY = 0.0;
    output.maxY = m_Height;
}

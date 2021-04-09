#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "models/jni_renderer.hpp"
#include "models/render_paint.hpp"
#include "models/linear_gradient_builder.hpp"
#include "models/radial_gradient_builder.hpp"

#include <vector>

using namespace rive_android;

JNIRenderPaint::JNIRenderPaint()
{
    __android_log_print(ANDROID_LOG_INFO, __FILE__, "create paint");
    auto env = getJNIEnv();
    jObject = env->NewGlobalRef(
        env->NewObject(getPaintClass(), getPaintInitMethod()));

    auto aaSetter = env->GetMethodID(getPaintClass(), "setAntiAlias", "(Z)V");
    env->CallVoidMethod(jObject, aaSetter, ::JNIRenderer::antialias);
}

JNIRenderPaint::~JNIRenderPaint()
{
    getJNIEnv()->DeleteGlobalRef(jObject);
}

void JNIRenderPaint::color(unsigned int value)
{

    getJNIEnv()->CallVoidMethod(jObject, getSetColorMethodId(), value);
}

void JNIRenderPaint::style(rive::RenderPaintStyle value)
{

    if (value == rive::RenderPaintStyle::stroke)
    {
        getJNIEnv()->CallVoidMethod(
            jObject,
            getSetStyleMethodId(),
            getJNIEnv()->GetStaticObjectField(
                getStyleClass(), getStrokeId()));
    }
    else
    {
        getJNIEnv()->CallVoidMethod(
            jObject,
            getSetStyleMethodId(),
            getJNIEnv()->GetStaticObjectField(
                getStyleClass(), getFillId()));
    }
}

void JNIRenderPaint::thickness(float value)
{
    getJNIEnv()->CallVoidMethod(jObject, getSetStrokeWidthMethodId(), value);
}

void JNIRenderPaint::join(rive::StrokeJoin value)
{

    jfieldID joinId;
    switch (value)
    {
    case rive::StrokeJoin::miter:
        joinId = getMiterId();
        break;
    case rive::StrokeJoin::round:
        joinId = getRoundId();
        break;
    case rive::StrokeJoin::bevel:
        joinId = getBevelId();
        break;
    default:
        joinId = getMiterId();
        break;
    }
    getJNIEnv()->CallVoidMethod(
        jObject,
        getSetStrokeJoinMethodId(),
        getJNIEnv()->GetStaticObjectField(getJoinClass(), joinId));
}

void JNIRenderPaint::cap(rive::StrokeCap value)
{
    jfieldID capId;
    switch (value)
    {
    case rive::StrokeCap::butt:
        capId = getCapButtID();
        break;
    case rive::StrokeCap::round:
        capId = getCapRoundId();
        break;
    case rive::StrokeCap::square:
        capId = getCapSquareId();
        break;
    default:
        capId = getCapButtID();
        break;
    }
    getJNIEnv()->CallVoidMethod(
        jObject,
        getSetStrokeCapMethodId(),
        getJNIEnv()->GetStaticObjectField(getCapClass(), capId));
}

void JNIRenderPaint::porterDuffBlendMode(rive::BlendMode value)
{
    jfieldID modeId;
    switch (value)
    {
    case rive::BlendMode::srcOver:
        modeId = ::getPdSrcOver();
        break;
    case rive::BlendMode::screen:
        modeId = ::getPdScreen();
        break;
    case rive::BlendMode::overlay:
        modeId = ::getPdOverlay();
        break;
    case rive::BlendMode::darken:
        modeId = ::getPdDarken();
        break;
    case rive::BlendMode::lighten:
        modeId = ::getPdLighten();
        break;
    case rive::BlendMode::colorDodge:
        return;
        break;
    case rive::BlendMode::colorBurn:
        return;
        break;
    case rive::BlendMode::hardLight:
        return;
        break;
    case rive::BlendMode::softLight:
        return;
        break;
    case rive::BlendMode::difference:
        return;
        break;
    case rive::BlendMode::exclusion:
        return;
        break;
    case rive::BlendMode::multiply:
        modeId = ::getPdMultiply();
        break;
    case rive::BlendMode::hue:
        return;
        break;
    case rive::BlendMode::saturation:
        return;
        break;
    case rive::BlendMode::color:
        return;
        break;
    case rive::BlendMode::luminosity:
        return;
        break;
    default:
        modeId = ::getPdClear();
        break;
    }

    jobject xferModeClass = getJNIEnv()->NewObject(
        getPorterDuffXferModeClass(),
        getPorterDuffXferModeInitMethodId(),
        getJNIEnv()->GetStaticObjectField(getPorterDuffClass(), modeId));

    getJNIEnv()->CallObjectMethod(
        jObject,
        getSetXfermodeMethodId(),
        xferModeClass);
}

void JNIRenderPaint::blendMode(rive::BlendMode value)
{
    if (::sdkVersion < 29)
    {
        return this->porterDuffBlendMode(value);
    }

    jfieldID modeId;
    switch (value)
    {
    case rive::BlendMode::srcOver:
        modeId = ::getSrcOver();
        break;
    case rive::BlendMode::screen:
        modeId = ::getScreen();
        break;
    case rive::BlendMode::overlay:
        modeId = ::getOverlay();
        break;
    case rive::BlendMode::darken:
        modeId = ::getDarken();
        break;
    case rive::BlendMode::lighten:
        modeId = ::getLighten();
        break;
    case rive::BlendMode::colorDodge:
        modeId = ::getColorDodge();
        break;
    case rive::BlendMode::colorBurn:
        modeId = ::getColorBurn();
        break;
    case rive::BlendMode::hardLight:
        modeId = ::getHardLight();
        break;
    case rive::BlendMode::softLight:
        modeId = ::getSoftLight();
        break;
    case rive::BlendMode::difference:
        modeId = ::getDifference();
        break;
    case rive::BlendMode::exclusion:
        modeId = ::getExclusion();
        break;
    case rive::BlendMode::multiply:
        modeId = ::getMultiply();
        break;
    case rive::BlendMode::hue:
        modeId = ::getHue();
        break;
    case rive::BlendMode::saturation:
        modeId = ::getSaturation();
        break;
    case rive::BlendMode::color:
        modeId = ::getColor();
        break;
    case rive::BlendMode::luminosity:
        modeId = ::getLuminosity();
        break;
    default:
        modeId = ::getClear();
        break;
    }

    getJNIEnv()->CallVoidMethod(
        jObject,
        getSetBlendModeMethodId(),
        getJNIEnv()->GetStaticObjectField(getBlendModeClass(), modeId));
}

void JNIRenderPaint::linearGradient(float sx, float sy, float ex, float ey)
{
    gradientBuilder = new JNILinearGradientBuilder(sx, sy, ex, ey);
}

void JNIRenderPaint::radialGradient(float sx, float sy, float ex, float ey)
{
    gradientBuilder = new JNIRadialGradientBuilder(sx, sy, ex, ey);
}

void JNIRenderPaint::addStop(unsigned int color, float stop)
{
    gradientBuilder->colors.emplace_back(color);
    gradientBuilder->stops.emplace_back(stop);
}

void JNIRenderPaint::completeGradient()
{
    gradientBuilder->apply(jObject);
    delete gradientBuilder;
}
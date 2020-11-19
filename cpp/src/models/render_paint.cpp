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
    jObject = globalJNIEnv->NewGlobalRef(
        globalJNIEnv->NewObject(paintClass, paintInitMethod));

    auto aaSetter = globalJNIEnv->GetMethodID(paintClass, "setAntiAlias", "(Z)V");
    globalJNIEnv->CallVoidMethod(jObject, aaSetter, ::JNIRenderer::antialias);
}

void JNIRenderPaint::color(unsigned int value)
{

    globalJNIEnv->CallVoidMethod(jObject, setColorMethodId, value);
}

void JNIRenderPaint::style(rive::RenderPaintStyle value)
{

    if (value == rive::RenderPaintStyle::stroke)
    {
        globalJNIEnv->CallVoidMethod(
            jObject,
            setStyleMethodId,
            globalJNIEnv->GetStaticObjectField(
                styleClass, strokeId));
    }
    else
    {
        globalJNIEnv->CallVoidMethod(
            jObject,
            setStyleMethodId,
            globalJNIEnv->GetStaticObjectField(
                styleClass, fillId));
    }
}

void JNIRenderPaint::thickness(float value)
{
    globalJNIEnv->CallVoidMethod(jObject, setStrokeWidthMethodId, value);
}

void JNIRenderPaint::join(rive::StrokeJoin value)
{

    jfieldID joinId;
    switch (value)
    {
    case rive::StrokeJoin::miter:
        joinId = miterId;
        break;
    case rive::StrokeJoin::round:
        joinId = roundId;
        break;
    case rive::StrokeJoin::bevel:
        joinId = bevelId;
        break;
    default:
        joinId = miterId;
        break;
    }
    globalJNIEnv->CallVoidMethod(
        jObject,
        setStrokeJoinMethodId,
        globalJNIEnv->GetStaticObjectField(joinClass, joinId));
}

void JNIRenderPaint::cap(rive::StrokeCap value)
{
    jfieldID capId;
    switch (value)
    {
    case rive::StrokeCap::butt:
        capId = capButtID;
        break;
    case rive::StrokeCap::round:
        capId = capRoundId;
        break;
    case rive::StrokeCap::square:
        capId = capSquareId;
        break;
    default:
        capId = capButtID;
        break;
    }
    globalJNIEnv->CallVoidMethod(
        jObject,
        setStrokeCapMethodId,
        globalJNIEnv->GetStaticObjectField(capClass, capId));
}

void JNIRenderPaint::porterDuffBlendMode(rive::BlendMode value)
{
    __android_log_print(ANDROID_LOG_INFO, __FILE__, "wat??");
    jfieldID modeId;
    switch (value)
    {
    case rive::BlendMode::srcOver:
        modeId = ::pdSrcOver;
        break;
    case rive::BlendMode::screen:
        modeId = ::pdScreen;
        break;
    case rive::BlendMode::overlay:
        modeId = ::pdOverlay;
        break;
    case rive::BlendMode::darken:
        modeId = ::pdDarken;
        break;
    case rive::BlendMode::lighten:
        modeId = ::pdLighten;
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
        modeId = ::pdMultiply;
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
        modeId = ::pdClear;
        break;
    }

    jobject xferModeClass = globalJNIEnv->NewObject(
        porterDuffXferModeClass,
        porterDuffXferModeInitMethodId,
        globalJNIEnv->GetStaticObjectField(porterDuffClass, modeId));

    globalJNIEnv->CallObjectMethod(
        jObject,
        setXfermodeMethodId,
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
        modeId = ::srcOver;
        break;
    case rive::BlendMode::screen:
        modeId = ::screen;
        break;
    case rive::BlendMode::overlay:
        modeId = ::overlay;
        break;
    case rive::BlendMode::darken:
        modeId = ::darken;
        break;
    case rive::BlendMode::lighten:
        modeId = ::lighten;
        break;
    case rive::BlendMode::colorDodge:
        modeId = ::colorDodge;
        break;
    case rive::BlendMode::colorBurn:
        modeId = ::colorBurn;
        break;
    case rive::BlendMode::hardLight:
        modeId = ::hardLight;
        break;
    case rive::BlendMode::softLight:
        modeId = ::softLight;
        break;
    case rive::BlendMode::difference:
        modeId = ::difference;
        break;
    case rive::BlendMode::exclusion:
        modeId = ::exclusion;
        break;
    case rive::BlendMode::multiply:
        modeId = ::multiply;
        break;
    case rive::BlendMode::hue:
        modeId = ::hue;
        break;
    case rive::BlendMode::saturation:
        modeId = ::saturation;
        break;
    case rive::BlendMode::color:
        modeId = ::color;
        break;
    case rive::BlendMode::luminosity:
        modeId = ::luminosity;
        break;
    default:
        modeId = ::clear;
        break;
    }

    globalJNIEnv->CallVoidMethod(
        jObject,
        setBlendModeMethodId,
        globalJNIEnv->GetStaticObjectField(blendModeClass, modeId));
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
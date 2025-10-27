#include <jni.h>
#include <memory>
#include <android/bitmap.h>

#include "helpers/canvas_render_objects.hpp"
#include "helpers/jni_exception_handler.hpp"

namespace rive_android
{

/** CanvasRenderPath */
/* static */ jobject CanvasRenderPath::CreatePath()
{
    JNIEnv* env = GetJNIEnv();
    jclass pathClass = GetPathClass();
    jmethodID pathConstructor = GetPathInitMethodId();
    jobject ktPath =
        env->NewGlobalRef(env->NewObject(pathClass, pathConstructor));

    env->DeleteLocalRef(pathClass);
    return ktPath;
}

CanvasRenderPath::CanvasRenderPath() :
    m_FillRule(rive::FillRule::nonZero), m_ktPath(CreatePath())
{}

static void addRawPathToCanvasPath(jobject ktPath, const rive::RawPath& path)
{
    JNIEnv* env = GetJNIEnv();
    auto points = path.points();
    auto pointsData = points.data();
    auto pathVerbs = path.verbs();

    // Let's cache these...
    auto moveToFn = GetMoveToMethodId();
    auto lineToFn = GetLineToMethodId();
    auto cubicToFn = GetCubicToMethodId();
    auto closeFn = GetCloseMethodId();
    for (auto verb : pathVerbs)
    {
        switch (verb)
        {
            case rive::PathVerb::move:
            {
                auto point = pointsData[0];
                JNIExceptionHandler::CallVoidMethod(env,
                                                    ktPath,
                                                    moveToFn,
                                                    point.x,
                                                    point.y);
                pointsData += 1;
                break;
            }
            case rive::PathVerb::line:
            {
                auto point = pointsData[0];
                JNIExceptionHandler::CallVoidMethod(env,
                                                    ktPath,
                                                    lineToFn,
                                                    point.x,
                                                    point.y);
                pointsData += 1;
                break;
            }
            case rive::PathVerb::cubic:
            {
                auto cp0 = pointsData[0];
                auto cp1 = pointsData[1];
                auto to = pointsData[2];
                JNIExceptionHandler::CallVoidMethod(env,
                                                    ktPath,
                                                    cubicToFn,
                                                    cp0.x,
                                                    cp0.y,
                                                    cp1.x,
                                                    cp1.y,
                                                    to.x,
                                                    to.y);
                pointsData += 3;
                break;
            }
            case rive::PathVerb::close:
            {
                JNIExceptionHandler::CallVoidMethod(env, ktPath, closeFn);
                break;
            }
            default:
                break;
        }
    }
}

CanvasRenderPath::CanvasRenderPath(rive::RawPath& path, rive::FillRule rule) :
    m_FillRule(rule), m_ktPath(CreatePath())
{
    addRawPathToCanvasPath(m_ktPath, path);
}

CanvasRenderPath::~CanvasRenderPath()
{
    GetJNIEnv()->DeleteGlobalRef(m_ktPath);
}

void CanvasRenderPath::rewind()
{
    GetJNIEnv()->CallVoidMethod(m_ktPath, GetResetMethodId());
}

void CanvasRenderPath::addRawPath(const rive::RawPath& path)
{
    addRawPathToCanvasPath(m_ktPath, path);
}

void CanvasRenderPath::addRenderPath(rive::RenderPath* path,
                                     const rive::Mat2D& transform)
{
    JNIEnv* env = GetJNIEnv();
    jclass matrixClass = GetMatrixClass();
    jobject matrix = env->NewObject(matrixClass, GetMatrixInitMethodId());

    float squareMatrix[9] = {transform.xx(),
                             transform.yx(),
                             transform.tx(),
                             transform.xy(),
                             transform.yy(),
                             transform.ty(),
                             0,
                             0,
                             1};

    jfloatArray matrixArray = env->NewFloatArray(9);
    env->SetFloatArrayRegion(matrixArray, 0, 9, squareMatrix);

    JNIExceptionHandler::CallVoidMethod(env,
                                        matrix,
                                        GetMatrixSetValuesMethodId(),
                                        matrixArray);

    JNIExceptionHandler::CallVoidMethod(
        env,
        m_ktPath,
        GetAddPathMethodId(),
        reinterpret_cast<CanvasRenderPath*>(path)->m_ktPath,
        matrix);

    env->DeleteLocalRef(matrixClass);
    env->DeleteLocalRef(matrix);
    env->DeleteLocalRef(matrixArray);
}

void CanvasRenderPath::moveTo(float x, float y)
{
    GetJNIEnv()->CallVoidMethod(m_ktPath, GetMoveToMethodId(), x, y);
}

void CanvasRenderPath::lineTo(float x, float y)
{
    GetJNIEnv()->CallVoidMethod(m_ktPath, GetLineToMethodId(), x, y);
}

void CanvasRenderPath::cubicTo(float ox,
                               float oy,
                               float ix,
                               float iy,
                               float x,
                               float y)
{
    GetJNIEnv()
        ->CallVoidMethod(m_ktPath, GetCubicToMethodId(), ox, oy, ix, iy, x, y);
}

void CanvasRenderPath::close()
{
    GetJNIEnv()->CallVoidMethod(m_ktPath, GetCloseMethodId());
}

void CanvasRenderPath::fillRule(rive::FillRule value)
{
    m_FillRule = value;
    jfieldID fillTypeId;
    switch (m_FillRule)
    {
        case rive::FillRule::evenOdd:
            fillTypeId = GetEvenOddId();
            break;
        case rive::FillRule::nonZero:
        case rive::FillRule::clockwise:
            fillTypeId = GetNonZeroId();
            break;
    }

    JNIEnv* env = GetJNIEnv();
    jclass fillTypeClass = GetFillTypeClass();
    jobject fillId = env->GetStaticObjectField(fillTypeClass, fillTypeId);

    JNIExceptionHandler::CallVoidMethod(env,
                                        m_ktPath,
                                        GetSetFillTypeMethodId(),
                                        fillId);

    env->DeleteLocalRef(fillTypeClass);
    env->DeleteLocalRef(fillId);
}

/** LinearGradientCanvasShader */
LinearGradientCanvasShader::LinearGradientCanvasShader(
    float sx,
    float sy,
    float ex,
    float ey,
    const rive::ColorInt colors[], // [count]
    const float stops[],           // [count]
    size_t count)
{
    JNIEnv* env = GetJNIEnv();

    auto intCount = SizeTTOInt(count);
    jintArray jcolors = env->NewIntArray(intCount);
    jfloatArray jstops = env->NewFloatArray(intCount);
    env->SetIntArrayRegion(jcolors, 0, intCount, (const jint*)colors);
    env->SetFloatArrayRegion(jstops, 0, intCount, stops);

    jclass tileModeClass = GetTileModeClass();
    jobject clampObject =
        env->GetStaticObjectField(tileModeClass, GetClampId());
    jclass linearGradientClass = GetLinearGradientClass();

    m_KtShader =
        env->NewGlobalRef(env->NewObject(linearGradientClass,
                                         GetLinearGradientInitMethodId(),
                                         sx,
                                         sy,
                                         ex,
                                         ey,
                                         jcolors,
                                         jstops,
                                         clampObject));

    env->DeleteLocalRef(jcolors);
    env->DeleteLocalRef(jstops);
    env->DeleteLocalRef(linearGradientClass);
    env->DeleteLocalRef(tileModeClass);
    env->DeleteLocalRef(clampObject);
}

/** RadialGradientCanvasShader */
RadialGradientCanvasShader::RadialGradientCanvasShader(
    float cx,
    float cy,
    float radius,
    const rive::ColorInt colors[], // [count]
    const float stops[],           // [count]
    size_t count)
{
    JNIEnv* env = GetJNIEnv();

    auto intCount = SizeTTOInt(count);
    jintArray jcolors = env->NewIntArray(intCount);
    jfloatArray jstops = env->NewFloatArray(intCount);
    env->SetIntArrayRegion(jcolors, 0, intCount, (const jint*)colors);
    env->SetFloatArrayRegion(jstops, 0, intCount, stops);

    jclass tileModeClass = GetTileModeClass();
    jobject clampObject =
        env->GetStaticObjectField(tileModeClass, GetClampId());
    jclass radialGradientClass = GetRadialGradientClass();

    m_KtShader =
        env->NewGlobalRef(env->NewObject(radialGradientClass,
                                         GetRadialGradientInitMethodId(),
                                         cx,
                                         cy,
                                         radius,
                                         jcolors,
                                         jstops,
                                         clampObject));

    env->DeleteLocalRef(jcolors);
    env->DeleteLocalRef(jstops);
    env->DeleteLocalRef(radialGradientClass);
    env->DeleteLocalRef(tileModeClass);
    env->DeleteLocalRef(clampObject);
}

/** CanvasRenderPaint */
CanvasRenderPaint::CanvasRenderPaint()
{
    JNIEnv* env = GetJNIEnv();
    m_ktPaint = env->NewGlobalRef(CreateKtPaint());
    JNIExceptionHandler::CallVoidMethod(env,
                                        m_ktPaint,
                                        GetSetAntiAliasMethodId(),
                                        JNI_TRUE);
    JNIExceptionHandler::CallVoidMethod(env,
                                        m_ktPaint,
                                        GetSetFilterBitmapMethodId(),
                                        JNI_TRUE);
}

CanvasRenderPaint::~CanvasRenderPaint()
{
    GetJNIEnv()->DeleteGlobalRef(m_ktPaint);
}
void CanvasRenderPaint::style(rive::RenderPaintStyle style)
{
    SetStyle(m_ktPaint, style);
}

void CanvasRenderPaint::thickness(float value)
{
    SetThickness(m_ktPaint, value);
}

void CanvasRenderPaint::join(rive::StrokeJoin join)
{
    SetJoin(m_ktPaint, join);
}

void CanvasRenderPaint::color(rive::ColorInt value)
{
    SetColor(m_ktPaint, value);
}

void CanvasRenderPaint::cap(rive::StrokeCap cap) { SetCap(m_ktPaint, cap); }

void CanvasRenderPaint::shader(rive::rcp<rive::RenderShader> shader)
{
    // `shader` can also be a `nullptr`.
    jobject shaderObject =
        shader == nullptr
            ? nullptr
            : reinterpret_cast<CanvasShader*>(shader.get())->ktShader();
    SetShader(m_ktPaint, shaderObject);
}

void CanvasRenderPaint::blendMode(rive::BlendMode blendMode)
{
    SetBlendMode(m_ktPaint, blendMode);
}

/* static */ void CanvasRenderPaint::SetStyle(jobject paint,
                                              rive::RenderPaintStyle style)
{
    JNIEnv* env = GetJNIEnv();
    jclass styleClass = GetStyleClass();
    jobject staticObject =
        (style == rive::RenderPaintStyle::stroke)
            ? env->GetStaticObjectField(styleClass, GetStrokeId())
            : env->GetStaticObjectField(styleClass, GetFillId());

    JNIExceptionHandler::CallVoidMethod(env,
                                        paint,
                                        GetSetStyleMethodId(),
                                        staticObject);
    env->DeleteLocalRef(styleClass);
    env->DeleteLocalRef(staticObject);
}

/* static */ void CanvasRenderPaint::SetThickness(jobject paint, float value)
{
    GetJNIEnv()->CallVoidMethod(paint, GetSetStrokeWidthMethodId(), value);
}

/* static */ void CanvasRenderPaint::SetJoin(jobject paint,
                                             rive::StrokeJoin value)
{
    jfieldID joinId;
    switch (value)
    {
        case rive::StrokeJoin::round:
            joinId = GetRoundId();
            break;
        case rive::StrokeJoin::bevel:
            joinId = GetBevelId();
            break;
        default:
        case rive::StrokeJoin::miter:
            joinId = GetMiterId();
            break;
    }
    JNIEnv* env = GetJNIEnv();
    jclass joinClass = GetJoinClass();
    jobject staticObject = env->GetStaticObjectField(joinClass, joinId);
    JNIExceptionHandler::CallVoidMethod(env,
                                        paint,
                                        GetSetStrokeJoinMethodId(),
                                        staticObject);

    env->DeleteLocalRef(joinClass);
    env->DeleteLocalRef(staticObject);
}

/* static */ void CanvasRenderPaint::SetColor(jobject paint,
                                              rive::ColorInt value)
{
    GetJNIEnv()->CallVoidMethod(paint, GetSetColorMethodId(), (int)value);
}

/* static */ void CanvasRenderPaint::SetCap(jobject paint,
                                            rive::StrokeCap value)
{
    jfieldID capId;
    switch (value)
    {
        case rive::StrokeCap::butt:
            capId = GetCapButtID();
            break;
        case rive::StrokeCap::round:
            capId = GetCapRoundId();
            break;
        case rive::StrokeCap::square:
            capId = GetCapSquareId();
            break;
        default:
            capId = GetCapButtID();
            break;
    }
    JNIEnv* env = GetJNIEnv();
    jclass capClass = GetCapClass();
    jobject staticObject = env->GetStaticObjectField(capClass, capId);
    JNIExceptionHandler::CallVoidMethod(env,
                                        paint,
                                        GetSetStrokeCapMethodId(),
                                        staticObject);

    env->DeleteLocalRef(capClass);
    env->DeleteLocalRef(staticObject);
}

/* static */ void CanvasRenderPaint::SetShader(jobject paint, jobject shader)
{
    GetJNIEnv()->CallObjectMethod(paint, GetSetShaderMethodId(), shader);
}

/* static */ jobject CanvasRenderPaint::CreateKtPaint()
{
    auto env = GetJNIEnv();
    jclass paintClass = GetPaintClass();
    jobject paint = env->NewObject(paintClass, GetPaintInitMethod());
    env->DeleteLocalRef(paintClass);
    return paint;
}

/* static */ void CanvasRenderPaint::porterDuffBlendMode(jobject paint,
                                                         rive::BlendMode value)
{
    jfieldID modeId;
    switch (value)
    {
        case rive::BlendMode::srcOver:
            modeId = GetPdSrcOver();
            break;
        case rive::BlendMode::screen:
            modeId = GetPdScreen();
            break;
        case rive::BlendMode::overlay:
            modeId = GetPdOverlay();
            break;
        case rive::BlendMode::darken:
            modeId = GetPdDarken();
            break;
        case rive::BlendMode::lighten:
            modeId = GetPdLighten();
            break;
        case rive::BlendMode::multiply:
            modeId = GetPdMultiply();
            break;
        case rive::BlendMode::colorDodge:
        case rive::BlendMode::colorBurn:
        case rive::BlendMode::hardLight:
        case rive::BlendMode::softLight:
        case rive::BlendMode::difference:
        case rive::BlendMode::exclusion:
        case rive::BlendMode::hue:
        case rive::BlendMode::saturation:
        case rive::BlendMode::color:
        case rive::BlendMode::luminosity:
            return;
        default:
            modeId = GetPdClear();
            break;
    }
    JNIEnv* env = GetJNIEnv();
    jclass porterDuffClass = GetPorterDuffClass();
    jobject porterDuffMode = env->GetStaticObjectField(porterDuffClass, modeId);
    jclass porterDuffXferModeClass = GetPorterDuffXferModeClass();

    jobject xferModeObject = env->NewObject(porterDuffXferModeClass,
                                            GetPorterDuffXferModeInitMethodId(),
                                            porterDuffMode);

    jobject extraXferModeObject =
        JNIExceptionHandler::CallObjectMethod(env,
                                              paint,
                                              GetSetXfermodeMethodId(),
                                              xferModeObject);

    env->DeleteLocalRef(extraXferModeObject);
    env->DeleteLocalRef(xferModeObject);
    env->DeleteLocalRef(porterDuffXferModeClass);
    env->DeleteLocalRef(porterDuffMode);
    env->DeleteLocalRef(porterDuffClass);
}

/* static */ void CanvasRenderPaint::SetBlendMode(jobject paint,
                                                  rive::BlendMode value)
{
    if (g_sdkVersion < 29)
    {
        return porterDuffBlendMode(paint, value);
    }

    jfieldID modeId;
    switch (value)
    {
        case rive::BlendMode::srcOver:
            modeId = GetSrcOver();
            break;
        case rive::BlendMode::screen:
            modeId = GetScreen();
            break;
        case rive::BlendMode::overlay:
            modeId = GetOverlay();
            break;
        case rive::BlendMode::darken:
            modeId = GetDarken();
            break;
        case rive::BlendMode::lighten:
            modeId = GetLighten();
            break;
        case rive::BlendMode::colorDodge:
            modeId = GetColorDodge();
            break;
        case rive::BlendMode::colorBurn:
            modeId = GetColorBurn();
            break;
        case rive::BlendMode::hardLight:
            modeId = GetHardLight();
            break;
        case rive::BlendMode::softLight:
            modeId = GetSoftLight();
            break;
        case rive::BlendMode::difference:
            modeId = GetDifference();
            break;
        case rive::BlendMode::exclusion:
            modeId = GetExclusion();
            break;
        case rive::BlendMode::multiply:
            modeId = GetMultiply();
            break;
        case rive::BlendMode::hue:
            modeId = GetHue();
            break;
        case rive::BlendMode::saturation:
            modeId = GetSaturation();
            break;
        case rive::BlendMode::color:
            modeId = GetColor();
            break;
        case rive::BlendMode::luminosity:
            modeId = GetLuminosity();
            break;
        default:
            modeId = GetSrcOver();
            break;
    }
    JNIEnv* env = GetJNIEnv();
    jclass blendModeClass = GetBlendModeClass();
    jobject blendModeStaticObject =
        env->GetStaticObjectField(blendModeClass, modeId);

    JNIExceptionHandler::CallVoidMethod(env,
                                        paint,
                                        GetSetBlendModeMethodId(),
                                        blendModeStaticObject);

    env->DeleteLocalRef(blendModeClass);
    env->DeleteLocalRef(blendModeStaticObject);
}

/* static */ void CanvasRenderPaint::SetPaintAlpha(jobject paint, int alpha)
{
    GetJNIEnv()->CallVoidMethod(paint, GetSetAlphaMethodId(), alpha);
}

/* CanvasRenderImage */
/* static */ jobject CanvasRenderImage::CreateKtBitmapFrom(
    JNIEnv* env,
    rive::Span<const uint8_t>& encodedBytes)
{
    auto jSize = static_cast<jsize>(encodedBytes.size());
    auto jByteArray = env->NewByteArray(jSize);
    if (jByteArray == nullptr)
    {
        LOGE("CreateKtBitmapFrom() - NewByteArray() failed.");
        return nullptr;
    }

    env->SetByteArrayRegion(
        jByteArray,
        0,
        jSize,
        reinterpret_cast<const jbyte*>(encodedBytes.data()));

    auto jBitmapFactoryClass = GetAndroidBitmapFactoryClass();
    auto jDecodeByteArrayMethodID = GetDecodeByteArrayStaticMethodId();

    auto jBitmap = JNIExceptionHandler::CallStaticObjectMethod(
        env,
        jBitmapFactoryClass,
        jDecodeByteArrayMethodID,
        jByteArray,
        0,
        SizeTTOInt(encodedBytes.size()));
    env->DeleteLocalRef(jByteArray);
    env->DeleteLocalRef(jBitmapFactoryClass);
    if (jBitmap == nullptr)
    {
        LOGE("CreateKtBitmapFrom() - decodeByteArray() failed.");
        return nullptr;
    }
    return jBitmap;
}

CanvasRenderImage::CanvasRenderImage(rive::Span<const uint8_t> encodedBytes)
{
    auto* env = GetJNIEnv();
    auto jBitmap = CreateKtBitmapFrom(env, encodedBytes);
    if (jBitmap == nullptr)
    {
        LOGE("CanvasRenderImage() - Failed to create a Bitmap.");
        return;
    }

    m_Width = JNIExceptionHandler::CallIntMethod(env,
                                                 jBitmap,
                                                 GetBitmapWidthMethodId());
    m_Height = JNIExceptionHandler::CallIntMethod(env,
                                                  jBitmap,
                                                  GetBitmapHeightMethodId());

    m_ktBitmap = env->NewGlobalRef(jBitmap);
    env->DeleteLocalRef(jBitmap);
    m_ktPaint = env->NewGlobalRef(CanvasRenderPaint::CreateKtPaint());
    JNIExceptionHandler::CallVoidMethod(env,
                                        m_ktPaint,
                                        GetSetAntiAliasMethodId(),
                                        JNI_TRUE);
    JNIExceptionHandler::CallVoidMethod(env,
                                        m_ktPaint,
                                        GetSetFilterBitmapMethodId(),
                                        JNI_TRUE);
}

CanvasRenderImage::CanvasRenderImage(jobject jBitmap)
{
    auto* env = GetJNIEnv();
    if (jBitmap == nullptr)
    {
        LOGE("CanvasRenderImage(jBitmap) - Bitmap is null.");
        return;
    }
    m_Width = JNIExceptionHandler::CallIntMethod(env,
                                                 jBitmap,
                                                 GetBitmapWidthMethodId());
    m_Height = JNIExceptionHandler::CallIntMethod(env,
                                                  jBitmap,
                                                  GetBitmapHeightMethodId());
    m_ktBitmap = env->NewGlobalRef(jBitmap);
    m_ktPaint = env->NewGlobalRef(CanvasRenderPaint::CreateKtPaint());
    JNIExceptionHandler::CallVoidMethod(env,
                                        m_ktPaint,
                                        GetSetAntiAliasMethodId(),
                                        JNI_TRUE);
    JNIExceptionHandler::CallVoidMethod(env,
                                        m_ktPaint,
                                        GetSetFilterBitmapMethodId(),
                                        JNI_TRUE);
}

CanvasRenderImage::~CanvasRenderImage()
{
    auto* env = GetJNIEnv();
    if (m_ktBitmap)
    {
        env->DeleteGlobalRef(m_ktBitmap);
        m_ktBitmap = nullptr;
    }
    if (m_ktPaint)
    {
        env->DeleteGlobalRef(m_ktPaint);
        m_ktPaint = nullptr;
    }
}
} // namespace rive_android
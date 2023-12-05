#include <jni.h>
#include <memory>
#include <android/bitmap.h>

#include "helpers/canvas_render_objects.hpp"

namespace rive_android
{

/** CanvasRenderPath */
/* static */ jobject CanvasRenderPath::CreatePath()
{
    JNIEnv* env = GetJNIEnv();
    jclass pathClass = GetPathClass();
    jmethodID pathConstructor = GetPathInitMethodId();
    jobject ktPath = env->NewGlobalRef(env->NewObject(pathClass, pathConstructor));

    env->DeleteLocalRef(pathClass);
    return ktPath;
}

CanvasRenderPath::CanvasRenderPath() : m_FillRule(rive::FillRule::nonZero), m_ktPath(CreatePath())
{}
CanvasRenderPath::CanvasRenderPath(rive::RawPath& path, rive::FillRule rule) :
    m_FillRule(rule), m_ktPath(CreatePath())
{
    JNIEnv* env = GetJNIEnv();
    rive::Span<rive::Vec2D> points = path.points();
    rive::Vec2D* pointsData = points.data();
    rive::Span<rive::PathVerb> pathVerbs = path.verbs();

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
                rive::Vec2D point = pointsData[0];
                env->CallVoidMethod(m_ktPath, moveToFn, point.x, point.y);
                pointsData += 1;
                break;
            }
            case rive::PathVerb::line:
            {
                rive::Vec2D point = pointsData[0];
                env->CallVoidMethod(m_ktPath, lineToFn, point.x, point.y);
                pointsData += 1;
                break;
            }
            case rive::PathVerb::cubic:
            {
                auto cp0 = pointsData[0];
                auto cp1 = pointsData[1];
                auto to = pointsData[2];
                env->CallVoidMethod(m_ktPath, cubicToFn, cp0.x, cp0.y, cp1.x, cp1.y, to.x, to.y);
                pointsData += 3;
                break;
            }
            case rive::PathVerb::close:
            {
                env->CallVoidMethod(m_ktPath, closeFn);
                break;
            }
            default:
                break;
        }
    }
}

CanvasRenderPath::~CanvasRenderPath() { GetJNIEnv()->DeleteGlobalRef(m_ktPath); }

void CanvasRenderPath::rewind() { GetJNIEnv()->CallVoidMethod(m_ktPath, GetResetMethodId()); }

void CanvasRenderPath::addRenderPath(rive::RenderPath* path, const rive::Mat2D& transform)
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

    env->CallVoidMethod(matrix, GetMatrixSetValuesMethodId(), matrixArray);

    env->CallVoidMethod(m_ktPath,
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

void CanvasRenderPath::cubicTo(float ox, float oy, float ix, float iy, float x, float y)
{
    GetJNIEnv()->CallVoidMethod(m_ktPath, GetCubicToMethodId(), ox, oy, ix, iy, x, y);
}

void CanvasRenderPath::close() { GetJNIEnv()->CallVoidMethod(m_ktPath, GetCloseMethodId()); }

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
            fillTypeId = GetNonZeroId();
            break;
    }

    JNIEnv* env = GetJNIEnv();
    jclass fillTypeClass = GetFillTypeClass();
    jobject fillId = env->GetStaticObjectField(fillTypeClass, fillTypeId);

    env->CallVoidMethod(m_ktPath, GetSetFillTypeMethodId(), fillId);

    env->DeleteLocalRef(fillTypeClass);
    env->DeleteLocalRef(fillId);
}

/** LinearGradientCanvasShader */
LinearGradientCanvasShader::LinearGradientCanvasShader(float sx,
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
    jobject clampObject = env->GetStaticObjectField(tileModeClass, GetClampId());
    jclass linearGradientClass = GetLinearGradientClass();

    m_KtShader = env->NewGlobalRef(env->NewObject(linearGradientClass,
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
RadialGradientCanvasShader::RadialGradientCanvasShader(float cx,
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
    jobject clampObject = env->GetStaticObjectField(tileModeClass, GetClampId());
    jclass radialGradientClass = GetRadialGradientClass();

    m_KtShader = env->NewGlobalRef(env->NewObject(radialGradientClass,
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
    env->CallVoidMethod(m_ktPaint, GetSetAntiAliasMethodId(), JNI_TRUE);
}

CanvasRenderPaint::~CanvasRenderPaint() { GetJNIEnv()->DeleteGlobalRef(m_ktPaint); }
void CanvasRenderPaint::style(rive::RenderPaintStyle style) { SetStyle(m_ktPaint, style); }

void CanvasRenderPaint::thickness(float value) { SetThickness(m_ktPaint, value); }

void CanvasRenderPaint::join(rive::StrokeJoin join) { SetJoin(m_ktPaint, join); }

void CanvasRenderPaint::color(rive::ColorInt value) { SetColor(m_ktPaint, value); }

void CanvasRenderPaint::cap(rive::StrokeCap cap) { SetCap(m_ktPaint, cap); }

void CanvasRenderPaint::shader(rive::rcp<rive::RenderShader> shader)
{
    // `shader` can also be a `nullptr`.
    jobject shaderObject =
        shader == nullptr ? nullptr : reinterpret_cast<CanvasShader*>(shader.get())->ktShader();
    SetShader(m_ktPaint, shaderObject);
}

void CanvasRenderPaint::blendMode(rive::BlendMode blendMode) { SetBlendMode(m_ktPaint, blendMode); }

/* static */ void CanvasRenderPaint::SetStyle(jobject paint, rive::RenderPaintStyle style)
{
    JNIEnv* env = GetJNIEnv();
    jclass styleClass = GetStyleClass();
    jobject staticObject = (style == rive::RenderPaintStyle::stroke)
                               ? env->GetStaticObjectField(styleClass, GetStrokeId())
                               : env->GetStaticObjectField(styleClass, GetFillId());

    env->CallVoidMethod(paint, GetSetStyleMethodId(), staticObject);
    env->DeleteLocalRef(styleClass);
    env->DeleteLocalRef(staticObject);
}

/* static */ void CanvasRenderPaint::SetThickness(jobject paint, float value)
{
    GetJNIEnv()->CallVoidMethod(paint, GetSetStrokeWidthMethodId(), value);
}

/* static */ void CanvasRenderPaint::SetJoin(jobject paint, rive::StrokeJoin value)
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
    env->CallVoidMethod(paint, GetSetStrokeJoinMethodId(), staticObject);

    env->DeleteLocalRef(joinClass);
    env->DeleteLocalRef(staticObject);
}

/* static */ void CanvasRenderPaint::SetColor(jobject paint, rive::ColorInt value)
{
    GetJNIEnv()->CallVoidMethod(paint, GetSetColorMethodId(), (int)value);
}

/* static */ void CanvasRenderPaint::SetCap(jobject paint, rive::StrokeCap value)
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
    env->CallVoidMethod(paint, GetSetStrokeCapMethodId(), staticObject);

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

/* static */ void CanvasRenderPaint::porterDuffBlendMode(jobject paint, rive::BlendMode value)
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
        env->CallObjectMethod(paint, GetSetXfermodeMethodId(), xferModeObject);

    env->DeleteLocalRef(extraXferModeObject);
    env->DeleteLocalRef(xferModeObject);
    env->DeleteLocalRef(porterDuffXferModeClass);
    env->DeleteLocalRef(porterDuffMode);
    env->DeleteLocalRef(porterDuffClass);
}

/* static */ void CanvasRenderPaint::SetBlendMode(jobject paint, rive::BlendMode value)
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
            modeId = GetClear();
            break;
    }
    JNIEnv* env = GetJNIEnv();
    jclass blendModeClass = GetBlendModeClass();
    jobject blendModeStaticObject = env->GetStaticObjectField(blendModeClass, modeId);

    env->CallVoidMethod(paint, GetSetBlendModeMethodId(), blendModeStaticObject);

    env->DeleteLocalRef(blendModeClass);
    env->DeleteLocalRef(blendModeStaticObject);
}

/* static */ void CanvasRenderPaint::SetPaintAlpha(jobject paint, int alpha)
{
    GetJNIEnv()->CallVoidMethod(paint, GetSetAlphaMethodId(), alpha);
}

/* CanvasRenderImage */
CanvasRenderImage::CanvasRenderImage(int width,
                                     int height,
                                     std::unique_ptr<const uint8_t[]> imageDataRGBAPtr)
{
    m_Width = width;
    m_Height = height;
    // Create the texture on the worker thread where the GL context is current.
    const uint8_t* imageDataRGBA = imageDataRGBAPtr.get();

    JNIEnv* env = GetJNIEnv();
    jobject bitmap = CreateKtBitmap(env, width, height);
    if (bitmap == nullptr)
    {
        LOGE("CreateBitmap failed: returned nullptr.");
        return;
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0)
    {
        LOGE("AndroidBitmap_lockPixels() failed.");
        return;
    }

    const uint8_t* rgbaLine = imageDataRGBA;
    uint32_t* line = (uint32_t*)pixels;
    for (int y = 0; y < height; y++)
    {
        for (int x = 0; x < width; x++)
        {
            uint8_t r = rgbaLine[4 * x];
            uint8_t g = rgbaLine[4 * x + 1];
            uint8_t b = rgbaLine[4 * x + 2];
            uint8_t a = rgbaLine[4 * x + 3];

            line[x] = (a << 24) | (b << 16) | (g << 8) | r;
        }
        line = line + width;
        rgbaLine += width * 4; // 4 bytes per pixel.
    }

    AndroidBitmap_unlockPixels(env, bitmap);

    m_ktBitmap = env->NewGlobalRef(bitmap);
    m_ktPaint = env->NewGlobalRef(CanvasRenderPaint::CreateKtPaint());
    env->CallVoidMethod(m_ktPaint, GetSetAntiAliasMethodId(), JNI_TRUE);
}

CanvasRenderImage::~CanvasRenderImage()
{
    JNIEnv* env = GetJNIEnv();
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
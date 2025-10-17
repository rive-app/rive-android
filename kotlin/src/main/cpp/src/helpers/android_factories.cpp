#include <android/bitmap.h>
#include <jni.h>
#include <vector>

#include "helpers/android_factories.hpp"
#include "helpers/canvas_render_objects.hpp"
#include "helpers/worker_ref.hpp"
#include "helpers/general.hpp"
#include "helpers/image_decode.hpp"
#include "helpers/jni_exception_handler.hpp"
#include "helpers/jni_resource.hpp"
#include "helpers/thread_state_pls.hpp"

#include "rive/math/math_types.hpp"
#include "rive/renderer/rive_render_image.hpp"
#include "rive/renderer/gl/render_buffer_gl_impl.hpp"
#include "rive/renderer/gl/render_context_gl_impl.hpp"
#include "utils/factory_utils.hpp"

using namespace rive;
using namespace rive::gpu;

namespace rive_android
{
template <>
rive::rcp<rive::RenderImage> decode(rive::Span<const uint8_t> data,
                                    RendererType rendererType)
{
    rive::Factory* factory = GetFactory(rendererType);
    return factory->decodeImage(data);
}

template <>
rive::rcp<rive::Font> decode(rive::Span<const uint8_t> data,
                             RendererType rendererType)
{
    rive::Factory* factory = GetFactory(rendererType);
    return factory->decodeFont(data);
}

template <>
rive::rcp<rive::AudioSource> decode(rive::Span<const uint8_t> data,
                                    RendererType rendererType)
{
    rive::Factory* factory = GetFactory(rendererType);
    return factory->decodeAudio(data);
}

// RenderBufferGLImpl specialization that can take advantage EGLWorker and be
// used on or off the GL thread.
class AndroidPLSRenderBuffer : public RenderBufferGLImpl
{
public:
    AndroidPLSRenderBuffer(RenderBufferType type,
                           RenderBufferFlags flags,
                           size_t sizeInBytes) :
        RenderBufferGLImpl(type, flags, sizeInBytes),
        m_glWorker(RefWorker::RiveWorker())
    {
        if (std::this_thread::get_id() != m_glWorker->threadID())
        {
            // We aren't on the GL thread. Init this object on the GL thread.
            // Keep this class alive until the worker thread finishes
            // initializing it.
            rcp<AndroidPLSRenderBuffer> thisRef = ref_rcp(this);
            m_bufferCreationWorkID =
                m_glWorker->run([thisRef](DrawableThreadState* threadState) {
                    auto plsState =
                        reinterpret_cast<PLSThreadState*>(threadState);
                    auto* renderContextImpl =
                        plsState->renderContext()
                            ->static_impl_cast<RenderContextGLImpl>();
                    thisRef->init(ref_rcp(renderContextImpl->state()));
                });
        }
        else
        {
            auto plsState =
                reinterpret_cast<PLSThreadState*>(m_glWorker->threadState());
            auto* renderContextImpl =
                plsState->renderContext()
                    ->static_impl_cast<RenderContextGLImpl>();
            init(ref_rcp(renderContextImpl->state()));
            m_bufferCreationWorkID = WorkerThread::kWorkIDAlwaysFinished;
        }
    }

    ~AndroidPLSRenderBuffer() override
    {
        if (std::this_thread::get_id() != m_glWorker->threadID())
        {
            // Ensure we are done initializing the buffers before we turn around
            // and delete them.
            m_glWorker->waitUntilComplete(m_bufferCreationWorkID);
            // We aren't on the GL thread. Intercept the buffers before
            // ~RenderBufferGLImpl(), and then marshal them off to the GL thread
            // for deletion.
            auto bufferToDelete = detachBuffer();
            rcp<GLState> glState = ref_rcp(state());
            m_glWorker->run([bufferToDelete, glState](DrawableThreadState*) {
                if (bufferToDelete != 0)
                {
                    glState->deleteBuffer(bufferToDelete);
                }
            });
        }
    }

    void* onMap() override
    {
        if (std::this_thread::get_id() != m_glWorker->threadID())
        {
            // We aren't on the GL thread. Allocate a side buffer to fill.
            assert(m_offThreadBufferDataMirror == nullptr);
            m_offThreadBufferDataMirror.reset(new uint8_t[sizeInBytes()]);
            return m_offThreadBufferDataMirror.get();
        }
        else
        {
            return RenderBufferGLImpl::onMap();
        }
    }

    void onUnmap() override
    {
        if (std::this_thread::get_id() != m_glWorker->threadID())
        {
            // We aren't on the GL thread. Marshal our side buffer to the GL
            // thread to update the buffer.
            const uint8_t* sideBufferData =
                m_offThreadBufferDataMirror.release();
            assert(sideBufferData != nullptr);
            // Keep this class alive until the worker thread finishes updating
            // the buffer.
            rcp<AndroidPLSRenderBuffer> thisRef = ref_rcp(this);
            m_glWorker->run([sideBufferData, thisRef](DrawableThreadState*) {
                void* ptr = thisRef->RenderBufferGLImpl::onMap();
                memcpy(ptr, sideBufferData, thisRef->sizeInBytes());
                thisRef->RenderBufferGLImpl::onUnmap();
                delete[] sideBufferData;
            });
        }
        else
        {
            assert(!m_offThreadBufferDataMirror);
            RenderBufferGLImpl::onUnmap();
        }
    }

protected:
    const rcp<RefWorker> m_glWorker;
    std::unique_ptr<uint8_t[]> m_offThreadBufferDataMirror;
    RefWorker::WorkID m_bufferCreationWorkID;
};

rcp<RenderBuffer> AndroidRiveRenderFactory::makeRenderBuffer(
    RenderBufferType type,
    RenderBufferFlags flags,
    size_t sizeInBytes)
{
    return make_rcp<AndroidPLSRenderBuffer>(type, flags, sizeInBytes);
}

AndroidImage::AndroidImage(int width,
                           int height,
                           std::unique_ptr<const uint8_t[]> imageDataRGBAPtr) :
    RiveRenderImage(width, height), m_glWorker(RefWorker::RiveWorker())
{
    // Create the texture on the worker thread where the GL context is
    // current.
    const auto* imageDataRGBA = imageDataRGBAPtr.release();
    m_textureCreationWorkID = m_glWorker->run(
        [this, imageDataRGBA](DrawableThreadState* threadState) {
            auto plsState = reinterpret_cast<PLSThreadState*>(threadState);
            auto mipLevelCount = math::msb(m_Height | m_Width);
            auto* renderContextImpl =
                plsState->renderContext()
                    ->static_impl_cast<RenderContextGLImpl>();
            resetTexture(renderContextImpl->makeImageTexture(m_Width,
                                                             m_Height,
                                                             mipLevelCount,
                                                             imageDataRGBA));
            delete[] imageDataRGBA;
        });
}

AndroidImage::~AndroidImage()
{
    // Ensure we are done initializing the texture before we turn around and
    // delete it.
    m_glWorker->waitUntilComplete(m_textureCreationWorkID);
    // Since this is the destructor, we know nobody else is using this
    // object anymore and there is not a race condition from accessing the
    // texture from any thread.
    auto* texture = releaseTexture();
    if (texture != nullptr)
    {
        // Delete the texture on the worker thread where the GL context is
        // current.
        m_glWorker->run([texture](DrawableThreadState*) { texture->unref(); });
    }
}

rcp<RenderImage> AndroidRiveRenderFactory::decodeImage(
    Span<const uint8_t> encodedBytes)
{
    return renderImageFromAndroidDecode(encodedBytes, false);
}

/** AndroidCanvasFactory */
rive::rcp<rive::RenderBuffer> AndroidCanvasFactory::makeRenderBuffer(
    rive::RenderBufferType type,
    rive::RenderBufferFlags flags,
    size_t sizeInBytes)
{
    return rive::make_rcp<rive::DataRenderBuffer>(type, flags, sizeInBytes);
}

rive::rcp<rive::RenderImage> AndroidCanvasFactory::decodeImage(
    rive::Span<const uint8_t> encodedBytes)
{
    return make_rcp<CanvasRenderImage>(encodedBytes);
}

rive::rcp<rive::RenderShader> AndroidCanvasFactory::makeLinearGradient(
    float sx,
    float sy,
    float ex,
    float ey,
    const rive::ColorInt colors[], // [count]
    const float stops[],           // [count]
    size_t count)
{
    return rive::rcp<rive::RenderShader>(
        new LinearGradientCanvasShader(sx, sy, ex, ey, colors, stops, count));
}

rive::rcp<rive::RenderShader> AndroidCanvasFactory::makeRadialGradient(
    float cx,
    float cy,
    float radius,
    const rive::ColorInt colors[], // [count]
    const float stops[],           // [count]
    size_t count)
{
    return rive::rcp<rive::RenderShader>(
        new RadialGradientCanvasShader(cx, cy, radius, colors, stops, count));
}

rive::rcp<rive::RenderPath> AndroidCanvasFactory::makeRenderPath(
    rive::RawPath& rawPath,
    rive::FillRule fillRule)
{
    return rive::make_rcp<CanvasRenderPath>(rawPath, fillRule);
}

rive::rcp<rive::RenderPath> AndroidCanvasFactory::makeEmptyRenderPath()
{
    return rive::make_rcp<CanvasRenderPath>();
}

rive::rcp<rive::RenderPaint> AndroidCanvasFactory::makeRenderPaint()
{
    return rive::make_rcp<CanvasRenderPaint>();
}

} // namespace rive_android

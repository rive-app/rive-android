/*
 * Copyright 2023 Rive
 */

#include "helpers/android_factories.hpp"

#include "helpers/egl_worker.hpp"
#include "helpers/general.hpp"
#include "helpers/thread_state_pls.hpp"
#include "rive/math/math_types.hpp"
#include "rive/pls/pls_image.hpp"
#include "rive/pls/gl/pls_render_buffer_gl_impl.hpp"
#include "rive/pls/gl/pls_render_context_gl_impl.hpp"

#include <vector>

using namespace rive;
using namespace rive::pls;

bool JNIDecodeImage(Span<const uint8_t> encodedBytes,
                    bool premultiply,
                    uint32_t* width,
                    uint32_t* height,
                    std::vector<uint8_t>* pixels,
                    bool* isOpaque)
{
    auto env = rive_android::GetJNIEnv();

    jclass cls = env->FindClass("app/rive/runtime/kotlin/core/Decoder");
    if (!cls)
    {
        LOGE("can't find class 'app/rive/runtime/kotlin/core/Decoder'");
        return false;
    }

    jmethodID method = env->GetStaticMethodID(cls, "decodeToPixels", "([B)[I");
    if (!method)
    {
        LOGE("can't find static method decodeToPixels");
        return false;
    }

    jbyteArray encoded = env->NewByteArray(rive_android::SizeTTOInt(encodedBytes.size()));
    if (!encoded)
    {
        LOGE("failed to allocate NewByteArray");
        return false;
    }

    env->SetByteArrayRegion(encoded,
                            0,
                            rive_android::SizeTTOInt(encodedBytes.size()),
                            (jbyte*)encodedBytes.data());
    auto jpixels = (jintArray)env->CallStaticObjectMethod(cls, method, encoded);
    env->DeleteLocalRef(encoded); // no longer need encoded

    // At ths point, we have the decode results. Now we just need to convert
    // it into the form we need (ImageInfo + premul pixels)

    size_t arrayCount = env->GetArrayLength(jpixels);
    if (arrayCount < 2)
    {
        LOGE("bad array length (unexpected)");
        return false;
    }

    int* rawPixels = env->GetIntArrayElements(jpixels, nullptr);
    const uint32_t rawWidth = rawPixels[0];
    const uint32_t rawHeight = rawPixels[1];
    const size_t pixelCount = (size_t)rawWidth * rawHeight;
    if (pixelCount == 0)
    {
        LOGE("don't support empty images (zero dimension)");
        return false;
    }
    if (2 + pixelCount < arrayCount)
    {
        LOGE("not enough elements in pixel array");
        return false;
    }

    *width = rawWidth;
    *height = rawHeight;
    pixels->resize(pixelCount * 4);

    auto div255 = [](unsigned value) { return (value + 128) * 257 >> 16; };
    uint8_t* bytes = pixels->data();
    bool rawIsOpaque = true;
    for (size_t i = 0; i < pixelCount; ++i)
    {
        uint32_t p = rawPixels[2 + i];
        unsigned a = (p >> 24) & 0xFF;
        unsigned r = (p >> 16) & 0xFF;
        unsigned g = (p >> 8) & 0xFF;
        unsigned b = (p >> 0) & 0xFF;
        // convert to premul as needed
        if (a != 255)
        {
            if (premultiply)
            {
                r = div255(r * a);
                g = div255(g * a);
                b = div255(b * a);
            }
            rawIsOpaque = false;
        }
        bytes[0] = r;
        bytes[1] = g;
        bytes[2] = b;
        bytes[3] = a;
        bytes += 4;
    }
    *isOpaque = rawIsOpaque;
    env->ReleaseIntArrayElements(jpixels, rawPixels, 0);
    return true;
}

std::vector<uint8_t> AndroidSkiaFactory::platformDecode(Span<const uint8_t> encodedBytes,
                                                        SkiaFactory::ImageInfo* info)
{
    uint32_t width, height;
    std::vector<uint8_t> pixels;
    bool isOpaque;
    if (!JNIDecodeImage(encodedBytes, true /*premultiply*/, &width, &height, &pixels, &isOpaque))
    {
        return pixels;
    }

    info->rowBytes = width * 4; // we're snug
    info->width = width;
    info->height = height;
    info->colorType = ColorType::rgba;
    info->alphaType = isOpaque ? AlphaType::opaque : AlphaType::premul;
    return pixels;
}

// PLSRenderBufferGLImpl specialization that can take advantage EGLWorker and be used on or off the
// GL thread.
class AndroidPLSRenderBuffer : public PLSRenderBufferGLImpl
{
public:
    AndroidPLSRenderBuffer(RenderBufferType type, RenderBufferFlags flags, size_t sizeInBytes) :
        PLSRenderBufferGLImpl(type, flags, sizeInBytes),
        m_glWorker(rive_android::EGLWorker::RiveWorker())
    {
        if (std::this_thread::get_id() != m_glWorker->threadID())
        {
            // We aren't on the GL thread. Init this object on the GL thread.
            // Keep this class alive until the worker thread finishes initializing it.
            rcp<AndroidPLSRenderBuffer> thisRef = ref_rcp(this);
            m_bufferCreationWorkID =
                m_glWorker->run([thisRef](rive_android::EGLThreadState* threadState) {
                    auto plsState = static_cast<rive_android::PLSThreadState*>(threadState);
                    auto* glImpl =
                        plsState->plsContext()->static_impl_cast<PLSRenderContextGLImpl>();
                    thisRef->init(ref_rcp(glImpl->state()));
                });
        }
        else
        {
            auto plsState = static_cast<rive_android::PLSThreadState*>(m_glWorker->threadState());
            auto* glImpl = plsState->plsContext()->static_impl_cast<PLSRenderContextGLImpl>();
            init(ref_rcp(glImpl->state()));
            m_bufferCreationWorkID = rive_android::WorkerThread::kWorkIDAlwaysFinished;
        }
    }

    ~AndroidPLSRenderBuffer()
    {
        if (std::this_thread::get_id() != m_glWorker->threadID())
        {
            // Ensure we are done initializing the buffers before we turn around and delete them.
            m_glWorker->waitUntilComplete(m_bufferCreationWorkID);
            // We aren't on the GL thread. Intercept the buffers before ~PLSRenderBufferGLImpl(),
            // and then marshal them off to the GL thread for deletion.
            std::array<GLuint, pls::kBufferRingSize> buffersToDelete = detachBuffers();
            rcp<GLState> glState = ref_rcp(state());
            m_glWorker->run([buffersToDelete, glState](rive_android::EGLThreadState* threadState) {
                for (GLuint bufferID : buffersToDelete)
                {
                    if (bufferID != 0)
                    {
                        glState->deleteBuffer(bufferID);
                    }
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
            return PLSRenderBufferGLImpl::onMap();
        }
    }

    void onUnmap() override
    {
        if (std::this_thread::get_id() != m_glWorker->threadID())
        {
            // We aren't on the GL thread. Marshal our side buffer to the GL thread to update the
            // buffer.
            const uint8_t* sideBufferData = m_offThreadBufferDataMirror.release();
            assert(sideBufferData != nullptr);
            // Keep this class alive until the worker thread finishes updating the buffer.
            rcp<AndroidPLSRenderBuffer> thisRef = ref_rcp(this);
            m_glWorker->run([sideBufferData, thisRef](rive_android::EGLThreadState* threadState) {
                void* ptr = thisRef->PLSRenderBufferGLImpl::onMap();
                memcpy(ptr, sideBufferData, thisRef->sizeInBytes());
                thisRef->PLSRenderBufferGLImpl::onUnmap();
                delete[] sideBufferData;
            });
        }
        else
        {
            assert(!m_offThreadBufferDataMirror);
            PLSRenderBufferGLImpl::onUnmap();
        }
    }

protected:
    const rcp<rive_android::EGLWorker> m_glWorker;
    std::unique_ptr<uint8_t[]> m_offThreadBufferDataMirror;
    rive_android::EGLWorker::WorkID m_bufferCreationWorkID;
};

rcp<RenderBuffer> AndroidPLSFactory::makeRenderBuffer(RenderBufferType type,
                                                      RenderBufferFlags flags,
                                                      size_t sizeInBytes)
{
    return make_rcp<AndroidPLSRenderBuffer>(type, flags, sizeInBytes);
}

class AndroidPLSImage : public PLSImage
{
public:
    AndroidPLSImage(int width, int height, std::unique_ptr<const uint8_t[]> imageDataRGBAPtr) :
        PLSImage(width, height), m_glWorker(rive_android::EGLWorker::RiveWorker())
    {
        // Create the texture on the worker thread where the GL context is current.
        const uint8_t* imageDataRGBA = imageDataRGBAPtr.release();
        m_textureCreationWorkID = m_glWorker->run([this, imageDataRGBA](
                                                      rive_android::EGLThreadState* threadState) {
            auto plsState = static_cast<rive_android::PLSThreadState*>(threadState);
            uint32_t mipLevelCount = math::msb(m_Height | m_Width);
            auto* glImpl = plsState->plsContext()->static_impl_cast<PLSRenderContextGLImpl>();
            resetTexture(glImpl->makeImageTexture(m_Width, m_Height, mipLevelCount, imageDataRGBA));
            delete[] imageDataRGBA;
        });
    }

    ~AndroidPLSImage() override
    {
        // Ensure we are done initializing the texture before we turn around and delete it.
        m_glWorker->waitUntilComplete(m_textureCreationWorkID);
        // Since this is the destructor, we know nobody else is using this object anymore and there
        // is not a race condition from accessing the texture from any thread.
        PLSTexture* texture = releaseTexture();
        if (texture != nullptr)
        {
            // Delete the texture on the worker thread where the GL context is current.
            m_glWorker->run([texture](rive_android::EGLThreadState*) { texture->unref(); });
        }
    }

private:
    const rcp<rive_android::EGLWorker> m_glWorker;
    rive_android::EGLWorker::WorkID m_textureCreationWorkID;
};

rcp<RenderImage> AndroidPLSFactory::decodeImage(Span<const uint8_t> encodedBytes)
{
    uint32_t width, height;
    std::vector<uint8_t> pixels;
    bool isOpaque;
    if (!JNIDecodeImage(encodedBytes, false /*premultiply*/, &width, &height, &pixels, &isOpaque))
    {
        return nullptr;
    }
    std::unique_ptr<uint8_t[]> bytes(new uint8_t[pixels.size()]);
    memcpy(bytes.get(), pixels.data(), pixels.size());
    return make_rcp<AndroidPLSImage>(width, height, std::move(bytes));
}

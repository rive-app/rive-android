// Desktop image decoding: instead of Android's BitmapFactory (via JNI),
// delegate to the Rive render context's built-in decoders (rive_decoders:
// png/jpeg/webp), which also handle premultiplication.
#include "helpers/image_decode.hpp"

#include "helpers/rive_log.hpp"

namespace rive_android
{

rive::rcp<rive::RenderImage> renderImageFromAndroidDecode(
    rive::Span<const uint8_t> encodedBytes,
    bool isPremultiplied,
    RenderContext* context)
{
    (void)isPremultiplied;
    if (context == nullptr || context->riveContext == nullptr)
    {
        RiveLogE("RiveN/Decode",
                 "Desktop image decode requires a command queue render context");
        return nullptr;
    }
    return context->riveContext->decodeImage(encodedBytes);
}

} // namespace rive_android

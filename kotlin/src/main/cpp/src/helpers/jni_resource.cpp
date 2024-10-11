#include "helpers/general.hpp"
#include "helpers/jni_resource.hpp"

namespace rive_android
{
std::vector<uint8_t> ByteArrayToUint8Vec(JNIEnv* env, jbyteArray byteArray)
{
    jsize length = env->GetArrayLength(byteArray);
    std::vector<uint8_t> bytes(JIntToSizeT(length));
    env->GetByteArrayRegion(byteArray,
                            0,
                            length,
                            reinterpret_cast<jbyte*>(bytes.data()));
    return bytes;
}
} // namespace rive_android

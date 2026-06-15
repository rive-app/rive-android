#include "helpers/conversions.hpp"

#include <climits>
#include <cstdint>

#include "helpers/rive_log.hpp"

namespace rive_android
{
std::string JStringToString(JNIEnv* env, jstring jStr)
{
    if (jStr == nullptr)
    {
        return {};
    }
    auto* cStr = env->GetStringUTFChars(jStr, nullptr);
    auto str = std::string(cStr);
    env->ReleaseStringUTFChars(jStr, cStr);
    return str;
}

int SizeTToInt(size_t size)
{
    return size > INT_MAX ? INT_MAX : static_cast<int>(size);
}

size_t JIntToSizeT(jint jintValue)
{
    if (jintValue < 0)
    {
        RiveLogW("RiveLN/JIntToSizeT",
                 "Value is a negative number %d",
                 jintValue);
        return 0;
    }
    return static_cast<size_t>(jintValue);
}

JniResource<jintArray> VecUInt32ToJIntArray(JNIEnv* env,
                                            const std::vector<uint32_t>& values)
{
    auto jArray = env->NewIntArray(static_cast<jsize>(values.size()));
    if (jArray == nullptr)
    {
        return {nullptr, env};
    }

    std::vector<jint> ints(values.begin(), values.end());
    env->SetIntArrayRegion(jArray,
                           0,
                           static_cast<jsize>(ints.size()),
                           ints.data());
    return {jArray, env};
}
} // namespace rive_android

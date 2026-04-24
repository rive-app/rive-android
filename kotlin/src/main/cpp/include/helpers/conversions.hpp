#pragma once

#include <cstddef>
#include <cstdint>
#include <jni.h>
#include <string>
#include <vector>

#include "helpers/jni_resource.hpp"

namespace rive_android
{
/**
 * Converts a JVM UTF-16 string reference into a native UTF-8 std::string.
 *
 * @param env JNI environment used for string access/release.
 * @param jStringValue JVM string to convert; may be null.
 * @return Converted std::string, or empty string when input is null.
 */
std::string JStringToString(JNIEnv* env, jstring jStringValue);

/**
 * Safely narrows a size_t value into int by clamping to INT_MAX.
 *
 * @param size Unsigned size value to convert.
 * @return Converted int value, clamped to INT_MAX on overflow.
 */
int SizeTToInt(size_t size);

/**
 * Safely widens a JVM int into size_t with a lower-bound check.
 *
 * @param jintValue JVM int value to convert.
 * @return 0 for negative input, otherwise cast value.
 */
size_t JIntToSizeT(jint jintValue);

/**
 * Converts a vector of uint32 values into a JVM int[] local reference.
 *
 * @param env JNI environment used to allocate/fill the JVM array.
 * @param values Native values to copy into the JVM int array.
 * @return Managed JNI local reference wrapping the created jintArray.
 */
JniResource<jintArray> VecUInt32ToJIntArray(
    JNIEnv* env,
    const std::vector<uint32_t>& values);
} // namespace rive_android

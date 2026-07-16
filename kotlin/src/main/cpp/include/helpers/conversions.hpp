#pragma once

#include <cstddef>
#include <cstdint>
#include <jni.h>
#include <vector>

#include "helpers/jni_resource.hpp"

namespace rive_android
{
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

#pragma once

#include <jni.h>
#include <string>

#include "helpers/jni_resource.hpp"

namespace rive_android
{
/**
 * Converts a JVM UTF-16 string reference into a native standard UTF-8 string.
 *
 * Unpaired UTF-16 surrogates are replaced with U+FFFD.
 *
 * @param env JNI environment used for string access and release.
 * @param jString JVM string exposed by JNI as UTF-16 code units; may be null.
 * @return Native standard UTF-8 string, or an empty string when input is null
 * or string access fails.
 */
std::string JStringToString(JNIEnv* env, jstring jString);

/**
 * Creates a JVM string through JNI's UTF-16 interface from a null-terminated
 * standard UTF-8 string.
 *
 * Invalid UTF-8 bytes are replaced with U+FFFD.
 *
 * @param env JNI environment used to create the string.
 * @param utf8 Null-terminated native standard UTF-8 string; may be null.
 * @return Managed JNI local reference to a JVM string constructed from UTF-16
 * code units, or null for null input or allocation failure.
 */
JniResource<jstring> MakeJString(JNIEnv* env, const char* utf8);

/**
 * Creates a JVM string through JNI's UTF-16 interface from a standard UTF-8
 * string.
 *
 * Invalid UTF-8 bytes are replaced with U+FFFD. Embedded null characters are
 * preserved.
 *
 * @param env JNI environment used to create the string.
 * @param utf8 Native standard UTF-8 string to convert.
 * @return Managed JNI local reference to a JVM string constructed from UTF-16
 * code units, or null on allocation failure.
 */
JniResource<jstring> MakeJString(JNIEnv* env, const std::string& utf8);
} // namespace rive_android

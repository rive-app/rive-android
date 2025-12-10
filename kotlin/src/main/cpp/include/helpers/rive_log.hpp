#pragma once

#include <jni.h>
#include <cstdarg>
#include <mutex>

namespace rive_android
{

/**
 * Initializes the RiveLog helper by caching class and method IDs.
 * This should be called once during JNI initialization.
 * Thread-safe.
 */
void InitializeRiveLog();

/**
 * Logging functions that call the Kotlin RiveLog infrastructure.
 * These functions format the message in C++ and then call the Kotlin RiveLog
 * methods, which respect the configured logger.
 *
 * All functions are thread-safe and cache JNI class/method IDs for efficiency.
 */
void RiveLogV(const char* tag, const char* format, ...);
void RiveLogD(const char* tag, const char* format, ...);
void RiveLogI(const char* tag, const char* format, ...);
void RiveLogW(const char* tag, const char* format, ...);
void RiveLogE(const char* tag, const char* format, ...);

} // namespace rive_android

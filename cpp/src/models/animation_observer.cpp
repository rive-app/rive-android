#include "helpers/general.hpp"
#include "models/animation_observer.hpp"
#include <stdio.h>
#include <android/log.h>

using namespace rive_android;

#define android_log(str) __android_log_print(ANDROID_LOG_INFO, __FILE__, str)

// Initialize static fields.
jclass AnimationObserver::jvmClass = nullptr;
jfieldID AnimationObserver::addressField = nullptr;
jmethodID AnimationObserver::jOnFinished = nullptr;
jmethodID AnimationObserver::jOnLoop = nullptr;
jmethodID AnimationObserver::jOnPingPong = nullptr;
std::vector<AnimationObserver *> AnimationObserver::m_RegisteredObservers = {};

void AnimationObserver::onFinished()
{
    android_log("Animation finished!");
}

void AnimationObserver::onLoop()
{
    android_log("Animation looped!");
    globalJNIEnv->CallVoidMethod(m_Observer, jOnLoop);
}

void AnimationObserver::onPingPong()
{
    android_log("Animation ping-pong'd!");
}
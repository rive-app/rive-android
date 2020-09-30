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

void AnimationObserver::onFinished(std::string const &animationName)
{
    jstring jName = globalJNIEnv->NewStringUTF(animationName.c_str());
    globalJNIEnv->CallVoidMethod(m_Observer, jOnFinished, jName);
}

void AnimationObserver::onLoop(std::string const &animationName)
{
    jstring jName = globalJNIEnv->NewStringUTF(animationName.c_str());
    globalJNIEnv->CallVoidMethod(m_Observer, jOnLoop, jName);
}

void AnimationObserver::onPingPong(std::string const &animationName)
{
    jstring jName = globalJNIEnv->NewStringUTF(animationName.c_str());
    globalJNIEnv->CallVoidMethod(m_Observer, jOnPingPong, jName);
}
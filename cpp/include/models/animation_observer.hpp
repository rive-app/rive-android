#ifndef _RIVE_ANIMATION_OBSERVER_HPP_
#define _RIVE_ANIMATION_OBSERVER_HPP_

#include "models/animation_observer.hpp"

// From rive-cpp
#include "animation/linear_animation_instance.hpp"
//

#include <jni.h>
#include <stdio.h>
#include <android/log.h>

#define android_log(str) __android_log_print(ANDROID_LOG_INFO, __FILE__, str)

namespace rive_android
{
    class AnimationObserver : public rive::IAnimationObserver
    {
    private:
        static std::vector<AnimationObserver *> m_RegisteredObservers;
        jobject m_Observer;

    public:
        static jfieldID addressField;
        static jclass jvmClass;
        static jmethodID jOnLoop;
        static jmethodID jOnFinished;
        static jmethodID jOnPingPong;

        static void jniInit(JNIEnv *env)
        {
            android_log("Init'ing jni!");
            auto jObserverClass = env->FindClass("app/rive/runtime/kotlin/AnimationObserver");

            jvmClass = (jclass)env->NewGlobalRef(jObserverClass);
            addressField = env->GetFieldID(jvmClass, "address", "J");

            jOnFinished = env->GetMethodID(jvmClass, "onFinished", "()V");
            jOnLoop = env->GetMethodID(jvmClass, "onLoop", "()V");
            jOnPingPong = env->GetMethodID(jvmClass, "onPingPong", "()V");
            __android_log_print(ANDROID_LOG_INFO, __FILE__, "I'm not sure here %p", jOnLoop);
        }

        static void jniDispose(JNIEnv *env)
        {
            env->DeleteGlobalRef(jvmClass);
            for (auto obs : m_RegisteredObservers)
            {
                env->DeleteGlobalRef(obs->m_Observer);
                delete obs;
            }
        }

        AnimationObserver(JNIEnv *env, jobject jObserver)
        {
            m_Observer = env->NewGlobalRef(jObserver);
            m_RegisteredObservers.push_back(this);
        }

        ~AnimationObserver() {}

        void onFinished();
        void onLoop();
        void onPingPong();
    };
} // namespace rive_android

#endif
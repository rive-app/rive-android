#include <jni.h>

#include "rive/bindable_artboard.hpp"

extern "C"
{
    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_BindableArtboard_cppDelete(JNIEnv*,
                                                                 jobject,
                                                                 jlong ref)
    {
        auto bindableArtboard = reinterpret_cast<rive::BindableArtboard*>(ref);
        // Because bindable artboards are created as rcp types that have been
        // released, we have an extra ref count to unref here.
        bindableArtboard->unref();
    }

    JNIEXPORT jstring JNICALL
    Java_app_rive_runtime_kotlin_core_BindableArtboard_cppName(JNIEnv* env,
                                                               jobject,
                                                               jlong ref)
    {
        auto bindableArtboard = reinterpret_cast<rive::BindableArtboard*>(ref);
        auto name = bindableArtboard->artboard()->name();
        return env->NewStringUTF(name.c_str());
    }
}

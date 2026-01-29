#include <jni.h>

#include "helpers/audio_engine.hpp"

extern "C"
{
    JNIEXPORT void JNICALL Java_app_rive_core_AudioEngine_acquire(JNIEnv*,
                                                                  jobject)
    {
        rive_android::AudioEngine::Instance().acquire();
    }

    JNIEXPORT void JNICALL Java_app_rive_core_AudioEngine_release(JNIEnv*,
                                                                  jobject)
    {
        rive_android::AudioEngine::Instance().release();
    }
}

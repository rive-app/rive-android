#include <jni.h>
#include <string>

#include "helpers/jni_string.hpp"
#include "rive/text/text_value_run.hpp"

#ifdef __cplusplus
extern "C"
{
#endif

    using namespace rive_android;

    JNIEXPORT jstring JNICALL
    Java_app_rive_runtime_kotlin_core_RiveTextValueRun_cppText(JNIEnv* env,
                                                               jobject,
                                                               jlong ref)
    {
        auto* run = reinterpret_cast<rive::TextValueRun*>(ref);
        return MakeJString(env, run->text()).release();
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_RiveTextValueRun_cppSetText(JNIEnv* env,
                                                                  jobject,
                                                                  jlong ref,
                                                                  jstring name)
    {
        auto* run = reinterpret_cast<rive::TextValueRun*>(ref);
        run->text(JStringToString(env, name));
    }

#ifdef __cplusplus
}
#endif

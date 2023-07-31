#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "rive/text/text_value_run.hpp"
#include <jni.h>

#ifdef __cplusplus
extern "C"
{
#endif

    using namespace rive_android;

    JNIEXPORT jstring JNICALL
    Java_app_rive_runtime_kotlin_core_RiveTextValueRun_cppText(JNIEnv* env,
                                                               jobject thisObj,
                                                               jlong ref)
    {
        rive::TextValueRun* run = reinterpret_cast<rive::TextValueRun*>(ref);
        return env->NewStringUTF(run->text().c_str());
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_RiveTextValueRun_cppSetText(JNIEnv* env,
                                                                  jobject thisObj,
                                                                  jlong ref,
                                                                  jstring name)
    {
        rive::TextValueRun* run = reinterpret_cast<rive::TextValueRun*>(ref);
        run->text(JStringToString(env, name));
    }

#ifdef __cplusplus
}
#endif

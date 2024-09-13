#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "models/jni_renderer.hpp"
#include "rive/artboard.hpp"
#include "rive/animation/linear_animation_instance.hpp"
#include "rive/animation/state_machine_instance.hpp"
#include "rive/text/text_value_run.hpp"
#include <jni.h>
#include <stdio.h>

#ifdef __cplusplus
extern "C"
{
#endif

    using namespace rive_android;

    // ARTBOARD

    JNIEXPORT jstring JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppName(JNIEnv* env,
                                                                                 jobject thisObj,
                                                                                 jlong ref)
    {
        auto artboard = reinterpret_cast<rive::ArtboardInstance*>(ref);
        return env->NewStringUTF(artboard->name().c_str());
    }

    JNIEXPORT jstring JNICALL
    Java_app_rive_runtime_kotlin_core_Artboard_cppAnimationNameByIndex(JNIEnv* env,
                                                                       jobject,
                                                                       jlong ref,
                                                                       jint index)
    {
        auto artboard = reinterpret_cast<rive::ArtboardInstance*>(ref);

        rive::LinearAnimation* animation = artboard->animation(index);
        auto name = animation->name();

        return env->NewStringUTF(name.c_str());
    }

    JNIEXPORT jstring JNICALL
    Java_app_rive_runtime_kotlin_core_Artboard_cppStateMachineNameByIndex(JNIEnv* env,
                                                                          jobject,
                                                                          jlong ref,
                                                                          jint index)
    {
        auto artboard = reinterpret_cast<rive::ArtboardInstance*>(ref);

        rive::StateMachine* stateMachine = artboard->stateMachine(index);
        auto name = stateMachine->name();

        return env->NewStringUTF(name.c_str());
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_Artboard_cppAnimationByIndex(JNIEnv* env,
                                                                   jobject thisObj,
                                                                   jlong ref,
                                                                   jint index)
    {
        auto artboard = reinterpret_cast<rive::ArtboardInstance*>(ref);
        // Creates a new instance.
        return (jlong)artboard->animationAt(index).release();
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_Artboard_cppAnimationByName(JNIEnv* env,
                                                                  jobject thisObj,
                                                                  jlong ref,
                                                                  jstring name)
    {
        auto artboard = reinterpret_cast<rive::ArtboardInstance*>(ref);
        // Creates a new instance.
        return (jlong)artboard->animationNamed(JStringToString(env, name)).release();
    }

    JNIEXPORT jint JNICALL
    Java_app_rive_runtime_kotlin_core_Artboard_cppAnimationCount(JNIEnv* env,
                                                                 jobject thisObj,
                                                                 jlong ref)
    {
        auto artboard = reinterpret_cast<rive::ArtboardInstance*>(ref);

        return (jint)artboard->animationCount();
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_Artboard_cppStateMachineByIndex(JNIEnv* env,
                                                                      jobject thisObj,
                                                                      jlong ref,
                                                                      jint index)
    {
        auto artboard = reinterpret_cast<rive::ArtboardInstance*>(ref);
        // Creates a new instance.
        return (jlong)artboard->stateMachineAt(index).release();
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_Artboard_cppStateMachineByName(JNIEnv* env,
                                                                     jobject thisObj,
                                                                     jlong ref,
                                                                     jstring name)
    {
        auto artboard = reinterpret_cast<rive::ArtboardInstance*>(ref);
        // Creates a new instance.

        return (jlong)artboard->stateMachineNamed(JStringToString(env, name)).release();
    }

    JNIEXPORT jint JNICALL
    Java_app_rive_runtime_kotlin_core_Artboard_cppStateMachineCount(JNIEnv* env,
                                                                    jobject thisObj,
                                                                    jlong ref)
    {
        auto artboard = reinterpret_cast<rive::ArtboardInstance*>(ref);

        return (jint)artboard->stateMachineCount();
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_Artboard_cppInputByNameAtPath(JNIEnv* env,
                                                                    jobject thisObj,
                                                                    jlong ref,
                                                                    jstring name,
                                                                    jstring path)
    {
        auto artboard = reinterpret_cast<rive::ArtboardInstance*>(ref);
        return (jlong)artboard->input(JStringToString(env, name), JStringToString(env, path));
    }

    JNIEXPORT jfloat JNICALL
    Java_app_rive_runtime_kotlin_core_Artboard_cppGetVolume(JNIEnv* env, jobject thisObj, jlong ref)
    {
        auto artboard = reinterpret_cast<rive::ArtboardInstance*>(ref);
        return artboard->volume();
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppSetVolume(JNIEnv* env,
                                                                                   jobject thisObj,
                                                                                   jlong ref,
                                                                                   jfloat volume)
    {
        auto artboard = reinterpret_cast<rive::ArtboardInstance*>(ref);
        artboard->volume(volume);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_Artboard_cppFindTextValueRun(JNIEnv* env,
                                                                   jobject thisObj,
                                                                   jlong ref,
                                                                   jstring name)
    {
        auto artboard = reinterpret_cast<rive::ArtboardInstance*>(ref);
        return (jlong)artboard->find<rive::TextValueRun>(JStringToString(env, name));
    }

    JNIEXPORT jstring JNICALL
    Java_app_rive_runtime_kotlin_core_Artboard_cppFindValueOfTextValueRun(JNIEnv* env,
                                                                          jobject thisObj,
                                                                          jlong ref,
                                                                          jstring name)
    {
        auto artboard = reinterpret_cast<rive::ArtboardInstance*>(ref);
        auto run = artboard->find<rive::TextValueRun>(JStringToString(env, name));
        if (run == nullptr)
        {
            return nullptr;
        }
        return env->NewStringUTF(run->text().c_str());
    }

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_core_Artboard_cppSetValueOfTextValueRun(JNIEnv* env,
                                                                         jobject thisObj,
                                                                         jlong ref,
                                                                         jstring name,
                                                                         jstring newText)
    {
        auto artboard = reinterpret_cast<rive::ArtboardInstance*>(ref);
        auto run = artboard->find<rive::TextValueRun>(JStringToString(env, name));
        if (run == nullptr)
        {
            return JNI_FALSE;
        }
        run->text(JStringToString(env, newText));
        return JNI_TRUE;
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_Artboard_cppFindTextValueRunAtPath(JNIEnv* env,
                                                                         jobject thisObj,
                                                                         jlong ref,
                                                                         jstring name,
                                                                         jstring path)
    {
        auto artboard = reinterpret_cast<rive::ArtboardInstance*>(ref);
        return (jlong)artboard->getTextRun(JStringToString(env, name), JStringToString(env, path));
    }

    JNIEXPORT jstring JNICALL
    Java_app_rive_runtime_kotlin_core_Artboard_cppFindValueOfTextValueRunAtPath(JNIEnv* env,
                                                                                jobject thisObj,
                                                                                jlong ref,
                                                                                jstring name,
                                                                                jstring path)
    {
        auto artboard = reinterpret_cast<rive::ArtboardInstance*>(ref);
        auto run = artboard->getTextRun(JStringToString(env, name), JStringToString(env, path));
        if (run == nullptr)
        {
            return nullptr;
        }
        return env->NewStringUTF(run->text().c_str());
    }

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_core_Artboard_cppSetValueOfTextValueRunAtPath(JNIEnv* env,
                                                                               jobject thisObj,
                                                                               jlong ref,
                                                                               jstring name,
                                                                               jstring newText,
                                                                               jstring path)
    {
        auto artboard = reinterpret_cast<rive::ArtboardInstance*>(ref);
        auto run = artboard->getTextRun(JStringToString(env, name), JStringToString(env, path));
        if (run == nullptr)
        {
            return JNI_FALSE;
        }
        run->text(JStringToString(env, newText));
        return JNI_TRUE;
    }

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_core_Artboard_cppAdvance(JNIEnv* env,
                                                          jobject thisObj,
                                                          jlong ref,
                                                          jfloat elapsedTime)
    {
        auto artboard = reinterpret_cast<rive::ArtboardInstance*>(ref);
        return artboard->advance(elapsedTime);
    }

    JNIEXPORT jobject JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppBounds(JNIEnv* env,
                                                                                   jobject thisObj,
                                                                                   jlong ref)
    {
        auto cls = env->FindClass("android/graphics/RectF");
        auto constructor = env->GetMethodID(cls, "<init>", "(FFFF)V");
        const auto bounds = (reinterpret_cast<rive::ArtboardInstance*>(ref))->bounds();
        auto res = env->NewObject(cls,
                                  constructor,
                                  bounds.left(),
                                  bounds.top(),
                                  bounds.right(),
                                  bounds.bottom());
        env->DeleteLocalRef(cls);
        return res;
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppDraw(JNIEnv* env,
                                                                              jobject,
                                                                              jlong artboardRef,
                                                                              jlong rendererRef)
    {
        auto artboard = reinterpret_cast<rive::ArtboardInstance*>(artboardRef);
        auto jniWrapper = reinterpret_cast<JNIRenderer*>(rendererRef);
        rive::Renderer* renderer = jniWrapper->getRendererOnWorkerThread();
        artboard->draw(renderer);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_Artboard_cppDrawAligned(JNIEnv* env,
                                                              jobject,
                                                              jlong artboardRef,
                                                              jlong rendererRef,
                                                              jobject ktFit,
                                                              jobject ktAlignment)
    {
        auto artboard = reinterpret_cast<rive::ArtboardInstance*>(artboardRef);
        auto jniWrapper = reinterpret_cast<JNIRenderer*>(rendererRef);
        rive::Renderer* renderer = jniWrapper->getRendererOnWorkerThread();

        rive::Fit fit = GetFit(env, ktFit);
        rive::Alignment alignment = GetAlignment(env, ktAlignment);

        renderer->save();
        renderer->align(fit,
                        alignment,
                        rive::AABB(0, 0, jniWrapper->width(), jniWrapper->height()),
                        artboard->bounds());
        artboard->draw(renderer);
        renderer->restore();
    }

    JNIEXPORT void JNICALL Java_app_rive_runtime_kotlin_core_Artboard_cppDelete(JNIEnv* env,
                                                                                jobject thisObj,
                                                                                jlong ref)
    {
        auto artboard = reinterpret_cast<rive::ArtboardInstance*>(ref);
        delete artboard;
    }

#ifdef __cplusplus
}
#endif

#include <jni.h>

#include "helpers/jni_resource.hpp"
#include "models/jni_renderer.hpp"
#include "rive/animation/linear_animation_instance.hpp"
#include "rive/animation/state_machine_instance.hpp"
#include "rive/artboard.hpp"
#include "rive/text/text_value_run.hpp"
#include "rive/viewmodel/runtime/viewmodel_instance_number_runtime.hpp"
#include "rive/viewmodel/runtime/viewmodel_instance_runtime.hpp"
#include "rive/viewmodel/runtime/viewmodel_runtime.hpp"

#ifdef __cplusplus
extern "C"
{
#endif
    using namespace rive_android;

    // ViewModel

    JNIEXPORT jstring JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModel_cppName(JNIEnv* env,
                                                        jobject,
                                                        jlong ref)
    {
        auto vm = reinterpret_cast<rive::ViewModelRuntime*>(ref);
        return env->NewStringUTF(vm->name().c_str());
    }

    JNIEXPORT jint JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModel_cppInstanceCount(JNIEnv*,
                                                                 jobject,
                                                                 jlong ref)
    {
        auto vm = reinterpret_cast<rive::ViewModelRuntime*>(ref);
        return static_cast<jint>(vm->instanceCount());
    }

    JNIEXPORT jint JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModel_cppPropertyCount(JNIEnv*,
                                                                 jobject,
                                                                 jlong ref)
    {
        auto vm = reinterpret_cast<rive::ViewModelRuntime*>(ref);
        return static_cast<jint>(vm->propertyCount());
    }

    JNIEXPORT jobject JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModel_cppGetProperties(JNIEnv* env,
                                                                 jobject,
                                                                 jlong ref)
    {
        auto vm = reinterpret_cast<rive::ViewModelRuntime*>(ref);
        auto properties = vm->properties();

        auto arrayListClass = FindClass(env, "java/util/ArrayList");
        auto arrayListConstructor =
            env->GetMethodID(arrayListClass.get(), "<init>", "()V");
        auto arrayListAddFn = env->GetMethodID(arrayListClass.get(),
                                               "add",
                                               "(Ljava/lang/Object;)Z");

        auto propertyClass =
            FindClass(env, "app/rive/runtime/kotlin/core/ViewModel$Property");
        auto constructor =
            env->GetMethodID(propertyClass.get(),
                             "<init>",
                             "(Lapp/rive/runtime/kotlin/core/"
                             "ViewModel$PropertyDataType;Ljava/lang/String;)V");

        auto dataTypeClass = FindClass(
            env,
            "app/rive/runtime/kotlin/core/ViewModel$PropertyDataType");
        auto fromIntFn = env->GetStaticMethodID(
            dataTypeClass.get(),
            "fromInt",
            "(I)Lapp/rive/runtime/kotlin/core/ViewModel$PropertyDataType;");

        auto propertyList =
            MakeObject(env, arrayListClass.get(), arrayListConstructor);

        for (const auto& property : properties)
        {
            auto name = MakeJString(env, property.name);
            auto dataTypeObject = JniResource(
                env->CallStaticObjectMethod(dataTypeClass.get(),
                                            fromIntFn,
                                            static_cast<jint>(property.type)),
                env);
            auto propertyObject = MakeObject(env,
                                             propertyClass.get(),
                                             constructor,
                                             dataTypeObject.get(),
                                             name.get());

            env->CallBooleanMethod(propertyList.get(),
                                   arrayListAddFn,
                                   propertyObject.get());
        }

        // release() to avoid RAII cleanup of the local reference
        return propertyList.release();
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModel_cppCreateBlankInstance(
        JNIEnv*,
        jobject,
        jlong ref)
    {
        auto vm = reinterpret_cast<rive::ViewModelRuntime*>(ref);
        return reinterpret_cast<jlong>(vm->createInstance());
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModel_cppCreateDefaultInstance(
        JNIEnv*,
        jobject,
        jlong ref)
    {
        auto vm = reinterpret_cast<rive::ViewModelRuntime*>(ref);
        return reinterpret_cast<jlong>(vm->createDefaultInstance());
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModel_cppCreateInstanceFromIndex(
        JNIEnv*,
        jobject,
        jlong ref,
        jint index)
    {
        auto vm = reinterpret_cast<rive::ViewModelRuntime*>(ref);
        return (jlong)vm->createInstanceFromIndex(index);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModel_cppCreateInstanceFromName(
        JNIEnv* env,
        jobject,
        jlong ref,
        jstring name)
    {
        auto vm = reinterpret_cast<rive::ViewModelRuntime*>(ref);
        return (jlong)vm->createInstanceFromName(JStringToString(env, name));
    }

    // ViewModelInstance

    JNIEXPORT jstring JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelInstance_cppName(JNIEnv* env,
                                                                jobject,
                                                                jlong ref)
    {
        auto vm = reinterpret_cast<rive::ViewModelInstanceRuntime*>(ref);
        return env->NewStringUTF(vm->name().c_str());
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelInstance_cppPropertyNumber(
        JNIEnv* env,
        jobject,
        jlong ref,
        jstring path)
    {
        auto vmi = reinterpret_cast<rive::ViewModelInstanceRuntime*>(ref);
        auto nativePath = JStringToString(env, path);
        return (jlong)vmi->propertyNumber(nativePath);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelInstance_cppPropertyString(
        JNIEnv* env,
        jobject,
        jlong ref,
        jstring path)
    {
        auto vmi = reinterpret_cast<rive::ViewModelInstanceRuntime*>(ref);
        auto nativePath = JStringToString(env, path);
        return (jlong)vmi->propertyString(nativePath);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelInstance_cppPropertyBoolean(
        JNIEnv* env,
        jobject,
        jlong ref,
        jstring path)
    {
        auto vmi = reinterpret_cast<rive::ViewModelInstanceRuntime*>(ref);
        auto nativePath = JStringToString(env, path);
        return (jlong)vmi->propertyBoolean(nativePath);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelInstance_cppPropertyColor(
        JNIEnv* env,
        jobject,
        jlong ref,
        jstring path)
    {
        auto vmi = reinterpret_cast<rive::ViewModelInstanceRuntime*>(ref);
        auto nativePath = JStringToString(env, path);
        return (jlong)vmi->propertyColor(nativePath);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelInstance_cppPropertyEnum(
        JNIEnv* env,
        jobject,
        jlong ref,
        jstring path)
    {
        auto vmi = reinterpret_cast<rive::ViewModelInstanceRuntime*>(ref);
        auto nativePath = JStringToString(env, path);
        return (jlong)vmi->propertyEnum(nativePath);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelInstance_cppPropertyTrigger(
        JNIEnv* env,
        jobject,
        jlong ref,
        jstring path)
    {
        auto vmi = reinterpret_cast<rive::ViewModelInstanceRuntime*>(ref);
        auto nativePath = JStringToString(env, path);
        return (jlong)vmi->propertyTrigger(nativePath);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelInstance_cppPropertyImage(
        JNIEnv* env,
        jobject,
        jlong ref,
        jstring path)
    {
        auto vmi = reinterpret_cast<rive::ViewModelInstanceRuntime*>(ref);
        auto nativePath = JStringToString(env, path);
        return (jlong)vmi->propertyImage(nativePath);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelInstance_cppPropertyList(
        JNIEnv* env,
        jobject,
        jlong ref,
        jstring path)
    {
        auto vmi = reinterpret_cast<rive::ViewModelInstanceRuntime*>(ref);
        auto nativePath = JStringToString(env, path);
        return (jlong)vmi->propertyList(nativePath);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelInstance_cppPropertyArtboard(
        JNIEnv* env,
        jobject,
        jlong ref,
        jstring path)
    {
        auto vmi = reinterpret_cast<rive::ViewModelInstanceRuntime*>(ref);
        auto nativePath = JStringToString(env, path);
        return (jlong)vmi->propertyArtboard(nativePath);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelInstance_cppPropertyInstance(
        JNIEnv* env,
        jobject,
        jlong ref,
        jstring path)
    {
        auto vmi = reinterpret_cast<rive::ViewModelInstanceRuntime*>(ref);
        auto nativePath = JStringToString(env, path);
        return reinterpret_cast<jlong>(vmi->propertyViewModel(nativePath));
    }

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelInstance_cppSetInstanceProperty(
        JNIEnv* env,
        jobject,
        jlong ref,
        jstring path,
        jlong propertyRef)
    {
        auto vmi = reinterpret_cast<rive::ViewModelInstanceRuntime*>(ref);
        auto property =
            reinterpret_cast<rive::ViewModelInstanceRuntime*>(propertyRef);
        return vmi->replaceViewModel(JStringToString(env, path), property);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelInstance_cppRefInstance(
        JNIEnv*,
        jobject,
        jlong ref)
    {
        auto vmi = reinterpret_cast<rive::ViewModelInstanceRuntime*>(ref);
        vmi->ref();
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelInstance_cppDerefInstance(
        JNIEnv*,
        jobject,
        jlong ref)
    {
        auto vmi = reinterpret_cast<rive::ViewModelInstanceRuntime*>(ref);
        vmi->unref();
    }

    // Properties

    JNIEXPORT jstring JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelProperty_cppName(JNIEnv* env,
                                                                jobject,
                                                                jlong ref)
    {
        auto property =
            reinterpret_cast<rive::ViewModelInstanceValueRuntime*>(ref);
        return env->NewStringUTF(property->name().c_str());
    }

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelProperty_cppHasChanged(JNIEnv*,
                                                                      jobject,
                                                                      jlong ref)
    {
        auto property =
            reinterpret_cast<rive::ViewModelInstanceValueRuntime*>(ref);
        return property->hasChanged();
    }

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelProperty_cppFlushChanges(
        JNIEnv*,
        jobject,
        jlong ref)
    {
        auto property =
            reinterpret_cast<rive::ViewModelInstanceValueRuntime*>(ref);
        return property->flushChanges();
    }

    JNIEXPORT jfloat JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelNumberProperty_cppGetValue(
        JNIEnv*,
        jobject,
        jlong ref)
    {
        auto property =
            reinterpret_cast<rive::ViewModelInstanceNumberRuntime*>(ref);
        return property->value();
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelNumberProperty_cppSetValue(
        JNIEnv*,
        jobject,
        jlong ref,
        jfloat value)
    {
        auto property =
            reinterpret_cast<rive::ViewModelInstanceNumberRuntime*>(ref);
        property->value(value);
    }

    JNIEXPORT jstring JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelStringProperty_cppGetValue(
        JNIEnv* env,
        jobject,
        jlong ref)
    {
        auto property =
            reinterpret_cast<rive::ViewModelInstanceStringRuntime*>(ref);
        return env->NewStringUTF(property->value().c_str());
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelStringProperty_cppSetValue(
        JNIEnv* env,
        jobject,
        jlong ref,
        jstring value)
    {
        auto property =
            reinterpret_cast<rive::ViewModelInstanceStringRuntime*>(ref);
        property->value(JStringToString(env, value));
    }

    JNIEXPORT jboolean JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelBooleanProperty_cppGetValue(
        JNIEnv*,
        jobject,
        jlong ref)
    {
        auto property =
            reinterpret_cast<rive::ViewModelInstanceBooleanRuntime*>(ref);
        return property->value();
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelBooleanProperty_cppSetValue(
        JNIEnv*,
        jobject,
        jlong ref,
        jboolean value)
    {
        auto property =
            reinterpret_cast<rive::ViewModelInstanceBooleanRuntime*>(ref);
        property->value(value);
    }

    JNIEXPORT jint JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelColorProperty_cppGetValue(
        JNIEnv*,
        jobject,
        jlong ref)
    {
        auto property =
            reinterpret_cast<rive::ViewModelInstanceColorRuntime*>(ref);
        return property->value();
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelColorProperty_cppSetValue(
        JNIEnv*,
        jobject,
        jlong ref,
        jint value)
    {
        auto property =
            reinterpret_cast<rive::ViewModelInstanceColorRuntime*>(ref);
        property->value(value);
    }

    JNIEXPORT jstring JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelEnumProperty_cppGetValue(
        JNIEnv* env,
        jobject,
        jlong ref)
    {
        auto property =
            reinterpret_cast<rive::ViewModelInstanceEnumRuntime*>(ref);
        return env->NewStringUTF(property->value().c_str());
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelEnumProperty_cppSetValue(
        JNIEnv* env,
        jobject,
        jlong ref,
        jstring value)
    {
        auto property =
            reinterpret_cast<rive::ViewModelInstanceEnumRuntime*>(ref);
        property->value(JStringToString(env, value));
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelTriggerProperty_cppTrigger(
        JNIEnv*,
        jobject,
        jlong ref)
    {
        auto property =
            reinterpret_cast<rive::ViewModelInstanceTriggerRuntime*>(ref);
        property->trigger();
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelImageProperty_cppSetValue(
        JNIEnv*,
        jobject,
        jlong ref,
        jlong imageRef)
    {
        auto property =
            reinterpret_cast<rive::ViewModelInstanceAssetImageRuntime*>(ref);
        auto image = reinterpret_cast<rive::RenderImage*>(imageRef);
        property->value(image);
    }

    JNIEXPORT jint JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelListProperty_cppSize(JNIEnv*,
                                                                    jobject,
                                                                    jlong ref)
    {
        auto property =
            reinterpret_cast<rive::ViewModelInstanceListRuntime*>(ref);
        return static_cast<jint>(property->size());
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelListProperty_cppElementAt(
        JNIEnv*,
        jobject,
        jlong ref,
        jint index)
    {
        auto property =
            reinterpret_cast<rive::ViewModelInstanceListRuntime*>(ref);
        return (jlong)property->instanceAt(index);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelListProperty_cppAdd(
        JNIEnv*,
        jobject,
        jlong ref,
        jlong itemRef)
    {
        auto property =
            reinterpret_cast<rive::ViewModelInstanceListRuntime*>(ref);
        auto item = reinterpret_cast<rive::ViewModelInstanceRuntime*>(itemRef);
        property->addInstance(item);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelListProperty_cppAddAt(
        JNIEnv*,
        jobject,
        jlong ref,
        jint index,
        jlong itemRef)
    {
        auto property =
            reinterpret_cast<rive::ViewModelInstanceListRuntime*>(ref);
        auto item = reinterpret_cast<rive::ViewModelInstanceRuntime*>(itemRef);
        property->addInstanceAt(item, index);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelListProperty_cppRemove(
        JNIEnv*,
        jobject,
        jlong ref,
        jlong itemRef)
    {
        auto property =
            reinterpret_cast<rive::ViewModelInstanceListRuntime*>(ref);
        auto item = reinterpret_cast<rive::ViewModelInstanceRuntime*>(itemRef);
        property->removeInstance(item);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelListProperty_cppRemoveAt(
        JNIEnv*,
        jobject,
        jlong ref,
        jint index)
    {
        auto property =
            reinterpret_cast<rive::ViewModelInstanceListRuntime*>(ref);
        property->removeInstanceAt(index);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelListProperty_cppSwap(JNIEnv*,
                                                                    jobject,
                                                                    jlong ref,
                                                                    jint indexA,
                                                                    jint indexB)
    {
        auto property =
            reinterpret_cast<rive::ViewModelInstanceListRuntime*>(ref);
        property->swap(indexA, indexB);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_ViewModelArtboardProperty_cppSetValue(
        JNIEnv*,
        jobject,
        jlong ref,
        jlong artboardRef)
    {
        auto property =
            reinterpret_cast<rive::ViewModelInstanceArtboardRuntime*>(ref);
        auto artboard = reinterpret_cast<rive::Artboard*>(artboardRef);
        property->value(artboard);
    }

#ifdef __cplusplus
}
#endif

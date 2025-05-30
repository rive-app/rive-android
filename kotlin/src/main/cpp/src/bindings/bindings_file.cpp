#include <jni.h>

#include "helpers/general.hpp"
#include "helpers/jni_resource.hpp"
#include "jni_refs.hpp"
#include "rive/file.hpp"
#include "rive/viewmodel/runtime/viewmodel_runtime.hpp"
#include "rive/viewmodel/viewmodel.hpp"

#ifdef __cplusplus
extern "C"
{
#endif
    using namespace rive_android;

    // FILE
    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_File_import(JNIEnv* env,
                                                  jobject thisObj,
                                                  jbyteArray bytes,
                                                  jint length,
                                                  jint type,
                                                  jlong fileAssetLoader)
    {
        RendererType rendererType = static_cast<RendererType>(type);
        rive::FileAssetLoader* assetLoader =
            reinterpret_cast<rive::FileAssetLoader*>(fileAssetLoader);

        jbyte* byte_array = env->GetByteArrayElements(bytes, NULL);
        long file = Import(reinterpret_cast<uint8_t*>(byte_array),
                           length,
                           rendererType,
                           assetLoader);
        env->ReleaseByteArrayElements(bytes, byte_array, JNI_ABORT);
        return (jlong)file;
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_File_cppArtboardByName(JNIEnv* env,
                                                             jobject thisObj,
                                                             jlong ref,
                                                             jstring name)
    {
        auto file = reinterpret_cast<rive::File*>(ref);
        // Creates a new Artboard instance.
        auto artboard = file->artboardNamed(JStringToString(env, name));
        if (artboard != nullptr)
        {
            artboard->advance(0.0);
        }
        return (jlong)artboard.release();
    }

    JNIEXPORT void JNICALL
    Java_app_rive_runtime_kotlin_core_File_cppDelete(JNIEnv* env,
                                                     jobject thisObj,
                                                     jlong ref)
    {
        // if we're wiping the file, we really should wipe all those refs?
        auto file = reinterpret_cast<rive::File*>(ref);
        delete file;
    }

    JNIEXPORT jint JNICALL
    Java_app_rive_runtime_kotlin_core_File_cppArtboardCount(JNIEnv* env,
                                                            jobject thisObj,
                                                            jlong ref)
    {
        auto file = reinterpret_cast<rive::File*>(ref);

        return (jint)file->artboardCount();
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_File_cppArtboardByIndex(JNIEnv* env,
                                                              jobject thisObj,
                                                              jlong ref,
                                                              jint index)
    {
        auto file = reinterpret_cast<rive::File*>(ref);
        // Creates a new Artboard instance.
        auto artboard = file->artboardAt(index);
        if (artboard != nullptr)
        {
            artboard->advance(0.0);
        }
        return (jlong)artboard.release();
    }

    JNIEXPORT
    jstring JNICALL
    Java_app_rive_runtime_kotlin_core_File_cppArtboardNameByIndex(JNIEnv* env,
                                                                  jobject,
                                                                  jlong ref,
                                                                  jint index)
    {
        auto file = reinterpret_cast<rive::File*>(ref);

        auto artboard = file->artboard(index);
        auto name = artboard->name();
        return env->NewStringUTF(name.c_str());
    }

    JNIEXPORT jobject JNICALL
    Java_app_rive_runtime_kotlin_core_File_cppEnums(JNIEnv* env,
                                                    jobject,
                                                    jlong ref)
    {
        auto file = reinterpret_cast<rive::File*>(ref);
        auto enums = file->enums();

        auto arrayListClass = FindClass(env, "java/util/ArrayList");
        auto arrayListConstructor =
            env->GetMethodID(arrayListClass.get(), "<init>", "()V");
        auto arrayListAddFn = env->GetMethodID(arrayListClass.get(),
                                               "add",
                                               "(Ljava/lang/Object;)Z");

        auto enumClass =
            FindClass(env, "app/rive/runtime/kotlin/core/File$Enum");
        auto enumConstructor =
            env->GetMethodID(enumClass.get(),
                             "<init>",
                             "(Ljava/lang/String;Ljava/util/List;)V");

        // Outer list of enums to be returned
        auto enumsList =
            MakeObject(env, arrayListClass.get(), arrayListConstructor);

        for (const auto& enumItem : enums)
        {
            auto name = MakeJString(env, enumItem->enumName());
            // Inner list of enum values
            auto valuesList =
                MakeObject(env, arrayListClass.get(), arrayListConstructor);

            // For each value in the enum item, add it to the inner list
            for (const auto& value : enumItem->values())
            {
                auto valueStr = MakeJString(env, value->key());
                env->CallBooleanMethod(valuesList.get(),
                                       arrayListAddFn,
                                       valueStr.get());
            }

            auto enumObject = MakeObject(env,
                                         enumClass.get(),
                                         enumConstructor,
                                         name.get(),
                                         valuesList.get());

            env->CallBooleanMethod(enumsList.get(),
                                   arrayListAddFn,
                                   enumObject.get());
        }

        // release() to avoid RAII cleanup of the local reference
        return enumsList.release();
    }

    JNIEXPORT jint JNICALL
    Java_app_rive_runtime_kotlin_core_File_cppViewModelCount(JNIEnv* env,
                                                             jobject thisObj,
                                                             jlong ref)
    {
        auto file = reinterpret_cast<rive::File*>(ref);
        return static_cast<jint>(file->viewModelCount());
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_File_cppViewModelByIndex(JNIEnv* env,
                                                               jobject thisObj,
                                                               jlong ref,
                                                               jint instanceIdx)
    {
        auto file = reinterpret_cast<rive::File*>(ref);
        return (jlong)file->viewModelByIndex(instanceIdx);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_File_cppViewModelByName(JNIEnv* env,
                                                              jobject thisObj,
                                                              jlong ref,
                                                              jstring name)
    {
        auto file = reinterpret_cast<rive::File*>(ref);
        auto viewModel = file->viewModelByName(JStringToString(env, name));
        return (jlong)viewModel;
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_runtime_kotlin_core_File_cppDefaultViewModelForArtboard(
        JNIEnv* env,
        jobject thisObj,
        jlong fileRef,
        jlong artboardRef)
    {
        auto file = reinterpret_cast<rive::File*>(fileRef);
        auto artboard = reinterpret_cast<rive::Artboard*>(artboardRef);
        auto viewModel = file->defaultArtboardViewModel(artboard);
        return reinterpret_cast<jlong>(viewModel);
    }

#ifdef __cplusplus
}
#endif

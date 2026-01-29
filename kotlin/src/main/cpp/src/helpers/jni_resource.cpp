#include "helpers/jni_resource.hpp"

#include "helpers/general.hpp"
#include "helpers/rive_log.hpp"
#include "helpers/jni_exception_handler.hpp"

#include <string>

namespace rive_android
{
// Global refs for class loading from native-attached threads.
jobject gClassLoader = nullptr;
jmethodID gLoadClassMID = nullptr;

void InitJNIClassLoader(JNIEnv* env, jobject anchorObject)
{
    assert(env && anchorObject);
    if (!env || !anchorObject)
    {
        RiveLogE("RiveN/ClassLoader", "Invalid arguments");
        return;
    }

    // If already initialized, do nothing.
    if (gClassLoader && gLoadClassMID)
    {
        return;
    }

    // Get the ClassLoader that loaded the anchor object's class.
    auto jObjClass = GetObjectClass(env, anchorObject);
    if (env->ExceptionCheck() || !jObjClass.get())
    {
        env->ExceptionClear();
        RiveLogE("RiveN/ClassLoader", "Failed to get anchor object class");
        return;
    }

    auto jClassClass = env->FindClass("java/lang/Class");
    if (env->ExceptionCheck() || !jClassClass)
    {
        env->ExceptionClear();
        RiveLogE("RiveN/ClassLoader", "Failed to find java/lang/Class");
        return;
    }

    auto jGetClassLoaderMID = env->GetMethodID(jClassClass,
                                               "getClassLoader",
                                               "()Ljava/lang/ClassLoader;");
    if (env->ExceptionCheck() || !jGetClassLoaderMID)
    {
        env->ExceptionClear();
        RiveLogE("RiveN/ClassLoader",
                 "Failed to get Class.getClassLoader method");
        return;
    }

    auto jLocalLoader =
        env->CallObjectMethod(jObjClass.get(), jGetClassLoaderMID);
    if (env->ExceptionCheck() || !jLocalLoader)
    {
        env->ExceptionClear();
        RiveLogE("RiveN/ClassLoader", "Failed to obtain anchor ClassLoader");
        return;
    }

    auto jClassLoaderClass = GetObjectClass(env, jLocalLoader);
    if (env->ExceptionCheck() || !jClassLoaderClass.get())
    {
        env->ExceptionClear();
        RiveLogE("RiveN/ClassLoader", "Failed to find java/lang/ClassLoader");
        return;
    }

    auto jLoadClassMID =
        env->GetMethodID(jClassLoaderClass.get(),
                         "loadClass",
                         "(Ljava/lang/String;)Ljava/lang/Class;");
    if (env->ExceptionCheck() || !jLoadClassMID)
    {
        env->ExceptionClear();
        RiveLogE("RiveN/ClassLoader",
                 "Failed to get ClassLoader.loadClass method");
        return;
    }

    // Promote to globals so they are usable on any attached thread.
    gClassLoader = env->NewGlobalRef(jLocalLoader);
    gLoadClassMID = jLoadClassMID;

    RiveLogD("RiveN/ClassLoader",
             "Initialized global ClassLoader for JNI class lookup");
}

JniResource<jclass> FindClass(JNIEnv* env, const char* name)
{
    if (!env || !name)
    {
        RiveLogE("RiveN/FindClass", "Invalid arguments");
        jclass nullClass = nullptr;
        return MakeJniResource(nullClass, env);
    }

    jclass result = nullptr;

    // If we have a global ClassLoader from the main thread (we should if we
    // initialized properly), prefer to use it. In attached command queue worker
    // threads (std::thread), `JNIEnv::FindClass` may only see the
    // bootstrap/system loader and fail.
    if (gClassLoader && gLoadClassMID)
    {
        // Convert JNI internal name (slash-separated) to binary name
        // (dot-separated) for ClassLoader.
        std::string binaryName(name);
        std::replace(binaryName.begin(), binaryName.end(), '/', '.');

        auto jName = MakeJString(env, binaryName);
        auto jClass = JNIExceptionHandler::CallObjectMethod(env,
                                                            gClassLoader,
                                                            gLoadClassMID,
                                                            jName.get());
        if (env->ExceptionCheck())
        {
            env->ExceptionClear();
            RiveLogE("RiveN/FindClass",
                     "ClassLoader.loadClass failed for: %s",
                     name);
        }
        else
        {
            result = reinterpret_cast<jclass>(jClass);
        }
    }

    // Fallback to the standard path if loader isn't initialized or loadClass
    // failed. This may still fail if we try to find a Rive class with the
    // system loader.
    if (!result)
    {
        result = env->FindClass(name);
        if (env->ExceptionCheck())
        {
            env->ExceptionClear();
            RiveLogE("RiveN/FindClass", "Failed to find class: %s", name);
        }
    }

    return MakeJniResource(result, env);
}

JniResource<jclass> GetObjectClass(JNIEnv* env, jobject obj)
{
    return MakeJniResource(env->GetObjectClass(obj), env);
}

JniResource<jobject> GetStaticObjectField(JNIEnv* env,
                                          jclass clazz,
                                          jfieldID fieldID)
{
    return MakeJniResource(env->GetStaticObjectField(clazz, fieldID), env);
}

JniResource<jobject> GetObjectFromMethod(JNIEnv* env,
                                         jobject obj,
                                         jmethodID methodID,
                                         ...)
{
    va_list args;
    va_start(args, methodID);
    jobject result = env->CallObjectMethodV(obj, methodID, args);
    va_end(args);
    return MakeJniResource(result, env);
}

JniResource<jobject> GetObjectArrayElement(JNIEnv* env,
                                           jobjectArray jarray,
                                           jsize index)
{
    jobject result = env->GetObjectArrayElement(jarray, index);
    return MakeJniResource(result, env);
}

JniResource<jobject> MakeObject(JNIEnv* env,
                                jclass clazz,
                                jmethodID initMid,
                                ...)
{
    va_list args;
    va_start(args, initMid);
    jobject result = env->NewObjectV(clazz, initMid, args);
    va_end(args);
    return MakeJniResource(result, env);
}

JniResource<jstring> MakeJString(JNIEnv* env, const char* str)
{
    return MakeJniResource(env->NewStringUTF(str), env);
}

JniResource<jstring> MakeJString(JNIEnv* env, const std::string& str)
{
    return MakeJString(env, str.c_str());
}

std::vector<uint8_t> ByteArrayToUint8Vec(JNIEnv* env, jbyteArray byteArray)
{
    jsize length = env->GetArrayLength(byteArray);
    std::vector<uint8_t> bytes(JIntToSizeT(length));
    env->GetByteArrayRegion(byteArray,
                            0,
                            length,
                            reinterpret_cast<jbyte*>(bytes.data()));
    return bytes;
}

JniResource<jobject> VecStringToJStringList(
    JNIEnv* env,
    const std::vector<std::string>& strs)
{
    auto arrayListClass = FindClass(env, "java/util/ArrayList");
    auto arrayListConstructor =
        env->GetMethodID(arrayListClass.get(), "<init>", "()V");
    auto arrayListAddFn =
        env->GetMethodID(arrayListClass.get(), "add", "(Ljava/lang/Object;)Z");
    auto arrayList =
        MakeObject(env, arrayListClass.get(), arrayListConstructor);
    for (const auto& str : strs)
    {
        auto jName = MakeJString(env, str);
        env->CallBooleanMethod(arrayList.get(), arrayListAddFn, jName.get());
    }
    return arrayList;
}

} // namespace rive_android

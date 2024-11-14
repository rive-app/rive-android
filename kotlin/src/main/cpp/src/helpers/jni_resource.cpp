#include "helpers/general.hpp"
#include "helpers/jni_resource.hpp"

namespace rive_android
{

JniResource<jclass> FindClass(JNIEnv* env, const char* name)
{
    return MakeJniResource(env->FindClass(name), env);
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
} // namespace rive_android

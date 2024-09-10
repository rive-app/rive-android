#ifndef _RIVE_ANDROID_JNI_RESOURCE_HPP_
#define _RIVE_ANDROID_JNI_RESOURCE_HPP_

#include <jni.h>
#include <vector>
#include "rive/math/aabb.hpp"

namespace rive_android
{

/**
 * JniResource manages JNI references (e.g. jclass, jobject, etc.) which require
 *  explicit deletion to prevent memory leaks.
 *
 * This class ensures that each resource is automatically released when the
 *  wrapped resource goes out of scope (i.e. applying RAII principles)
 */
template <typename T> class JniResource
{
private:
    T resource;
    JNIEnv* env;

    // Prevent copying to ensure unique ownership
    JniResource(const JniResource&) = delete;
    JniResource& operator=(const JniResource&) = delete;

public:
    // Direct initialization with resource
    JniResource(T res, JNIEnv* env) : resource(res), env(env) {}

    // Move constructor to transfer ownership
    JniResource(JniResource&& other) noexcept : resource(other.resource), env(other.env)
    {
        other.resource = nullptr;
    }

    /**
     * Automatically called when the JniResource object goes out of scope. It
     *  deletes the JNI reference using the appropriate JNI method to prevent
     *  memory leaks.
     */
    ~JniResource()
    {
        if (resource)
        {
            env->DeleteLocalRef(resource);
        }
    }

    operator T() const { return resource; }

    T get() const { return resource; }
};

// Helper function templates to simplify usage
template <typename T> JniResource<T> MakeJniResource(T res, JNIEnv* env)
{
    return JniResource<T>(res, env);
}

template <typename T> JniResource<T> FindClass(JNIEnv* env, const char* name)
{
    return MakeJniResource(env->FindClass(name), env);
}

template <typename T>
JniResource<T> GetStaticObjectField(JNIEnv* env, jclass clazz, jfieldID fieldID)
{
    return MakeJniResource(static_cast<T>(env->GetStaticObjectField(clazz, fieldID)), env);
}

std::vector<uint8_t> ByteArrayToUint8Vec(JNIEnv*, jbyteArray);

} // namespace rive_android
#endif
#ifndef _RIVE_ANDROID_JNI_RESOURCE_HPP_
#define _RIVE_ANDROID_JNI_RESOURCE_HPP_

#include <jni.h>
#include <vector>

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
    JniResource(JniResource&& other) noexcept :
        resource(other.resource), env(other.env)
    {
        other.resource = nullptr;
        other.env = nullptr;
    }

    /**
     * Automatically called when the JniResource object goes out of scope. It
     *  deletes the JNI reference using the appropriate JNI method to prevent
     *  memory leaks.
     */
    ~JniResource() noexcept
    {
        if (resource)
        {
            env->DeleteLocalRef(resource);
        }
    }

    JniResource& operator=(JniResource&& other) noexcept
    {
        if (this != &other)
        {
            if (resource)
            {
                // Cleanup the current resource if needed.
                env->DeleteLocalRef(resource);
            }
            resource = other.resource;
            env = other.env;
            other.resource = nullptr;
            other.env = nullptr;
        }
        return *this;
    }

    explicit operator T() const { return resource; }

    T get() const { return resource; }

    // Use when the JNI resource should no longer be managed by this object,
    // such as when you'd like to return it to Kotlin.
    T release() noexcept
    {
        T toRelease = resource;
        resource = nullptr;
        return toRelease;
    }
};

// Helper function templates to simplify usage
template <typename T> JniResource<T> MakeJniResource(T res, JNIEnv* env)
{
    return JniResource<T>(res, env);
}

JniResource<jclass> FindClass(JNIEnv*, const char*);

JniResource<jclass> GetObjectClass(JNIEnv*, jobject);

JniResource<jobject> GetStaticObjectField(JNIEnv*, jclass, jfieldID);

JniResource<jobject> GetObjectFromMethod(JNIEnv*, jobject, jmethodID, ...);

JniResource<jobject> GetObjectArrayElement(JNIEnv*, jobjectArray, jsize);

JniResource<jobject> MakeObject(JNIEnv* env,
                                jclass clazz,
                                jmethodID initMid,
                                ...);

JniResource<jstring> MakeJString(JNIEnv*, const char*);
JniResource<jstring> MakeJString(JNIEnv*, const std::string&);

std::vector<uint8_t> ByteArrayToUint8Vec(JNIEnv*, jbyteArray);
JniResource<jobject> VecStringToJStringList(JNIEnv*,
                                            const std::vector<std::string>&);

} // namespace rive_android
#endif
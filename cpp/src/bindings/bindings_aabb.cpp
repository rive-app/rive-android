// From rive-cpp
#include "jni_refs.hpp"
#include "helpers/general.hpp"
#include "math/aabb.hpp"
//
#include <jni.h>

#ifdef __cplusplus
extern "C"
{
#endif
    // AABB

    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_core_AABB_constructor(
        JNIEnv *env,
        jobject thisObj,
        jfloat width,
        jfloat height)
    {
        // TODO: garbage collection?
        rive::AABB *aabb = new rive::AABB(0, 0, width, height);
        return (jlong)aabb;
    }

    JNIEXPORT jfloat JNICALL Java_app_rive_runtime_kotlin_core_AABB_cppWidth(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        rive::AABB *aabb = (rive::AABB *)ref;
        return (jfloat)aabb->width();
    }

    JNIEXPORT jfloat JNICALL Java_app_rive_runtime_kotlin_core_AABB_cppHeight(
        JNIEnv *env,
        jobject thisObj,
        jlong ref)
    {
        rive::AABB *aabb = (rive::AABB *)ref;
        return (jfloat)aabb->height();
    }

#ifdef __cplusplus
}
#endif

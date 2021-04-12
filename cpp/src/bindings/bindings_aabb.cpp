// From rive-cpp
#include "math/aabb.hpp"
//
#include <jni.h>

#ifdef __cplusplus
extern "C"
{
#endif
    // AABB

    JNIEXPORT jlong JNICALL Java_app_rive_runtime_kotlin_AABB_constructor(
        JNIEnv *env,
        jobject thisObj,
        jfloat width,
        jfloat height)
    {
        // TODO: garbage collection?
        rive::AABB *aabb = new rive::AABB(0, 0, width, height);
        return (jlong)aabb;
    }

#ifdef __cplusplus
}
#endif

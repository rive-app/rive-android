#include <jni.h>
#include <android/log.h>
#include <sys/system_properties.h>
#include <stdlib.h>
#include "jni_refs.hpp"
#include "helpers/general.hpp"

namespace rive_android {
jclass getClass(const char* name) { return getJNIEnv()->FindClass(name); }
jmethodID getMethodId(jclass clazz, const char* name, const char* sig) {
    JNIEnv* env = getJNIEnv();
    jmethodID output = env->GetMethodID(clazz, name, sig);
    env->DeleteLocalRef(clazz);
    return output;
}

jfieldID getStaticFieldId(jclass clazz, const char* name, const char* sig) {
    JNIEnv* env = getJNIEnv();
    jfieldID output = env->GetStaticFieldID(clazz, name, sig);
    env->DeleteLocalRef(clazz);
    return output;
}

jfieldID getFieldId(jclass clazz, const char* name, const char* sig) {
    JNIEnv* env = getJNIEnv();
    jfieldID output = env->GetFieldID(clazz, name, sig);
    env->DeleteLocalRef(clazz);
    return output;
}

jint throwRiveException(const char* message) {
    jclass exClass = getClass("app/rive/runtime/kotlin/core/errors/RiveException");
    return getJNIEnv()->ThrowNew(exClass, message);
}
jint throwMalformedFileException(const char* message) {
    jclass exClass = getClass("app/rive/runtime/kotlin/core/errors/MalformedFileException");
    return getJNIEnv()->ThrowNew(exClass, message);
}
jint throwUnsupportedRuntimeVersionException(const char* message) {
    jclass exClass = getClass("app/rive/runtime/kotlin/core/errors/"
                              "UnsupportedRuntimeVersionException");
    return getJNIEnv()->ThrowNew(exClass, message);
}

jclass getFitClass() { return getClass("app/rive/runtime/kotlin/core/Fit"); };
jmethodID getFitNameMethodId() {
    return getMethodId(getFitClass(), "name", "()Ljava/lang/String;");
}

jclass getAlignmentClass() { return getClass("app/rive/runtime/kotlin/core/Alignment"); }
jmethodID getAlignmentNameMethodId() {
    return getMethodId(getAlignmentClass(), "name", "()Ljava/lang/String;");
};

jclass getLoopClass() { return getClass("app/rive/runtime/kotlin/core/Loop"); };

jfieldID getNoneLoopField() {
    return getStaticFieldId(getLoopClass(), "NONE", "Lapp/rive/runtime/kotlin/core/Loop;");
};
jfieldID getOneShotLoopField() {
    return getStaticFieldId(getLoopClass(), "ONESHOT", "Lapp/rive/runtime/kotlin/core/Loop;");
};
jfieldID getLoopLoopField() {
    return getStaticFieldId(getLoopClass(), "LOOP", "Lapp/rive/runtime/kotlin/core/Loop;");
};
jfieldID getPingPongLoopField() {
    return getStaticFieldId(getLoopClass(), "PINGPONG", "Lapp/rive/runtime/kotlin/core/Loop;");
};

jclass getPointerFClass() { return getClass("android/graphics/PointF"); };

jfieldID getXFieldId() { return getFieldId(getPointerFClass(), "x", "F"); }

jfieldID getYFieldId() { return getFieldId(getPointerFClass(), "y", "F"); }

jmethodID getPointFInitMethod() { return getMethodId(getPointerFClass(), "<init>", "(FF)V"); };

static const char* AABBFieldNames[] = {"left", "top", "right", "bottom"};

rive::AABB rectFToAABB(JNIEnv* env, jobject rectf) {
    auto cls = env->FindClass("android/graphics/RectF");
    float values[4];
    for (int i = 0; i < 4; ++i) {
        values[i] = env->GetFloatField(rectf, env->GetFieldID(cls, AABBFieldNames[i], "F"));
    }
    return rive::AABB(values[0], values[1], values[2], values[3]);
}

void aabbToRectF(JNIEnv* env, const rive::AABB& aabb, jobject rectf) {
    auto cls = env->FindClass("android/graphics/RectF");
    const float values[4] = {aabb.left(), aabb.top(), aabb.right(), aabb.bottom()};
    for (int i = 0; i < 4; ++i) {
        env->SetFloatField(rectf, env->GetFieldID(cls, AABBFieldNames[i], "F"), values[i]);
    }
}

} // namespace rive_android

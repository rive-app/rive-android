#include <jni.h>
#include <android/log.h>
#include <sys/system_properties.h>
#include <stdlib.h>
#include "jni_refs.hpp"
#include "helpers/general.hpp"

namespace rive_android
{
jclass getClass(const char* name) { return GetJNIEnv()->FindClass(name); }
jmethodID getMethodId(jclass clazz, const char* name, const char* sig)
{
    JNIEnv* env = GetJNIEnv();
    jmethodID output = env->GetMethodID(clazz, name, sig);
    env->DeleteLocalRef(clazz);
    return output;
}

jfieldID getStaticFieldId(jclass clazz, const char* name, const char* sig)
{
    JNIEnv* env = GetJNIEnv();
    jfieldID output = env->GetStaticFieldID(clazz, name, sig);
    env->DeleteLocalRef(clazz);
    return output;
}

jfieldID getFieldId(jclass clazz, const char* name, const char* sig)
{
    JNIEnv* env = GetJNIEnv();
    jfieldID output = env->GetFieldID(clazz, name, sig);
    env->DeleteLocalRef(clazz);
    return output;
}

jint ThrowRiveException(const char* message)
{
    jclass exClass = getClass("app/rive/runtime/kotlin/core/errors/RiveException");
    return GetJNIEnv()->ThrowNew(exClass, message);
}
jint ThrowMalformedFileException(const char* message)
{
    jclass exClass = getClass("app/rive/runtime/kotlin/core/errors/MalformedFileException");
    return GetJNIEnv()->ThrowNew(exClass, message);
}
jint ThrowUnsupportedRuntimeVersionException(const char* message)
{
    jclass exClass = getClass("app/rive/runtime/kotlin/core/errors/"
                              "UnsupportedRuntimeVersionException");
    return GetJNIEnv()->ThrowNew(exClass, message);
}

jclass GetFitClass() { return getClass("app/rive/runtime/kotlin/core/Fit"); };
jmethodID GetFitNameMethodId()
{
    return getMethodId(GetFitClass(), "name", "()Ljava/lang/String;");
}

jclass GetAlignmentClass() { return getClass("app/rive/runtime/kotlin/core/Alignment"); }
jmethodID GetAlignmentNameMethodId()
{
    return getMethodId(GetAlignmentClass(), "name", "()Ljava/lang/String;");
};

jclass GetLoopClass() { return getClass("app/rive/runtime/kotlin/core/Loop"); };

jfieldID GetNoneLoopField()
{
    return getStaticFieldId(GetLoopClass(), "NONE", "Lapp/rive/runtime/kotlin/core/Loop;");
};
jfieldID GetOneShotLoopField()
{
    return getStaticFieldId(GetLoopClass(), "ONESHOT", "Lapp/rive/runtime/kotlin/core/Loop;");
};
jfieldID GetLoopLoopField()
{
    return getStaticFieldId(GetLoopClass(), "LOOP", "Lapp/rive/runtime/kotlin/core/Loop;");
};
jfieldID GetPingPongLoopField()
{
    return getStaticFieldId(GetLoopClass(), "PINGPONG", "Lapp/rive/runtime/kotlin/core/Loop;");
};

jclass GetPointerFClass() { return getClass("android/graphics/PointF"); };

jfieldID GetXFieldId() { return getFieldId(GetPointerFClass(), "x", "F"); }

jfieldID GetYFieldId() { return getFieldId(GetPointerFClass(), "y", "F"); }

jmethodID GetPointFInitMethod() { return getMethodId(GetPointerFClass(), "<init>", "(FF)V"); };

static const char* AABBFieldNames[] = {"left", "top", "right", "bottom"};

rive::AABB RectFToAABB(JNIEnv* env, jobject rectf)
{
    auto cls = env->FindClass("android/graphics/RectF");
    float values[4];
    for (int i = 0; i < 4; ++i)
    {
        values[i] = env->GetFloatField(rectf, env->GetFieldID(cls, AABBFieldNames[i], "F"));
    }
    env->DeleteLocalRef(cls);
    return rive::AABB(values[0], values[1], values[2], values[3]);
}

void AABBToRectF(JNIEnv* env, const rive::AABB& aabb, jobject rectf)
{
    auto cls = env->FindClass("android/graphics/RectF");
    const float values[4] = {aabb.left(), aabb.top(), aabb.right(), aabb.bottom()};
    for (int i = 0; i < 4; ++i)
    {
        env->SetFloatField(rectf, env->GetFieldID(cls, AABBFieldNames[i], "F"), values[i]);
    }
    env->DeleteLocalRef(cls);
}

} // namespace rive_android

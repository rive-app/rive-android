#include "helpers/jni_exception_handler.hpp"

#include "helpers/general.hpp"
#include "helpers/jni_resource.hpp"
#include "helpers/rive_log.hpp"

namespace rive_android
{
/* static  */ std::string JNIExceptionHandler::get_exception_message(
    JNIEnv* env,
    jthrowable exception)
{
    std::ostringstream errorMsg;
    append_throwable_message(env, exception, errorMsg);
    jthrowable cause = get_cause(env, exception);
    if (cause != nullptr)
    {
        errorMsg << "\nCaused by: ";
        append_throwable_message(env, cause, errorMsg);
    }
    return errorMsg.str();
}

/* static  */ void JNIExceptionHandler::append_throwable_message(
    JNIEnv* env,
    jthrowable throwable,
    std::ostringstream& errorMsg)
{
    JniResource<jclass> throwableClass = FindClass(env, "java/lang/Throwable");
    jmethodID midToString = env->GetMethodID(throwableClass.get(),
                                             "toString",
                                             "()Ljava/lang/String;");
    jmethodID midGetStackTrace =
        env->GetMethodID(throwableClass.get(),
                         "getStackTrace",
                         "()[Ljava/lang/StackTraceElement;");

    // Append the error message.
    auto msgObj = (jstring)env->CallObjectMethod(throwable, midToString);
    if (msgObj != nullptr)
    {
        const char* msgStr = env->GetStringUTFChars(msgObj, nullptr);
        errorMsg << "\n" << msgStr;
        env->ReleaseStringUTFChars(msgObj, msgStr);
        env->DeleteLocalRef(msgObj);
    }

    append_stack_trace(env, throwable, midGetStackTrace, errorMsg);
}

/* static  */ void JNIExceptionHandler::append_stack_trace(
    JNIEnv* env,
    jthrowable throwable,
    jmethodID midGetStackTrace,
    std::ostringstream& errorMsg)
{
    JniResource<jobject> frames =
        GetObjectFromMethod(env, throwable, midGetStackTrace);
    if (!frames.get())
    {
        return;
    }

    JniResource<jclass> frameClass =
        FindClass(env, "java/lang/StackTraceElement");
    jmethodID midFrameToString =
        env->GetMethodID(frameClass.get(), "toString", "()Ljava/lang/String;");

    auto framesArray = reinterpret_cast<jobjectArray>(frames.get());
    jsize framesLength = env->GetArrayLength(framesArray);
    for (jsize i = 0; i < framesLength; i++)
    {
        JniResource<jobject> frame = GetObjectArrayElement(env, framesArray, i);

        if (!frame.get())
        {
            continue;
        }

        // Append stack trace elements if any.
        JniResource<jobject> frameString =
            GetObjectFromMethod(env, frame.get(), midFrameToString);
        if (frameString.get())
        {
            auto toJString = reinterpret_cast<jstring>(frameString.get());
            const char* frameStr = env->GetStringUTFChars(toJString, nullptr);
            errorMsg << "\n    " << frameStr;
            env->ReleaseStringUTFChars(toJString, frameStr);
        }
    }
}

/* static  */ jthrowable JNIExceptionHandler::get_cause(JNIEnv* env,
                                                        jthrowable throwable)
{
    jclass throwableClass = env->FindClass("java/lang/Throwable");
    jmethodID midGetCause =
        env->GetMethodID(throwableClass, "getCause", "()Ljava/lang/Throwable;");
    return (jthrowable)env->CallObjectMethod(throwable, midGetCause);
}

/* static  */ void JNIExceptionHandler::check_and_rethrow(JNIEnv* env)
{
    if (!env->ExceptionCheck())
    {
        return;
    }

    jthrowable exception = env->ExceptionOccurred();
    env->ExceptionClear();

    std::string errorMsg = get_exception_message(env, exception);

    // Create the new exception.
    JniResource<jclass> throwableClass = FindClass(env, "java/lang/Throwable");
    jmethodID midInit = env->GetMethodID(throwableClass.get(),
                                         "<init>",
                                         "(Ljava/lang/String;)V");
    JniResource<jstring> newMessage = MakeJString(env, errorMsg);

    // Finally throw with the aggregated message.
    JniResource<jobject> newException =
        MakeObject(env, throwableClass.get(), midInit, newMessage.get());
    env->Throw(reinterpret_cast<jthrowable>(newException.get()));

    // Detach thread so the app can end.
    DetachThread();
}

/* static  */ bool JNIExceptionHandler::ClearAndLogErrors(JNIEnv* env,
                                                          const char* tag,
                                                          const char* message)
{
    if (!env->ExceptionCheck())
    {
        return false;
    }

    // Grab the throwable and clear it
    auto throwable = MakeJniResource(env->ExceptionOccurred(), env);
    env->ExceptionClear();

    auto errorString =
        JNIExceptionHandler::get_exception_message(env, throwable.get());
    RiveLogE(tag, "%s\n%s", message, errorString.c_str());
    return true;
}

/* static  */ jobject JNIExceptionHandler::CallObjectMethod(JNIEnv* env,
                                                            jobject obj,
                                                            jmethodID methodID,
                                                            ...)
{
    va_list args;
    va_start(args, methodID);
    jobject result = env->CallObjectMethodV(obj, methodID, args);
    va_end(args);
    check_and_rethrow(env);
    return result;
}

/* static  */ void JNIExceptionHandler::CallVoidMethod(JNIEnv* env,
                                                       jobject obj,
                                                       jmethodID methodID,
                                                       ...)
{
    va_list args;
    va_start(args, methodID);
    env->CallVoidMethodV(obj, methodID, args);
    va_end(args);
    check_and_rethrow(env);
}

/* static  */ jint JNIExceptionHandler::CallIntMethod(JNIEnv* env,
                                                      jobject obj,
                                                      jmethodID methodID,
                                                      ...)
{
    va_list args;
    va_start(args, methodID);
    jint result = env->CallIntMethodV(obj, methodID, args);
    va_end(args);
    check_and_rethrow(env);
    return result;
}

/* static  */ jboolean JNIExceptionHandler::CallBooleanMethod(
    JNIEnv* env,
    jobject obj,
    jmethodID methodID,
    ...)
{
    va_list args;
    va_start(args, methodID);
    jboolean result = env->CallBooleanMethodV(obj, methodID, args);
    va_end(args);
    check_and_rethrow(env);
    return result;
}

/* static  */ jobject JNIExceptionHandler::CallStaticObjectMethod(
    JNIEnv* env,
    jclass clazz,
    jmethodID methodID,
    ...)
{
    va_list args;
    va_start(args, methodID);
    jobject result = env->CallStaticObjectMethodV(clazz, methodID, args);
    va_end(args);
    check_and_rethrow(env);
    return result;
}

} // namespace rive_android
//
// Created by Peter G Hayes on 23/08/2023.
//

#include <jni.h>

#include "helpers/general.hpp"
#include "helpers/jni_exception_handler.hpp"
#include "helpers/jni_string.hpp"
#include "jni_refs.hpp"
#include "rive/custom_property_boolean.hpp"
#include "rive/custom_property_number.hpp"
#include "rive/custom_property_string.hpp"
#include "rive/event.hpp"
#include "rive/open_url_event.hpp"

#ifdef __cplusplus
extern "C"
{
#endif

    using namespace rive_android;

    const char* GetTargetValue(rive::OpenUrlEvent* urlEvent)
    {
        switch (urlEvent->targetValue())
        {
            case 0:
                return "_blank";
            case 1:
                return "_parent";
            case 2:
                return "_self";
            case 3:
                return "_top";
        }
        return "_blank";
    }

    jobject GetProperties(JNIEnv* env, rive::Event* event)
    {
        jmethodID putMethod = env->GetMethodID(
            GetHashMapClass(),
            "put",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

        jobject propertiesObject =
            env->NewObject(GetHashMapClass(), GetHashMapConstructorId());

        if (event == nullptr)
        {
            return propertiesObject;
        }

        for (auto child : event->children())
        {
            if (child->is<rive::CustomProperty>())
            {
                if (!child->name().empty())
                {
                    auto jKey = MakeJString(env, child->name());
                    switch (child->coreType())
                    {
                        case rive::CustomPropertyBoolean::typeKey:
                        {
                            jboolean jValueBoolean =
                                child->as<rive::CustomPropertyBoolean>()
                                    ->propertyValue();
                            jobject booleanValue =
                                env->NewObject(GetBooleanClass(),
                                               GetBooleanConstructor(),
                                               jValueBoolean);
                            JNIExceptionHandler::CallObjectMethod(
                                env,
                                propertiesObject,
                                putMethod,
                                jKey.get(),
                                booleanValue);

                            env->DeleteLocalRef(booleanValue);
                            break;
                        }
                        case rive::CustomPropertyString::typeKey:
                        {
                            auto jValueString = MakeJString(
                                env,
                                child->as<rive::CustomPropertyString>()
                                    ->propertyValue());
                            JNIExceptionHandler::CallObjectMethod(
                                env,
                                propertiesObject,
                                putMethod,
                                jKey.get(),
                                jValueString.get());
                            break;
                        }
                        case rive::CustomPropertyNumber::typeKey:
                        {

                            jfloat jValueFloat =
                                child->as<rive::CustomPropertyNumber>()
                                    ->propertyValue();
                            jobject floatValue =
                                env->NewObject(GetFloatClass(),
                                               GetFloatConstructor(),
                                               jValueFloat);
                            JNIExceptionHandler::CallObjectMethod(
                                env,
                                propertiesObject,
                                putMethod,
                                jKey.get(),
                                floatValue);

                            env->DeleteLocalRef(floatValue);
                            break;
                        }
                    }
                }
            }
        }

        return propertiesObject;
    }

    JNIEXPORT jstring JNICALL
    Java_app_rive_runtime_kotlin_core_RiveOpenURLEvent_cppURL(JNIEnv* env,
                                                              jobject,
                                                              jlong ref)
    {
        auto* event = reinterpret_cast<rive::Event*>(ref);
        if (event->is<rive::OpenUrlEvent>())
        {
            auto urlEvent = event->as<rive::OpenUrlEvent>();
            return MakeJString(env, urlEvent->url()).release();
        }
        return MakeJString(env, "").release();
    }

    JNIEXPORT jstring JNICALL
    Java_app_rive_runtime_kotlin_core_RiveOpenURLEvent_cppTarget(JNIEnv* env,
                                                                 jobject,
                                                                 jlong ref)
    {
        auto* event = reinterpret_cast<rive::Event*>(ref);
        if (event->is<rive::OpenUrlEvent>())
        {
            auto urlEvent = event->as<rive::OpenUrlEvent>();
            const char* target = GetTargetValue(urlEvent);

            return MakeJString(env, target).release();
        }

        return MakeJString(env, "_blank").release();
    }

    JNIEXPORT jstring JNICALL
    Java_app_rive_runtime_kotlin_core_RiveEvent_cppName(JNIEnv* env,
                                                        jobject,
                                                        jlong ref)
    {
        auto* event = reinterpret_cast<rive::Event*>(ref);
        return MakeJString(env, event->name()).release();
    }

    JNIEXPORT jshort JNICALL
    Java_app_rive_runtime_kotlin_core_RiveEvent_cppType(JNIEnv*,
                                                        jobject,
                                                        jlong ref)
    {
        auto* event = reinterpret_cast<rive::Event*>(ref);
        return static_cast<jshort>(event->coreType());
    }

    JNIEXPORT jobject JNICALL
    Java_app_rive_runtime_kotlin_core_RiveEvent_cppProperties(JNIEnv* env,
                                                              jobject,
                                                              jlong ref)
    {

        auto* event = reinterpret_cast<rive::Event*>(ref);
        return GetProperties(env, event);
    }

    JNIEXPORT jobject JNICALL
    Java_app_rive_runtime_kotlin_core_RiveEvent_cppData(JNIEnv* env,
                                                        jobject,
                                                        jlong ref)
    {
        jclass hashMapClass = GetHashMapClass();
        jmethodID hashMapConstructor = GetHashMapConstructorId();
        jmethodID putMethod = env->GetMethodID(
            hashMapClass,
            "put",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
        jobject eventObject = env->NewObject(hashMapClass, hashMapConstructor);

        auto* event = reinterpret_cast<rive::Event*>(ref);

        if (event == nullptr)
        {
            return eventObject;
        }

        auto nameKey = MakeJString(env, "name");
        auto name = MakeJString(env, event->name());
        JNIExceptionHandler::CallObjectMethod(env,
                                              eventObject,
                                              putMethod,
                                              nameKey.get(),
                                              name.get());

        if (event->is<rive::OpenUrlEvent>())
        {
            auto urlEvent = event->as<rive::OpenUrlEvent>();
            auto url = MakeJString(env, urlEvent->url());
            jobject type = env->NewObject(GetShortClass(),
                                          GetShortConstructor(),
                                          event->coreType());
            auto typeKey = MakeJString(env, "type");
            JNIExceptionHandler::CallObjectMethod(env,
                                                  eventObject,
                                                  putMethod,
                                                  typeKey.get(),
                                                  type);
            auto urlKey = MakeJString(env, "url");
            JNIExceptionHandler::CallObjectMethod(env,
                                                  eventObject,
                                                  putMethod,
                                                  urlKey.get(),
                                                  url.get());
            const char* target = GetTargetValue(urlEvent);
            auto targetKey = MakeJString(env, "target");
            auto targetValue = MakeJString(env, target);
            JNIExceptionHandler::CallObjectMethod(env,
                                                  eventObject,
                                                  putMethod,
                                                  targetKey.get(),
                                                  targetValue.get());
        }

        auto propertiesKey = MakeJString(env, "properties");
        JNIExceptionHandler::CallObjectMethod(env,
                                              eventObject,
                                              putMethod,
                                              propertiesKey.get(),
                                              GetProperties(env, event));
        return eventObject;
    }

#ifdef __cplusplus
}
#endif

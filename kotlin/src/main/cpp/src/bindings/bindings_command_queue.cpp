#include <jni.h>
#include <android/native_window_jni.h>

#include "helpers/android_factories.hpp"
#include "helpers/jni_resource.hpp"
#include "models/jni_renderer.hpp"
#include "rive/animation/state_machine_instance.hpp"
#include "rive/command_queue.hpp"
#include "rive/command_server.hpp"
#include "rive/file.hpp"
#include "rive/renderer/gl/render_buffer_gl_impl.hpp"
#include "rive/renderer/gl/render_context_gl_impl.hpp"
#include "rive/renderer/gl/render_target_gl.hpp"
#include "rive/renderer/rive_render_image.hpp"

#include <future>
#include <string>

using namespace rive_android;

/** Convert a JVM long handle to a typed C++ handle */
template <typename HandleT> static HandleT handleFromLong(jlong handle)
{
    return reinterpret_cast<HandleT>(static_cast<uint64_t>(handle));
}

/** Convert a typed C++ handle to a JVM long handle */
template <typename HandleT> static jlong longFromHandle(HandleT handle)
{
    return static_cast<jlong>(reinterpret_cast<uint64_t>(handle));
}

/**
 * Holds a reference to a Kotlin CommandQueue instance and allows calling
 * methods on it. Used by the Listener classes for callbacks.
 */
class JCommandQueue
{
public:
    JCommandQueue(JNIEnv* env, jobject jQueue) :
        m_env(env),
        m_class(static_cast<jclass>(
            env->NewGlobalRef(env->FindClass("app/rive/core/CommandQueue")))),
        m_jQueue(env->NewGlobalRef(jQueue))
    {}

    ~JCommandQueue()
    {
        m_env->DeleteGlobalRef(m_class);
        m_env->DeleteGlobalRef(m_jQueue);
    }

    [[nodiscard]] JNIEnv* env() const { return m_env; }

    /**
     * Call a CommandQueue Kotlin instance method.
     *
     * @param name Kotlin method name
     * @param sig JNI signature string
     * @param args Arguments forwarded to CallVoidMethod
     */
    template <typename... Args>
    void call(const char* name, const char* sig, Args... args) const
    {
        jmethodID mid = m_env->GetMethodID(m_class, name, sig);
        m_env->CallVoidMethod(m_jQueue, mid, args...);
    }

private:
    JNIEnv* const m_env;
    jclass m_class;
    jobject m_jQueue;
};

class FileListener : public rive::CommandQueue::FileListener
{
public:
    FileListener(JNIEnv* env, jobject jQueue) :
        rive::CommandQueue::FileListener(), m_queue(env, jQueue)
    {}

    virtual ~FileListener() = default;

    void onFileLoaded(const rive::FileHandle handle,
                      uint64_t requestID) override
    {
        m_queue.call("onFileLoaded", "(JJ)V", requestID, handle);
    }

    void onFileError(const rive::FileHandle,
                     uint64_t requestID,
                     std::string error) override
    {
        auto jError = MakeJString(m_queue.env(), error);
        m_queue.call("onFileError",
                     "(JLjava/lang/String;)V",
                     requestID,
                     jError.get());
    }

    void onArtboardsListed(const rive::FileHandle,
                           uint64_t requestID,
                           std::vector<std::string> artboardNames) override
    {
        auto jList = VecStringToJStringList(m_queue.env(), artboardNames);
        m_queue.call("onArtboardsListed",
                     "(JLjava/util/List;)V",
                     requestID,
                     jList.get());
    }

    void onViewModelsListed(const rive::FileHandle,
                            uint64_t requestID,
                            std::vector<std::string> viewModelNames) override
    {
        auto jList = VecStringToJStringList(m_queue.env(), viewModelNames);
        m_queue.call("onViewModelsListed",
                     "(JLjava/util/List;)V",
                     requestID,
                     jList.get());
    }

    void onViewModelInstanceNamesListed(
        const rive::FileHandle,
        uint64_t requestID,
        std::string,
        std::vector<std::string> instanceNames) override
    {
        auto jList = VecStringToJStringList(m_queue.env(), instanceNames);
        m_queue.call("onViewModelInstancesListed",
                     "(JLjava/util/List;)V",
                     requestID,
                     jList.get());
    }

    void onViewModelPropertiesListed(
        const rive::FileHandle,
        uint64_t requestID,
        std::string viewModelName,
        std::vector<rive::CommandQueue::FileListener::ViewModelPropertyData>
            properties) override
    {
        auto env = m_queue.env();
        auto arrayListClass = FindClass(env, "java/util/ArrayList");
        auto arrayListConstructor =
            env->GetMethodID(arrayListClass.get(), "<init>", "()V");
        auto arrayListAddFn = env->GetMethodID(arrayListClass.get(),
                                               "add",
                                               "(Ljava/lang/Object;)Z");

        auto propertyClass =
            FindClass(env, "app/rive/runtime/kotlin/core/ViewModel$Property");
        auto constructor =
            env->GetMethodID(propertyClass.get(),
                             "<init>",
                             "(Lapp/rive/runtime/kotlin/core/"
                             "ViewModel$PropertyDataType;Ljava/lang/String;)V");

        auto dataTypeClass = FindClass(
            env,
            "app/rive/runtime/kotlin/core/ViewModel$PropertyDataType");
        auto fromIntFn = env->GetStaticMethodID(
            dataTypeClass.get(),
            "fromInt",
            "(I)Lapp/rive/runtime/kotlin/core/ViewModel$PropertyDataType;");

        auto jPropertyList =
            MakeObject(env, arrayListClass.get(), arrayListConstructor);

        for (const auto& property : properties)
        {
            auto jName = MakeJString(env, property.name);
            auto jDataType = JniResource(
                env->CallStaticObjectMethod(dataTypeClass.get(),
                                            fromIntFn,
                                            static_cast<jint>(property.type)),
                env);
            auto propertyObject = MakeObject(env,
                                             propertyClass.get(),
                                             constructor,
                                             jDataType.get(),
                                             jName.get());

            env->CallBooleanMethod(jPropertyList.get(),
                                   arrayListAddFn,
                                   propertyObject.get());
        }

        m_queue.call("onViewModelPropertiesListed",
                     "(JLjava/util/List;)V",
                     requestID,
                     jPropertyList.get());
    }

    void onViewModelEnumsListed(const rive::FileHandle,
                                uint64_t requestID,
                                std::vector<rive::ViewModelEnum> enums) override
    {
        auto env = m_queue.env();
        auto arrayListClass = FindClass(env, "java/util/ArrayList");
        auto arrayListConstructor =
            env->GetMethodID(arrayListClass.get(), "<init>", "()V");
        auto arrayListAddFn = env->GetMethodID(arrayListClass.get(),
                                               "add",
                                               "(Ljava/lang/Object;)Z");

        auto enumClass =
            FindClass(env, "app/rive/runtime/kotlin/core/File$Enum");
        auto enumConstructor =
            env->GetMethodID(enumClass.get(),
                             "<init>",
                             "(Ljava/lang/String;Ljava/util/List;)V");

        // Outer list of enums to be returned
        auto jEnumsList =
            MakeObject(env, arrayListClass.get(), arrayListConstructor);

        for (const auto& enumItem : enums)
        {
            auto jName = MakeJString(env, enumItem.name);
            // Inner list of enum values
            auto jValuesList =
                MakeObject(env, arrayListClass.get(), arrayListConstructor);

            // For each value in the enum item, add it to the inner list
            for (const auto& value : enumItem.enumerants)
            {
                auto jValue = MakeJString(env, value);
                env->CallBooleanMethod(jValuesList.get(),
                                       arrayListAddFn,
                                       jValue.get());
            }

            auto jEnumObject = MakeObject(env,
                                          enumClass.get(),
                                          enumConstructor,
                                          jName.get(),
                                          jValuesList.get());

            // Add the inner enum description to the outer list
            env->CallBooleanMethod(jEnumsList.get(),
                                   arrayListAddFn,
                                   jEnumObject.get());
        }

        m_queue.call("onEnumsListed",
                     "(JLjava/util/List;)V",
                     requestID,
                     jEnumsList.get());
    }

private:
    JCommandQueue m_queue;
};

class ArtboardListener : public rive::CommandQueue::ArtboardListener
{
public:
    ArtboardListener(JNIEnv* env, jobject jQueue) :
        rive::CommandQueue::ArtboardListener(), m_queue(env, jQueue)
    {}

    virtual ~ArtboardListener() = default;

    void onStateMachinesListed(
        const rive::ArtboardHandle,
        uint64_t requestID,
        std::vector<std::string> stateMachineNames) override
    {
        auto jList = VecStringToJStringList(m_queue.env(), stateMachineNames);
        m_queue.call("onStateMachinesListed",
                     "(JLjava/util/List;)V",
                     requestID,
                     jList.get());
    }

private:
    JCommandQueue m_queue;
};

class StateMachineListener : public rive::CommandQueue::StateMachineListener
{
public:
    StateMachineListener(JNIEnv* env, jobject jQueue) :
        rive::CommandQueue::StateMachineListener(), m_queue(env, jQueue)
    {}

    virtual ~StateMachineListener() = default;

    void onStateMachineSettled(const rive::StateMachineHandle smHandle,
                               uint64_t requestID) override
    {
        m_queue.call("onStateMachineSettled", "(J)V", longFromHandle(smHandle));
    }

private:
    JCommandQueue m_queue;
};

class ViewModelInstanceListener
    : public rive::CommandQueue::ViewModelInstanceListener
{
public:
    ViewModelInstanceListener(JNIEnv* env, jobject jQueue) :
        rive::CommandQueue::ViewModelInstanceListener(), m_queue(env, jQueue)
    {}

    virtual ~ViewModelInstanceListener() = default;

    void onViewModelDataReceived(
        const rive::ViewModelInstanceHandle vmiHandle,
        uint64_t requestID,
        rive::CommandQueue::ViewModelInstanceData data) override
    {
        auto env = m_queue.env();
        auto jPropertyName = MakeJString(env, data.metaData.name);

        switch (data.metaData.type)
        {
            case rive::DataType::number:
                m_queue.call("onNumberPropertyUpdated",
                             "(JJLjava/lang/String;F)V",
                             requestID,
                             longFromHandle(vmiHandle),
                             jPropertyName.get(),
                             data.numberValue);
                break;
            case rive::DataType::string:
                m_queue.call("onStringPropertyUpdated",
                             "(JJLjava/lang/String;Ljava/"
                             "lang/String;)V",
                             requestID,
                             longFromHandle(vmiHandle),
                             jPropertyName.get(),
                             MakeJString(env, data.stringValue).get());
                break;
            case rive::DataType::boolean:
                m_queue.call("onBooleanPropertyUpdated",
                             "(JJLjava/lang/String;Z)V",
                             requestID,
                             longFromHandle(vmiHandle),
                             jPropertyName.get(),
                             data.boolValue);
                break;
            case rive::DataType::enumType:
                m_queue.call("onEnumPropertyUpdated",
                             "(JJLjava/lang/String;Ljava/"
                             "lang/String;)V",
                             requestID,
                             longFromHandle(vmiHandle),
                             jPropertyName.get(),
                             MakeJString(env, data.stringValue).get());
                break;
            case rive::DataType::color:
                m_queue.call("onColorPropertyUpdated",
                             "(JJLjava/lang/String;I)V",
                             requestID,
                             longFromHandle(vmiHandle),
                             jPropertyName.get(),
                             data.colorValue);
                break;
            case rive::DataType::trigger:
                m_queue.call("onTriggerPropertyUpdated",
                             "(JJLjava/lang/String;)V",
                             requestID,
                             longFromHandle(vmiHandle),
                             jPropertyName.get());
                break;
            default:
                LOGE("Unknown ViewModelInstance property type: %d",
                     static_cast<int>(data.metaData.type));
        }
    }

private:
    JCommandQueue m_queue;
};

class ImageListener : public rive::CommandQueue::RenderImageListener
{
public:
    ImageListener(JNIEnv* env, jobject jQueue) :
        rive::CommandQueue::RenderImageListener(), m_queue(env, jQueue)
    {}

    virtual ~ImageListener() = default;

    void onRenderImageDecoded(const rive::RenderImageHandle handle,
                              uint64_t requestID) override
    {
        m_queue.call("onImageDecoded",
                     "(JJ)V",
                     requestID,
                     longFromHandle(handle));
    }

    void onRenderImageError(const rive::RenderImageHandle,
                            uint64_t requestID,
                            std::string error) override
    {
        auto jError = MakeJString(m_queue.env(), error);
        m_queue.call("onImageError",
                     "(JLjava/lang/String;)V",
                     requestID,
                     jError.get());
    }

private:
    JCommandQueue m_queue;
};

class AudioListener : public rive::CommandQueue::AudioSourceListener
{
public:
    AudioListener(JNIEnv* env, jobject queue) :
        rive::CommandQueue::AudioSourceListener(), m_queue(env, queue)
    {}

    virtual ~AudioListener() = default;

    void onAudioSourceDecoded(const rive::AudioSourceHandle handle,
                              uint64_t requestID) override
    {
        m_queue.call("onAudioDecoded",
                     "(JJ)V",
                     requestID,
                     longFromHandle(handle));
    }

    void onAudioSourceError(const rive::AudioSourceHandle,
                            uint64_t requestID,
                            std::string error) override
    {
        auto jError = MakeJString(m_queue.env(), error);
        m_queue.call("onAudioError",
                     "(JLjava/lang/String;)V",
                     requestID,
                     jError.get());
    }

private:
    JCommandQueue m_queue;
};

class FontListener : public rive::CommandQueue::FontListener
{
public:
    FontListener(JNIEnv* env, jobject jQueue) :
        rive::CommandQueue::FontListener(), m_queue(env, jQueue)
    {}

    virtual ~FontListener() = default;

    void onFontDecoded(const rive::FontHandle handle,
                       uint64_t requestID) override
    {
        m_queue.call("onFontDecoded",
                     "(JJ)V",
                     requestID,
                     longFromHandle(handle));
    }

    void onFontError(const rive::FontHandle,
                     uint64_t requestID,
                     std::string error) override
    {
        auto jError = MakeJString(m_queue.env(), error);
        m_queue.call("onFontError",
                     "(JLjava/lang/String;)V",
                     requestID,
                     jError.get());
    }

private:
    JCommandQueue m_queue;
};

/** Typedef for the below setProperty function. */
template <typename T>
using PropertySetter =
    void (rive::CommandQueue::*)(rive::ViewModelInstanceHandle,
                                 std::string,
                                 T,
                                 uint64_t);
/** A generic setter for all property types. */
template <typename T>
void setProperty(JNIEnv* env,
                 jlong ref,
                 jlong jViewModelInstanceHandle,
                 jstring jPropertyPath,
                 T value,
                 PropertySetter<T> setter)
{
    auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
    auto viewModelInstanceHandle =
        handleFromLong<rive::ViewModelInstanceHandle>(jViewModelInstanceHandle);
    auto propertyPath = JStringToString(env, jPropertyPath);

    (commandQueue->*setter)(viewModelInstanceHandle,
                            propertyPath,
                            value,
                            0); // Pass 0 for requestID
}

/** Typedef for the below getProperty function. */
using PropertyGetter =
    void (rive::CommandQueue::*)(rive::ViewModelInstanceHandle,
                                 std::string,
                                 uint64_t);
/** A generic getter for all property types. */
void getProperty(JNIEnv* env,
                 jlong ref,
                 jlong requestID,
                 jlong jViewModelInstanceHandle,
                 jstring jPropertyPath,
                 PropertyGetter getter)
{
    auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
    auto viewModelInstanceHandle =
        handleFromLong<rive::ViewModelInstanceHandle>(jViewModelInstanceHandle);
    auto propertyPath = JStringToString(env, jPropertyPath);

    (commandQueue->*getter)(viewModelInstanceHandle, propertyPath, requestID);
}

/** Create a 1x1 PBuffer surface to bind before Android provides a surface. */
EGLSurface createPBufferSurface(EGLDisplay eglDisplay, EGLContext eglContext)
{
    EGLint configID = 0;
    eglQueryContext(eglDisplay, eglContext, EGL_CONFIG_ID, &configID);

    EGLConfig config;
    EGLint configCount = 0;
    EGLint configAttributes[] = {EGL_CONFIG_ID, configID, EGL_NONE};
    eglChooseConfig(eglDisplay, configAttributes, &config, 1, &configCount);

    // We expect only one config.
    if (configCount == 1)
    {
        EGLint pBufferAttributes[] = {EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE};
        return eglCreatePbufferSurface(eglDisplay, config, pBufferAttributes);
    }
    else
    {
        LOGE("Failed to choose EGL config for PBuffer surface");
        return EGL_NO_SURFACE;
    }
}

extern "C"
{
    JNIEXPORT jlong JNICALL
    Java_app_rive_core_CommandQueue_cppConstructor(JNIEnv* env,
                                                   jobject,
                                                   jlong display,
                                                   jlong context)
    {
        auto commandQueue =
            rive::rcp<rive::CommandQueue>(new rive::CommandQueue());
        auto eglDisplay = reinterpret_cast<EGLDisplay>(display);
        auto eglContext = reinterpret_cast<EGLContext>(context);

        struct StartupResult
        {
            bool success;
            EGLint eglError;
            std::string message;
        };
        // Used by the CommandServer thread to signal startup success or failure
        std::promise<StartupResult> promise;
        // Blocks the main thread until the CommandServer is ready
        std::future<StartupResult> f = promise.get_future();

        // Setup the C++ thread that drives the CommandServer
        std::thread([eglDisplay,
                     eglContext,
                     commandQueue,
                     // Move the promise into the thread and mark mutable so we
                     // can `set_value`
                     promise = std::move(promise)]() mutable {
            JNIEnv* env = nullptr;
            JavaVMAttachArgs args{.version = JNI_VERSION_1_6,
                                  .name = "Rive CmdServer",
                                  .group = nullptr};
            auto attachResult = g_JVM->AttachCurrentThread(&env, &args);
            if (attachResult != JNI_OK)
            {
                LOGE("Failed to attach thread to JVM: %d", attachResult);
                promise.set_value(
                    {false, EGL_BAD_ALLOC, "Failed to attach thread to JVM"});
                return;
            }

            // Create a 1x1 PBuffer to bind to the context (some devices do not
            // support surface-less bindings)
            // We must have a valid binding for `MakeContext` to succeed,
            auto pBuffer = createPBufferSurface(eglDisplay, eglContext);
            if (pBuffer == EGL_NO_SURFACE)
            {
                auto error = eglGetError();
                LOGE("Failed to create PBuffer surface. Error: (0x%04x)",
                     error);
                promise.set_value(
                    {false, error, "Failed to create PBuffer surface"});

                // Cleanup JVM thread attachment
                g_JVM->DetachCurrentThread();
                return;
            }

            auto contextCurrentSuccess =
                eglMakeCurrent(eglDisplay, pBuffer, pBuffer, eglContext);
            if (!contextCurrentSuccess)
            {
                auto error = eglGetError();
                LOGE("Failed to make EGL context current. Error: (0x%04x)",
                     error);
                promise.set_value(
                    {false, error, "Failed to make EGL context current"});

                // Cleanup PBuffer and JVM thread attachment
                if (pBuffer != EGL_NO_SURFACE)
                {
                    eglDestroySurface(eglDisplay, pBuffer);
                }
                g_JVM->DetachCurrentThread();
                return;
            }

            auto riveContext = rive::gpu::RenderContextGLImpl::MakeContext();
            if (!riveContext)
            {
                auto error = eglGetError();
                LOGE("Failed to create Rive RenderContextGL. Error: (0x%04x)",
                     error);
                promise.set_value(
                    {false, error, "Failed to create Rive RenderContextGL"});

                // Cleanup PBuffer and JVM thread attachment
                if (pBuffer != EGL_NO_SURFACE)
                {
                    eglDestroySurface(eglDisplay, pBuffer);
                }
                g_JVM->DetachCurrentThread();
                return;
            }

            auto commandServer =
                std::make_unique<rive::CommandServer>(commandQueue,
                                                      riveContext.get());

            // Signal success and unblock the main thread
            promise.set_value(
                {true, EGL_SUCCESS, "Command Server started successfully"});

            // Begin the serving loop. This will "block" the thread until the
            // server receives the disconnect command.
            commandServer->serveUntilDisconnect();

            // Matching unref from constructor since we release()'d.
            // Ensures the command queue outlives the command server's run.
            commandQueue->unref();

            // Cleanup the EGL context and surface
            eglMakeCurrent(eglDisplay,
                           EGL_NO_SURFACE,
                           EGL_NO_SURFACE,
                           EGL_NO_CONTEXT);
            if (pBuffer != EGL_NO_SURFACE)
            {
                eglDestroySurface(eglDisplay, pBuffer);
            }

            // Cleanup JVM thread attachment
            g_JVM->DetachCurrentThread();
        }).detach();

        // Wait for the command server to start and return the result
        auto result = f.get();
        if (!result.success)
        {
            // Surface error to Java
            auto jRTEClass = FindClass(env, "java/lang/RuntimeException");
            char messageBuffer[256];
            snprintf(messageBuffer,
                     sizeof(messageBuffer),
                     "CommandQueue startup failed (EGL 0x%04x): %s",
                     result.eglError,
                     result.message.c_str());
            env->ThrowNew(jRTEClass.get(), messageBuffer);

            // Return null pointer
            return 0L;
        }

        // Ref count is currently 1, unref'd on disconnect.
        return reinterpret_cast<jlong>(commandQueue.release());
    }

    JNIEXPORT void JNICALL Java_app_rive_core_CommandQueue_cppDelete(JNIEnv*,
                                                                     jobject,
                                                                     jlong ref)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        commandQueue->disconnect();
    }

    JNIEXPORT jobject JNICALL
    Java_app_rive_core_CommandQueue_cppCreateListeners(JNIEnv* env,
                                                       jobject jCommandQueue,
                                                       jlong ref)
    {
        auto listenersClass = FindClass(env, "app/rive/core/Listeners");
        auto listenersConstructor =
            env->GetMethodID(listenersClass.get(), "<init>", "(JJJJJJJ)V");

        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);

        auto fileListener = new FileListener(env, jCommandQueue);
        auto artboardListener = new ArtboardListener(env, jCommandQueue);
        auto stateMachineListener =
            new StateMachineListener(env, jCommandQueue);
        auto viewModelInstanceListener =
            new ViewModelInstanceListener(env, jCommandQueue);
        auto imageListener = new ImageListener(env, jCommandQueue);
        auto audioListener = new AudioListener(env, jCommandQueue);
        auto fontListener = new FontListener(env, jCommandQueue);

        commandQueue->setGlobalFileListener(fileListener);
        commandQueue->setGlobalArtboardListener(artboardListener);
        commandQueue->setGlobalStateMachineListener(stateMachineListener);
        commandQueue->setGlobalViewModelInstanceListener(
            viewModelInstanceListener);
        commandQueue->setGlobalRenderImageListener(imageListener);
        commandQueue->setGlobalAudioSourceListener(audioListener);
        commandQueue->setGlobalFontListener(fontListener);

        auto listeners =
            MakeObject(env,
                       listenersClass.get(),
                       listenersConstructor,
                       reinterpret_cast<jlong>(fileListener),
                       reinterpret_cast<jlong>(artboardListener),
                       reinterpret_cast<jlong>(stateMachineListener),
                       reinterpret_cast<jlong>(viewModelInstanceListener),
                       reinterpret_cast<jlong>(imageListener),
                       reinterpret_cast<jlong>(audioListener),
                       reinterpret_cast<jlong>(fontListener));
        return listeners.release();
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_core_CommandQueue_cppCreateRenderTarget(JNIEnv* env,
                                                          jobject,
                                                          jint width,
                                                          jint height)
    {
        // TODO: Move this off the main thread.
        GLint sampleCount;
        glGetIntegerv(GL_SAMPLES, &sampleCount);

        auto renderTarget =
            new rive::gpu::FramebufferRenderTargetGL(width,
                                                     height,
                                                     0, // externalFramebufferID
                                                     sampleCount);
        return reinterpret_cast<jlong>(renderTarget);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppLoadFile(JNIEnv* env,
                                                jobject,
                                                jlong ref,
                                                jlong requestID,
                                                jbyteArray bytes)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto byteVec = ByteArrayToUint8Vec(env, bytes);

        commandQueue->loadFile(byteVec, nullptr, requestID);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppDeleteFile(JNIEnv*,
                                                  jobject,
                                                  jlong ref,
                                                  jlong requestID,
                                                  jlong jFileHandle)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        commandQueue->deleteFile(handleFromLong<rive::FileHandle>(jFileHandle),
                                 requestID);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppGetArtboardNames(JNIEnv*,
                                                        jobject,
                                                        jlong ref,
                                                        jlong requestID,
                                                        jlong jFileHandle)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto fileHandle = handleFromLong<rive::FileHandle>(jFileHandle);

        commandQueue->requestArtboardNames(fileHandle, requestID);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppGetStateMachineNames(
        JNIEnv*,
        jobject,
        jlong ref,
        jlong requestID,
        jlong jArtboardHandle)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto artboardHandle =
            handleFromLong<rive::ArtboardHandle>(jArtboardHandle);

        commandQueue->requestStateMachineNames(artboardHandle, requestID);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppGetViewModelNames(JNIEnv*,
                                                         jobject,
                                                         jlong ref,
                                                         jlong requestID,
                                                         jlong jFileHandle)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto fileHandle = handleFromLong<rive::FileHandle>(jFileHandle);

        commandQueue->requestViewModelNames(fileHandle, requestID);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppGetViewModelInstanceNames(
        JNIEnv* env,
        jobject,
        jlong ref,
        jlong requestID,
        jlong jFileHandle,
        jstring jViewModelName)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto name = JStringToString(env, jViewModelName);
        auto fileHandle = handleFromLong<rive::FileHandle>(jFileHandle);

        commandQueue->requestViewModelInstanceNames(fileHandle,
                                                    name,
                                                    requestID);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppGetViewModelProperties(
        JNIEnv* env,
        jobject,
        jlong ref,
        jlong requestID,
        jlong jFileHandle,
        jstring jViewModelName)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto name = JStringToString(env, jViewModelName);
        auto fileHandle = handleFromLong<rive::FileHandle>(jFileHandle);

        commandQueue->requestViewModelPropertyDefinitions(fileHandle,
                                                          name,
                                                          requestID);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppGetEnums(JNIEnv*,
                                                jobject,
                                                jlong ref,
                                                jlong requestID,
                                                jlong jFileHandle)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto fileHandle = handleFromLong<rive::FileHandle>(jFileHandle);

        commandQueue->requestViewModelEnums(fileHandle, requestID);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_core_CommandQueue_cppCreateDefaultArtboard(JNIEnv*,
                                                             jobject,
                                                             jlong ref,
                                                             jlong requestID,
                                                             jlong jFileHandle)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);

        auto artboard = commandQueue->instantiateDefaultArtboard(
            handleFromLong<rive::FileHandle>(jFileHandle),
            nullptr,
            requestID);
        return longFromHandle(artboard);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_core_CommandQueue_cppCreateArtboardByName(JNIEnv* env,
                                                            jobject,
                                                            jlong ref,
                                                            jlong requestID,
                                                            jlong jFileHandle,
                                                            jstring name)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto nativeName = JStringToString(env, name);

        auto artboard = commandQueue->instantiateArtboardNamed(
            handleFromLong<rive::FileHandle>(jFileHandle),
            nativeName,
            nullptr,
            requestID);
        return longFromHandle(artboard);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppDeleteArtboard(JNIEnv*,
                                                      jobject,
                                                      jlong ref,
                                                      jlong requestID,
                                                      jlong jArtboardHandle)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        commandQueue->deleteArtboard(
            handleFromLong<rive::ArtboardHandle>(jArtboardHandle),
            requestID);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_core_CommandQueue_cppCreateDefaultStateMachine(
        JNIEnv*,
        jobject,
        jlong ref,
        jlong requestID,
        jlong jArtboardHandle)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);

        auto stateMachine = commandQueue->instantiateDefaultStateMachine(
            handleFromLong<rive::ArtboardHandle>(jArtboardHandle),
            nullptr,
            requestID);
        return longFromHandle(stateMachine);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_core_CommandQueue_cppCreateStateMachineByName(
        JNIEnv* env,
        jobject,
        jlong ref,
        jlong requestID,
        jlong jArtboardHandle,
        jstring name)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto nativeName = JStringToString(env, name);

        auto stateMachine = commandQueue->instantiateStateMachineNamed(
            handleFromLong<rive::ArtboardHandle>(jArtboardHandle),
            nativeName,
            nullptr,
            requestID);
        return longFromHandle(stateMachine);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppDeleteStateMachine(
        JNIEnv*,
        jobject,
        jlong ref,
        jlong requestID,
        jlong stateMachineHandle)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        commandQueue->deleteStateMachine(
            handleFromLong<rive::StateMachineHandle>(stateMachineHandle),
            requestID);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppAdvanceStateMachine(
        JNIEnv*,
        jobject,
        jlong ref,
        jlong stateMachineHandle,
        jlong deltaTimeNs)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto deltaSeconds = static_cast<float_t>(deltaTimeNs) / 1e9f; // NS to S
        commandQueue->advanceStateMachine(
            handleFromLong<rive::StateMachineHandle>(stateMachineHandle),
            deltaSeconds);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_core_CommandQueue_cppNamedVMCreateBlankVMI(
        JNIEnv* env,
        jobject,
        jlong ref,
        jlong requestID,
        jlong jFileHandle,
        jstring jViewModelName)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto fileHandle = handleFromLong<rive::FileHandle>(jFileHandle);
        auto viewModelName = JStringToString(env, jViewModelName);

        auto viewModelInstance =
            commandQueue->instantiateBlankViewModelInstance(fileHandle,
                                                            viewModelName,
                                                            nullptr,
                                                            requestID);
        return longFromHandle(viewModelInstance);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_core_CommandQueue_cppDefaultVMCreateBlankVMI(
        JNIEnv*,
        jobject,
        jlong ref,
        jlong requestID,
        jlong jFileHandle,
        jlong jArtboardHandle)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto fileHandle = handleFromLong<rive::FileHandle>(jFileHandle);
        auto artboardHandle =
            handleFromLong<rive::ArtboardHandle>(jArtboardHandle);

        auto viewModelInstance =
            commandQueue->instantiateBlankViewModelInstance(fileHandle,
                                                            artboardHandle,
                                                            nullptr,
                                                            requestID);
        return longFromHandle(viewModelInstance);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_core_CommandQueue_cppNamedVMCreateDefaultVMI(
        JNIEnv* env,
        jobject,
        jlong ref,
        jlong requestID,
        jlong jFileHandle,
        jstring jViewModelName)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto fileHandle = handleFromLong<rive::FileHandle>(jFileHandle);
        auto viewModelName = JStringToString(env, jViewModelName);

        auto viewModelInstance =
            commandQueue->instantiateDefaultViewModelInstance(fileHandle,
                                                              viewModelName,
                                                              nullptr,
                                                              requestID);
        return longFromHandle(viewModelInstance);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_core_CommandQueue_cppDefaultVMCreateDefaultVMI(
        JNIEnv*,
        jobject,
        jlong ref,
        jlong requestID,
        jlong jFileHandle,
        jlong jArtboardHandle)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto fileHandle = handleFromLong<rive::FileHandle>(jFileHandle);
        auto artboardHandle =
            handleFromLong<rive::ArtboardHandle>(jArtboardHandle);

        auto viewModelInstance =
            commandQueue->instantiateDefaultViewModelInstance(fileHandle,
                                                              artboardHandle,
                                                              nullptr,
                                                              requestID);
        return longFromHandle(viewModelInstance);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_core_CommandQueue_cppNamedVMCreateNamedVMI(
        JNIEnv* env,
        jobject,
        jlong ref,
        jlong requestID,
        jlong jFileHandle,
        jstring jViewModelName,
        jstring jInstanceName)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto fileHandle = handleFromLong<rive::FileHandle>(jFileHandle);
        auto viewModelName = JStringToString(env, jViewModelName);
        auto instanceName = JStringToString(env, jInstanceName);

        auto viewModelInstance =
            commandQueue->instantiateViewModelInstanceNamed(fileHandle,
                                                            viewModelName,
                                                            instanceName,
                                                            nullptr,
                                                            requestID);
        return longFromHandle(viewModelInstance);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_core_CommandQueue_cppDefaultVMCreateNamedVMI(
        JNIEnv* env,
        jobject,
        jlong ref,
        jlong requestID,
        jlong jFileHandle,
        jlong jArtboardHandle,
        jstring jInstanceName)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto fileHandle = handleFromLong<rive::FileHandle>(jFileHandle);
        auto artboardHandle =
            handleFromLong<rive::ArtboardHandle>(jArtboardHandle);
        auto instanceName = JStringToString(env, jInstanceName);

        auto viewModelInstance =
            commandQueue->instantiateViewModelInstanceNamed(fileHandle,
                                                            artboardHandle,
                                                            instanceName,
                                                            nullptr,
                                                            requestID);
        return longFromHandle(viewModelInstance);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_core_CommandQueue_cppReferenceNestedVMI(
        JNIEnv* env,
        jobject,
        jlong ref,
        jlong requestID,
        jlong jViewModelInstanceHandle,
        jstring jPath)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto viewModelInstanceHandle =
            handleFromLong<rive::ViewModelInstanceHandle>(
                jViewModelInstanceHandle);
        auto path = JStringToString(env, jPath);

        auto nestedViewModelInstance =
            commandQueue->referenceNestedViewModelInstance(
                viewModelInstanceHandle,
                path,
                nullptr,
                requestID);
        return longFromHandle(nestedViewModelInstance);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppDeleteViewModelInstance(
        JNIEnv*,
        jobject,
        jlong ref,
        jlong requestID,
        jlong jViewModelInstanceHandle)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        commandQueue->deleteViewModelInstance(
            handleFromLong<rive::ViewModelInstanceHandle>(
                jViewModelInstanceHandle),
            requestID);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppBindViewModelInstance(
        JNIEnv*,
        jobject,
        jlong ref,
        jlong requestID,
        jlong jStateMachineHandle,
        jlong jViewModelInstanceHandle)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto stateMachineHandle =
            handleFromLong<rive::StateMachineHandle>(jStateMachineHandle);
        auto viewModelInstanceHandle =
            handleFromLong<rive::ViewModelInstanceHandle>(
                jViewModelInstanceHandle);

        commandQueue->bindViewModelInstance(stateMachineHandle,
                                            viewModelInstanceHandle,
                                            requestID);
    }

    JNIEXPORT void JNICALL Java_app_rive_core_CommandQueue_cppSetNumberProperty(
        JNIEnv* env,
        jobject,
        jlong ref,
        jlong jViewModelInstanceHandle,
        jstring jPropertyPath,
        jfloat value)
    {
        setProperty(env,
                    ref,
                    jViewModelInstanceHandle,
                    jPropertyPath,
                    value,
                    &rive::CommandQueue::setViewModelInstanceNumber);
    }

    JNIEXPORT void JNICALL Java_app_rive_core_CommandQueue_cppGetNumberProperty(
        JNIEnv* env,
        jobject,
        jlong ref,
        jlong requestID,
        jlong jViewModelInstanceHandle,
        jstring jPropertyPath)
    {
        getProperty(env,
                    ref,
                    requestID,
                    jViewModelInstanceHandle,
                    jPropertyPath,
                    &rive::CommandQueue::requestViewModelInstanceNumber);
    }

    JNIEXPORT void JNICALL Java_app_rive_core_CommandQueue_cppSetStringProperty(
        JNIEnv* env,
        jobject,
        jlong ref,
        jlong jViewModelInstanceHandle,
        jstring jPropertyPath,
        jstring jValue)
    {
        auto value = JStringToString(env, jValue);
        setProperty(env,
                    ref,
                    jViewModelInstanceHandle,
                    jPropertyPath,
                    value,
                    &rive::CommandQueue::setViewModelInstanceString);
    }

    JNIEXPORT void JNICALL Java_app_rive_core_CommandQueue_cppGetStringProperty(
        JNIEnv* env,
        jobject,
        jlong ref,
        jlong requestID,
        jlong jViewModelInstanceHandle,
        jstring jPropertyPath)
    {
        getProperty(env,
                    ref,
                    requestID,
                    jViewModelInstanceHandle,
                    jPropertyPath,
                    &rive::CommandQueue::requestViewModelInstanceString);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppSetBooleanProperty(
        JNIEnv* env,
        jobject,
        jlong ref,
        jlong jViewModelInstanceHandle,
        jstring jPropertyPath,
        jboolean jValue)
    {
        setProperty<bool>(env,
                          ref,
                          jViewModelInstanceHandle,
                          jPropertyPath,
                          jValue,
                          &rive::CommandQueue::setViewModelInstanceBool);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppGetBooleanProperty(
        JNIEnv* env,
        jobject,
        jlong ref,
        jlong requestID,
        jlong jViewModelInstanceHandle,
        jstring jPropertyPath)
    {
        getProperty(env,
                    ref,
                    requestID,
                    jViewModelInstanceHandle,
                    jPropertyPath,
                    &rive::CommandQueue::requestViewModelInstanceBool);
    }

    JNIEXPORT void JNICALL Java_app_rive_core_CommandQueue_cppSetEnumProperty(
        JNIEnv* env,
        jobject,
        jlong ref,
        jlong jViewModelInstanceHandle,
        jstring jPropertyPath,
        jstring jValue)
    {
        auto value = JStringToString(env, jValue);
        setProperty(env,
                    ref,
                    jViewModelInstanceHandle,
                    jPropertyPath,
                    value,
                    &rive::CommandQueue::setViewModelInstanceEnum);
    }

    JNIEXPORT void JNICALL Java_app_rive_core_CommandQueue_cppGetEnumProperty(
        JNIEnv* env,
        jobject,
        jlong ref,
        jlong requestID,
        jlong jViewModelInstanceHandle,
        jstring jPropertyPath)
    {
        getProperty(env,
                    ref,
                    requestID,
                    jViewModelInstanceHandle,
                    jPropertyPath,
                    &rive::CommandQueue::requestViewModelInstanceEnum);
    }

    JNIEXPORT void JNICALL Java_app_rive_core_CommandQueue_cppSetColorProperty(
        JNIEnv* env,
        jobject,
        jlong ref,
        jlong jViewModelInstanceHandle,
        jstring jPropertyPath,
        jint jValue)
    {
        // ColorInt is uint32_t in C++
        auto value = static_cast<uint32_t>(jValue);
        setProperty(env,
                    ref,
                    jViewModelInstanceHandle,
                    jPropertyPath,
                    value,
                    &rive::CommandQueue::setViewModelInstanceColor);
    }

    JNIEXPORT void JNICALL Java_app_rive_core_CommandQueue_cppGetColorProperty(
        JNIEnv* env,
        jobject,
        jlong ref,
        jlong requestID,
        jlong jViewModelInstanceHandle,
        jstring jPropertyPath)
    {
        getProperty(env,
                    ref,
                    requestID,
                    jViewModelInstanceHandle,
                    jPropertyPath,
                    &rive::CommandQueue::requestViewModelInstanceColor);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppFireTriggerProperty(
        JNIEnv* env,
        jobject,
        jlong ref,
        jlong jViewModelInstanceHandle,
        jstring jPropertyPath)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto viewModelInstanceHandle =
            handleFromLong<rive::ViewModelInstanceHandle>(
                jViewModelInstanceHandle);
        auto propertyPath = JStringToString(env, jPropertyPath);

        commandQueue->fireViewModelTrigger(viewModelInstanceHandle,
                                           propertyPath);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppSubscribeToProperty(
        JNIEnv* env,
        jobject,
        jlong ref,
        jlong jViewModelInstanceHandle,
        jstring jPropertyPath,
        jint propertyType)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto viewModelInstanceHandle =
            handleFromLong<rive::ViewModelInstanceHandle>(
                jViewModelInstanceHandle);
        auto propertyPath = JStringToString(env, jPropertyPath);
        auto dataType = static_cast<rive::DataType>(propertyType);

        commandQueue->subscribeToViewModelProperty(viewModelInstanceHandle,
                                                   propertyPath,
                                                   dataType);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppUnsubscribeFromProperty(
        JNIEnv* env,
        jobject,
        jlong ref,
        jlong jViewModelInstanceHandle,
        jstring jPropertyPath,
        jint propertyType)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto viewModelInstanceHandle =
            handleFromLong<rive::ViewModelInstanceHandle>(
                jViewModelInstanceHandle);
        auto propertyPath = JStringToString(env, jPropertyPath);
        auto dataType = static_cast<rive::DataType>(propertyType);

        commandQueue->unsubscribeToViewModelProperty(viewModelInstanceHandle,
                                                     propertyPath,
                                                     dataType);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppDecodeImage(JNIEnv* env,
                                                   jobject,
                                                   jlong ref,
                                                   jlong requestID,
                                                   jbyteArray bytes)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto byteVec = ByteArrayToUint8Vec(env, bytes);

        commandQueue->decodeImage(byteVec, nullptr, requestID);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppDeleteImage(JNIEnv*,
                                                   jobject,
                                                   jlong ref,
                                                   jlong jImageHandle)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto imageHandle =
            handleFromLong<rive::RenderImageHandle>(jImageHandle);

        commandQueue->deleteImage(imageHandle);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppRegisterImage(JNIEnv* env,
                                                     jobject,
                                                     jlong ref,
                                                     jstring jPath,
                                                     jlong jImageHandle)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto path = JStringToString(env, jPath);
        auto imageHandle =
            handleFromLong<rive::RenderImageHandle>(jImageHandle);

        commandQueue->addGlobalImageAsset(path, imageHandle);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppUnregisterImage(JNIEnv* env,
                                                       jobject,
                                                       jlong ref,
                                                       jstring jPath)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto path = JStringToString(env, jPath);

        commandQueue->removeGlobalImageAsset(path);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppDecodeAudio(JNIEnv* env,
                                                   jobject,
                                                   jlong ref,
                                                   jlong requestID,
                                                   jbyteArray bytes)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto byteVec = ByteArrayToUint8Vec(env, bytes);

        commandQueue->decodeAudio(byteVec, nullptr, requestID);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppDeleteAudio(JNIEnv*,
                                                   jobject,
                                                   jlong ref,
                                                   jlong jAudioHandle)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto audioHandle =
            handleFromLong<rive::AudioSourceHandle>(jAudioHandle);

        commandQueue->deleteAudio(audioHandle);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppRegisterAudio(JNIEnv* env,
                                                     jobject,
                                                     jlong ref,
                                                     jstring jPath,
                                                     jlong jAudioHandle)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto path = JStringToString(env, jPath);
        auto audioHandle =
            handleFromLong<rive::AudioSourceHandle>(jAudioHandle);

        commandQueue->addGlobalAudioAsset(path, audioHandle);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppUnregisterAudio(JNIEnv* env,
                                                       jobject,
                                                       jlong ref,
                                                       jstring jPath)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto path = JStringToString(env, jPath);

        commandQueue->removeGlobalAudioAsset(path);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppDecodeFont(JNIEnv* env,
                                                  jobject,
                                                  jlong ref,
                                                  jlong requestID,
                                                  jbyteArray bytes)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto byteVec = ByteArrayToUint8Vec(env, bytes);

        commandQueue->decodeFont(byteVec, nullptr, requestID);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppDeleteFont(JNIEnv*,
                                                  jobject,
                                                  jlong ref,
                                                  jlong jFontHandle)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto fontHandle = handleFromLong<rive::FontHandle>(jFontHandle);

        commandQueue->deleteFont(fontHandle);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppRegisterFont(JNIEnv* env,
                                                    jobject,
                                                    jlong ref,
                                                    jstring jPath,
                                                    jlong jFontHandle)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto path = JStringToString(env, jPath);
        auto fontHandle = handleFromLong<rive::FontHandle>(jFontHandle);

        commandQueue->addGlobalFontAsset(path, fontHandle);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppUnregisterFont(JNIEnv* env,
                                                      jobject,
                                                      jlong ref,
                                                      jstring jPath)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto path = JStringToString(env, jPath);

        commandQueue->removeGlobalFontAsset(path);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppPointerMove(JNIEnv* env,
                                                   jobject,
                                                   jlong ref,
                                                   jlong stateMachineHandle,
                                                   jobject jFit,
                                                   jobject jAlignment,
                                                   jfloat surfaceWidth,
                                                   jfloat surfaceHeight,
                                                   jint pointerID,
                                                   jfloat pointerX,
                                                   jfloat pointerY)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        rive::CommandQueue::PointerEvent event{
            .fit = GetFit(env, jFit),
            .alignment = GetAlignment(env, jAlignment),
            .screenBounds = rive::Vec2D(static_cast<float_t>(surfaceWidth),
                                        static_cast<float_t>(surfaceHeight)),
            .position = rive::Vec2D(static_cast<float_t>(pointerX),
                                    static_cast<float_t>(pointerY))};
        event.pointerId = static_cast<int>(pointerID);

        commandQueue->pointerMove(
            handleFromLong<rive::StateMachineHandle>(stateMachineHandle),
            event);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppPointerDown(JNIEnv* env,
                                                   jobject,
                                                   jlong ref,
                                                   jlong stateMachineHandle,
                                                   jobject jFit,
                                                   jobject jAlignment,
                                                   jfloat surfaceWidth,
                                                   jfloat surfaceHeight,
                                                   jint pointerID,
                                                   jfloat pointerX,
                                                   jfloat pointerY)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        rive::CommandQueue::PointerEvent event{
            .fit = GetFit(env, jFit),
            .alignment = GetAlignment(env, jAlignment),
            .screenBounds = rive::Vec2D(static_cast<float_t>(surfaceWidth),
                                        static_cast<float_t>(surfaceHeight)),
            .position = rive::Vec2D(static_cast<float_t>(pointerX),
                                    static_cast<float_t>(pointerY))};
        event.pointerId = static_cast<int>(pointerID);

        commandQueue->pointerDown(
            handleFromLong<rive::StateMachineHandle>(stateMachineHandle),
            event);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppPointerUp(JNIEnv* env,
                                                 jobject,
                                                 jlong ref,
                                                 jlong stateMachineHandle,
                                                 jobject jFit,
                                                 jobject jAlignment,
                                                 jfloat surfaceWidth,
                                                 jfloat surfaceHeight,
                                                 jint pointerID,
                                                 jfloat pointerX,
                                                 jfloat pointerY)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        rive::CommandQueue::PointerEvent event{
            .fit = GetFit(env, jFit),
            .alignment = GetAlignment(env, jAlignment),
            .screenBounds = rive::Vec2D(static_cast<float_t>(surfaceWidth),
                                        static_cast<float_t>(surfaceHeight)),
            .position = rive::Vec2D(static_cast<float_t>(pointerX),
                                    static_cast<float_t>(pointerY))};
        event.pointerId = static_cast<int>(pointerID);

        commandQueue->pointerUp(
            handleFromLong<rive::StateMachineHandle>(stateMachineHandle),
            event);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppPointerExit(JNIEnv* env,
                                                   jobject,
                                                   jlong ref,
                                                   jlong stateMachineHandle,
                                                   jobject jFit,
                                                   jobject jAlignment,
                                                   jfloat surfaceWidth,
                                                   jfloat surfaceHeight,
                                                   jint pointerID,
                                                   jfloat pointerX,
                                                   jfloat pointerY)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        rive::CommandQueue::PointerEvent event{
            .fit = GetFit(env, jFit),
            .alignment = GetAlignment(env, jAlignment),
            .screenBounds = rive::Vec2D(static_cast<float_t>(surfaceWidth),
                                        static_cast<float_t>(surfaceHeight)),
            .position = rive::Vec2D(static_cast<float_t>(pointerX),
                                    static_cast<float_t>(pointerY))};
        event.pointerId = static_cast<int>(pointerID);

        commandQueue->pointerExit(
            handleFromLong<rive::StateMachineHandle>(stateMachineHandle),
            event);
    }

    JNIEXPORT jlong JNICALL
    Java_app_rive_core_CommandQueue_cppCreateDrawKey(JNIEnv*,
                                                     jobject,
                                                     jlong ref)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto drawKey = commandQueue->createDrawKey();
        return longFromHandle(drawKey);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppPollMessages(JNIEnv*, jobject, jlong ref)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        commandQueue->processMessages();
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppDraw(JNIEnv* env,
                                            jobject,
                                            jlong ref,
                                            jlong eglDisplayRef,
                                            jlong eglSurfaceRef,
                                            jlong eglContextRef,
                                            jlong drawKey,
                                            jlong artboardHandleRef,
                                            jlong stateMachineHandleRef,
                                            jlong renderTargetRef,
                                            jint width,
                                            jint height,
                                            jobject jFit,
                                            jobject jAlignment,
                                            jint jClearColor)
    {
        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);
        auto eglDisplay = reinterpret_cast<EGLDisplay>(eglDisplayRef);
        auto eglSurface = reinterpret_cast<EGLSurface>(eglSurfaceRef);
        auto eglContext = reinterpret_cast<EGLContext>(eglContextRef);
        auto renderTarget =
            reinterpret_cast<rive::gpu::RenderTargetGL*>(renderTargetRef);
        auto fit = GetFit(env, jFit);
        auto alignment = GetAlignment(env, jAlignment);
        auto clearColor = static_cast<uint32_t>(jClearColor);

        auto loop = [eglDisplay,
                     eglSurface,
                     eglContext,
                     artboardHandleRef,
                     stateMachineHandleRef,
                     renderTarget,
                     width,
                     height,
                     fit,
                     alignment,
                     clearColor](rive::DrawKey, rive::CommandServer* server) {
            auto artboard = server->getArtboardInstance(
                handleFromLong<rive::ArtboardHandle>(artboardHandleRef));
            if (artboard == nullptr)
            {
                return;
            }

            auto stateMachine = server->getStateMachineInstance(
                handleFromLong<rive::StateMachineHandle>(
                    stateMachineHandleRef));
            if (stateMachine == nullptr)
            {
                return;
            }

            // Make the EGL surface current
            eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);

            auto error = eglGetError();
            if (error != EGL_SUCCESS)
            {
                LOGE("EGL error setting context (SetSurface): %d", error);
                return;
            }

            // Stack allocate a Rive Renderer
            auto riveContext =
                static_cast<rive::gpu::RenderContext*>(server->factory());

            riveContext->beginFrame(rive::gpu::RenderContext::FrameDescriptor{
                .renderTargetWidth = static_cast<uint32_t>(width),
                .renderTargetHeight = static_cast<uint32_t>(height),
                .loadAction = rive::gpu::LoadAction::clear,
                .clearColor = clearColor,
            });

            auto renderer = rive::RiveRenderer(riveContext);

            // Draw the .riv
            renderer.align(fit,
                           alignment,
                           rive::AABB(0.0f,
                                      0.0f,
                                      static_cast<float_t>(width),
                                      static_cast<float_t>(height)),
                           artboard->bounds());
            artboard->draw(&renderer);

            // Flush the draw commands
            riveContext->flush({
                .renderTarget = renderTarget,
            });

            // Swap buffers
            eglSwapBuffers(eglDisplay, eglSurface);
        };
        commandQueue->draw(handleFromLong<rive::DrawKey>(drawKey), loop);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_RiveSurface_cppDelete(JNIEnv*,
                                             jobject,
                                             jlong renderTargetRef)
    {
        auto renderTarget =
            reinterpret_cast<rive::gpu::RenderTargetGL*>(renderTargetRef);
        delete renderTarget;
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_Listeners_cppDelete(JNIEnv*,
                                           jobject,
                                           jlong fileListenerRef,
                                           jlong artboardListenerRef,
                                           jlong stateMachineListenerRef,
                                           jlong viewModelInstanceListenerRef,
                                           jlong imageListenerRef,
                                           jlong audioListenerRef,
                                           jlong fontListenerRef)
    {
        auto fileListener = reinterpret_cast<FileListener*>(fileListenerRef);
        auto artboardListener =
            reinterpret_cast<ArtboardListener*>(artboardListenerRef);
        auto stateMachineListener =
            reinterpret_cast<StateMachineListener*>(stateMachineListenerRef);
        auto viewModelInstanceListener =
            reinterpret_cast<ViewModelInstanceListener*>(
                viewModelInstanceListenerRef);
        auto imageListener = reinterpret_cast<ImageListener*>(imageListenerRef);
        auto audioListener = reinterpret_cast<AudioListener*>(audioListenerRef);
        auto fontListener = reinterpret_cast<FontListener*>(fontListenerRef);

        delete fileListener;
        delete artboardListener;
        delete stateMachineListener;
        delete viewModelInstanceListener;
        delete imageListener;
        delete audioListener;
        delete fontListener;
    }
}

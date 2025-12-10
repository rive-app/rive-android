#include <jni.h>
#include <android/native_window_jni.h>
#include <GLES3/gl3.h>

#include "models/render_context.hpp"
#include "helpers/android_factories.hpp"
#include "helpers/jni_resource.hpp"
#include "helpers/rive_log.hpp"
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
#include <utility>
#include <vector>
#include <cstring>
#include <atomic>

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
        m_class(reinterpret_cast<jclass>(
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

    void onFileLoaded(const rive::FileHandle handle,
                      uint64_t requestID) override
    {
        m_queue.call("onFileLoaded", "(JJ)V", requestID, handle);
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

    void onArtboardError(const rive::ArtboardHandle,
                         uint64_t requestID,
                         std::string error) override
    {
        auto jError = MakeJString(m_queue.env(), error);
        m_queue.call("onArtboardError",
                     "(JLjava/lang/String;)V",
                     requestID,
                     jError.get());
    }

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

    void onStateMachineError(const rive::StateMachineHandle,
                             uint64_t requestID,
                             std::string error) override
    {
        auto jError = MakeJString(m_queue.env(), error);
        m_queue.call("onStateMachineError",
                     "(JLjava/lang/String;)V",
                     requestID,
                     jError.get());
    }

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

    void onViewModelInstanceError(const rive::ViewModelInstanceHandle,
                                  uint64_t requestID,
                                  std::string error) override
    {
        auto jError = MakeJString(m_queue.env(), error);
        m_queue.call("onViewModelInstanceError",
                     "(JLjava/lang/String;)V",
                     requestID,
                     jError.get());
    }

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
                RiveLogE(TAG,
                         "Unknown ViewModelInstance property type: %d",
                         static_cast<int>(data.metaData.type));
        }
    }

private:
    constexpr static const char* TAG = "RiveN/VMIListener";
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
                            std::move(value),
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

constexpr static const char* TAG_CQ = "RiveN/CQ";

class CommandQueueWithThread : public rive::CommandQueue
{
public:
    CommandQueueWithThread() : rive::CommandQueue() {}

    /**
     * Starts the command server thread.
     *
     * @param renderContext The render context to use in the command server
     * thread. Its resources will be created and destroyed within the thread,
     * but the object itself must still be managed by the caller and outlive the
     * thread.
     * @param promise A promise to signal startup success or failure.
     */
    void startCommandServer(RenderContext* renderContext,
                            std::promise<StartupResult>&& promise)
    {
        // Keep a strong ref while thread runs
        auto self = rive::rcp<CommandQueueWithThread>(this);

        m_commandServerThread = std::thread([renderContext,
                                             self,
                                             promise =
                                                 std::move(promise)]() mutable {
            const auto THREAD_NAME = "Rive CmdServer";
            JNIEnv* env = nullptr;
            RiveLogD(TAG_CQ, "Attaching command server thread to JVM");
            JavaVMAttachArgs args{.version = JNI_VERSION_1_6,
                                  .name = THREAD_NAME,
                                  .group = nullptr};
            auto attachResult = g_JVM->AttachCurrentThread(&env, &args);
            if (attachResult != JNI_OK)
            {
                RiveLogE(TAG_CQ,
                         "Failed to attach command server thread to JVM: %d",
                         attachResult);
                promise.set_value(
                    {false, EGL_BAD_ALLOC, "Failed to attach thread to JVM"});
                return;
            }

            RiveLogD(TAG_CQ, "Setting command server thread name");
            // Set the native thread name
            pthread_setname_np(pthread_self(), THREAD_NAME);
            // Set the JVM thread name
            // Scope the JniResource objects to fall out of scope and delete
            // local refs before detaching the thread (which makes the JNIEnv
            // invalid).
            {
                auto jThreadClass = FindClass(env, "java/lang/Thread");
                auto currentThreadFn =
                    env->GetStaticMethodID(jThreadClass.get(),
                                           "currentThread",
                                           "()Ljava/lang/Thread;");
                auto jThreadObj = MakeJniResource(
                    env->CallStaticObjectMethod(jThreadClass.get(),
                                                currentThreadFn),
                    env);

                auto setNameFn = env->GetMethodID(jThreadClass.get(),
                                                  "setName",
                                                  "(Ljava/lang/String;)V");
                auto jName = MakeJString(env, THREAD_NAME);
                env->CallVoidMethod(jThreadObj.get(), setNameFn, jName.get());
            }

            RiveLogD(TAG_CQ,
                     "Initializing Rive render context for command server");
            auto result = renderContext->initialize();
            if (!result.success)
            {
                RiveLogE(TAG_CQ,
                         "Failed to initialize the Rive render context");
                promise.set_value(result);
                return;
            }

            RiveLogD(TAG_CQ, "Creating command server");
            auto commandServer = std::make_unique<rive::CommandServer>(
                self,
                renderContext->riveContext.get());

            // Signal success and unblock the main thread
            promise.set_value(
                {true, EGL_SUCCESS, "Command Server started successfully"});

            // Begin the serving loop. This will "block" the thread until
            // the server receives the disconnect command.
            RiveLogD(TAG_CQ, "Beginning command server processing loop");
            commandServer->serveUntilDisconnect();

            RiveLogD(TAG_CQ, "Command server disconnected, cleaning up");

            // Matching unref from constructor since we release()'d.
            // Ensures the command queue outlives the command server's run.
            RiveLogD(TAG_CQ, "Deleting command queue");
            self->unref();

            RiveLogD(TAG_CQ, "Deleting render context");
            renderContext->destroy();

            // Cleanup JVM thread attachment
            RiveLogD(TAG_CQ, "Detaching command server thread from JVM");
            g_JVM->DetachCurrentThread();
        });
    }

    /**
     * Shuts down the command server by sending a disconnect command, then joins
     * the thread. This means it is a blocking call until the command server
     * thread has fully exited.
     */
    void shutdownAndJoin()
    {
        disconnect();
        if (m_commandServerThread.joinable())
        {
            m_commandServerThread.join();
        }
    }

    /**
     * Check if we've logged a missing artboard error already for this draw
     * key.
     */
    bool shouldLogArtboardNull(rive::DrawKey key)
    {
        return m_artboardNullKeys.insert(key).second;
    }

    /**
     * Check if we've logged a missing state machine error already for this
     * draw key.
     */
    bool shouldLogStateMachineNull(rive::DrawKey key)
    {
        return m_stateMachineNullKeys.insert(key).second;
    }

private:
    std::thread m_commandServerThread;
    // Holds that an error has been reported, to avoid log spam
    std::unordered_set<rive::DrawKey> m_artboardNullKeys;
    std::unordered_set<rive::DrawKey> m_stateMachineNullKeys;
};

extern "C"
{
    JNIEXPORT jlong JNICALL
    Java_app_rive_core_CommandQueueJNIBridge_cppConstructor(
        JNIEnv* env,
        jobject,
        jlong renderContextPtr)
    {
        auto* renderContext =
            reinterpret_cast<RenderContext*>(renderContextPtr);

        // Used by the CommandServer thread to signal startup success or failure
        std::promise<StartupResult> promise;
        std::future<StartupResult> resultFuture = promise.get_future();

        /* Create a command queue with an owned thread handle.
         * The ref count is now 2, one for the base constructor of RefCnt, one
         * for using `ref_rcp`. One is released in the CommandServer thread when
         * it shuts down. The other is released in cppDelete. */
        auto commandQueue =
            rive::ref_rcp<CommandQueueWithThread>(new CommandQueueWithThread());
        // Start the C++ thread that drives the CommandServer
        commandQueue->startCommandServer(renderContext, std::move(promise));

        // Wait for the command server to start, blocking the main thread, and
        // return the result
        auto result = resultFuture.get();
        if (!result.success)
        {
            // Surface error to Java
            auto jRiveInitializationExceptionClass =
                FindClass(env, "app/rive/RiveInitializationException");
            char messageBuffer[256];
            snprintf(messageBuffer,
                     sizeof(messageBuffer),
                     "CommandQueue startup failed (EGL 0x%04x): %s",
                     result.errorCode,
                     result.message.c_str());
            env->ThrowNew(jRiveInitializationExceptionClass.get(),
                          messageBuffer);

            // Return null pointer
            return 0L;
        }

        return reinterpret_cast<jlong>(commandQueue.release());
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueueJNIBridge_cppDelete(JNIEnv*,
                                                       jobject,
                                                       jlong ref)
    {
        auto commandQueue = reinterpret_cast<CommandQueueWithThread*>(ref);
        // Blocks the calling thread until the command server thread shuts down
        commandQueue->shutdownAndJoin();
        // Second unref, matches the one from cppConstructor
        commandQueue->unref();
    }

    JNIEXPORT jobject JNICALL
    Java_app_rive_core_CommandQueueJNIBridge_cppCreateListeners(
        JNIEnv* env,
        jobject,
        jlong ref,
        jobject jReceiver)
    {
        auto listenersClass = FindClass(env, "app/rive/core/Listeners");
        auto listenersConstructor =
            env->GetMethodID(listenersClass.get(), "<init>", "(JJJJJJJ)V");

        auto commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);

        auto fileListener = new FileListener(env, jReceiver);
        auto artboardListener = new ArtboardListener(env, jReceiver);
        auto stateMachineListener = new StateMachineListener(env, jReceiver);
        auto viewModelInstanceListener =
            new ViewModelInstanceListener(env, jReceiver);
        auto imageListener = new ImageListener(env, jReceiver);
        auto audioListener = new AudioListener(env, jReceiver);
        auto fontListener = new FontListener(env, jReceiver);

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

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueueJNIBridge_cppLoadFile(JNIEnv* env,
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
    Java_app_rive_core_CommandQueueJNIBridge_cppDeleteFile(JNIEnv*,
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
    Java_app_rive_core_CommandQueue_cppCreateRiveRenderTarget(JNIEnv*,
                                                              jobject,
                                                              jlong ref,
                                                              jint width,
                                                              jint height)
    {
        auto* commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);

        // Use a promise/future to make this synchronous
        auto promise = std::make_shared<std::promise<jlong>>();
        std::future<jlong> future = promise->get_future();

        // Use runOnce to execute on the command server thread where GL context
        // is active
        commandQueue->runOnce([width, height, promise](
                                  rive::CommandServer* server) {
            // Query sample count from the current GL context
            GLint actualSampleCount = 1;
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glGetIntegerv(GL_SAMPLES, &actualSampleCount);
            RiveLogD(TAG_CQ,
                     "Creating render target on command server "
                     "(sample count: %d)",
                     actualSampleCount);

            auto renderTarget =
                new rive::gpu::FramebufferRenderTargetGL(width,
                                                         height,
                                                         0, // Framebuffer ID
                                                         actualSampleCount);
            promise->set_value(reinterpret_cast<jlong>(renderTarget));
        });

        // Wait for the result. Blocks the main thread until complete.
        return future.get();
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
                                            jlong renderContextRef,
                                            jlong surfaceRef,
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
        auto* commandQueue = reinterpret_cast<CommandQueueWithThread*>(ref);
        auto* renderContext =
            reinterpret_cast<RenderContext*>(renderContextRef);
        auto* nativeSurface = reinterpret_cast<void*>(surfaceRef);
        auto* renderTarget =
            reinterpret_cast<rive::gpu::RenderTargetGL*>(renderTargetRef);
        auto fit = GetFit(env, jFit);
        auto alignment = GetAlignment(env, jAlignment);
        auto clearColor = static_cast<uint32_t>(jClearColor);

        auto drawWork = [commandQueue,
                         renderContext,
                         nativeSurface,
                         artboardHandleRef,
                         stateMachineHandleRef,
                         renderTarget,
                         width,
                         height,
                         fit,
                         alignment,
                         clearColor](rive::DrawKey drawKey,
                                     rive::CommandServer* server) {
            auto artboard = server->getArtboardInstance(
                handleFromLong<rive::ArtboardHandle>(artboardHandleRef));
            if (artboard == nullptr)
            {
                if (commandQueue->shouldLogArtboardNull(drawKey))
                {
                    RiveLogE(
                        TAG_CQ,
                        "Draw failed: Artboard instance is null (only reported once)");
                }
                return;
            }

            auto stateMachine = server->getStateMachineInstance(
                handleFromLong<rive::StateMachineHandle>(
                    stateMachineHandleRef));
            if (stateMachine == nullptr)
            {
                if (commandQueue->shouldLogStateMachineNull(drawKey))
                {
                    RiveLogE(
                        TAG_CQ,
                        "Draw failed: State machine instance is null (only reported once)");
                }
                return;
            }

            // Render backend specific - make the context current
            renderContext->beginFrame(nativeSurface);

            // Retrieve the Rive RenderContext from the CommandServer
            auto riveContext =
                static_cast<rive::gpu::RenderContext*>(server->factory());

            riveContext->beginFrame(rive::gpu::RenderContext::FrameDescriptor{
                .renderTargetWidth = static_cast<uint32_t>(width),
                .renderTargetHeight = static_cast<uint32_t>(height),
                .loadAction = rive::gpu::LoadAction::clear,
                .clearColor = clearColor,
            });

            // Stack allocate a Rive Renderer
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

            // Render context specific - swap buffers
            renderContext->present(nativeSurface);
        };
        commandQueue->draw(handleFromLong<rive::DrawKey>(drawKey), drawWork);
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppDrawToBuffer(JNIEnv* env,
                                                    jobject,
                                                    jlong ref,
                                                    jlong renderContextRef,
                                                    jlong surfaceRef,
                                                    jlong drawKey,
                                                    jlong artboardHandleRef,
                                                    jlong stateMachineHandleRef,
                                                    jlong renderTargetRef,
                                                    jint width,
                                                    jint height,
                                                    jobject jFit,
                                                    jobject jAlignment,
                                                    jint jClearColor,
                                                    jbyteArray jBuffer)
    {
        auto* commandQueue = reinterpret_cast<CommandQueueWithThread*>(ref);
        auto* renderContext =
            reinterpret_cast<RenderContext*>(renderContextRef);
        auto* nativeSurface = reinterpret_cast<void*>(surfaceRef);
        auto* renderTarget =
            reinterpret_cast<rive::gpu::RenderTargetGL*>(renderTargetRef);
        auto fit = GetFit(env, jFit);
        auto alignment = GetAlignment(env, jAlignment);
        auto clearColor = static_cast<uint32_t>(jClearColor);
        auto widthInt = static_cast<int>(width);
        auto heightInt = static_cast<int>(height);
        auto* pixels = reinterpret_cast<uint8_t*>(
            env->GetByteArrayElements(jBuffer, nullptr));
        auto jExceptionClass =
            FindClass(env, "app/rive/RiveDrawToBufferException");
        if (pixels == nullptr)
        {
            RiveLogE(TAG_CQ,
                     "Failed to access pixel buffer for drawIntoBuffer");
            env->ThrowNew(jExceptionClass.get(),
                          "Failed to access pixel buffer for drawing");
            return;
        }

        // Success case plus any potential errors producing the drawn buffer
        enum class DrawResult
        {
            Success,
            ArtboardNull,
            StateMachineNull,
        };

        // Be sure all pathways signal completion with `set_value` before
        // returning to avoid deadlock.
        auto completionPromise = std::make_shared<std::promise<DrawResult>>();
        auto drawWork = [commandQueue,
                         renderContext,
                         nativeSurface,
                         artboardHandleRef,
                         stateMachineHandleRef,
                         renderTarget,
                         widthInt,
                         heightInt,
                         fit,
                         alignment,
                         clearColor,
                         pixels,
                         completionPromise](rive::DrawKey drawKey,
                                            rive::CommandServer* server) {
            auto artboard = server->getArtboardInstance(
                handleFromLong<rive::ArtboardHandle>(artboardHandleRef));
            if (artboard == nullptr)
            {
                if (commandQueue->shouldLogArtboardNull(drawKey))
                {
                    RiveLogE(
                        TAG_CQ,
                        "Draw failed: Artboard instance is null (only reported once)");
                }
                completionPromise->set_value(DrawResult::ArtboardNull);
                return;
            }

            auto stateMachine = server->getStateMachineInstance(
                handleFromLong<rive::StateMachineHandle>(
                    stateMachineHandleRef));
            if (stateMachine == nullptr)
            {
                if (commandQueue->shouldLogStateMachineNull(drawKey))
                {
                    RiveLogE(
                        TAG_CQ,
                        "Draw failed: State machine instance is null (only reported once)");
                }
                completionPromise->set_value(DrawResult::StateMachineNull);
                return;
            }

            renderContext->beginFrame(nativeSurface);

            auto riveContext =
                static_cast<rive::gpu::RenderContext*>(server->factory());

            riveContext->beginFrame(rive::gpu::RenderContext::FrameDescriptor{
                .renderTargetWidth = static_cast<uint32_t>(widthInt),
                .renderTargetHeight = static_cast<uint32_t>(heightInt),
                .loadAction = rive::gpu::LoadAction::clear,
                .clearColor = clearColor,
            });

            auto renderer = rive::RiveRenderer(riveContext);

            renderer.align(fit,
                           alignment,
                           rive::AABB(0.0f,
                                      0.0f,
                                      static_cast<float_t>(widthInt),
                                      static_cast<float_t>(heightInt)),
                           artboard->bounds());
            artboard->draw(&renderer);

            riveContext->flush({
                .renderTarget = renderTarget,
            });

            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glFinish();
            glPixelStorei(GL_PACK_ALIGNMENT, 1);
            glReadPixels(0,
                         0,
                         widthInt,
                         heightInt,
                         GL_RGBA,
                         GL_UNSIGNED_BYTE,
                         pixels);

            auto rowBytes = static_cast<size_t>(widthInt) * 4;
            std::vector<uint8_t> row(rowBytes);
            auto* data = pixels;
            for (int y = 0; y < heightInt / 2; ++y)
            {
                auto* top = data + (static_cast<size_t>(y) * rowBytes);
                auto* bottom =
                    data + (static_cast<size_t>(heightInt - 1 - y) * rowBytes);
                std::memcpy(row.data(), top, rowBytes);
                std::memcpy(top, bottom, rowBytes);
                std::memcpy(bottom, row.data(), rowBytes);
            }

            renderContext->present(nativeSurface);
            completionPromise->set_value(DrawResult::Success);
        };

        commandQueue->draw(handleFromLong<rive::DrawKey>(drawKey), drawWork);
        auto result = completionPromise->get_future().get();
        env->ReleaseByteArrayElements(jBuffer,
                                      reinterpret_cast<jbyte*>(pixels),
                                      0);

        switch (result)
        {
            case DrawResult::Success:
                break;
            case DrawResult::ArtboardNull:
                env->ThrowNew(
                    jExceptionClass.get(),
                    "Failed to draw into buffer: Artboard instance is null");
                break;
            case DrawResult::StateMachineNull:
                env->ThrowNew(
                    jExceptionClass.get(),
                    "Failed to draw into buffer: State machine instance is null");
                break;
        }
    }

    JNIEXPORT void JNICALL
    Java_app_rive_core_CommandQueue_cppRunOnCommandServer(JNIEnv* env,
                                                          jobject,
                                                          jlong ref,
                                                          jobject jWork)
    {
        auto* commandQueue = reinterpret_cast<rive::CommandQueue*>(ref);

        // Create a global reference to the Kotlin lambda so that it
        // survives until the command server thread invokes it
        jobject jGlobalWork = env->NewGlobalRef(jWork);

        commandQueue->runOnce([jGlobalWork](rive::CommandServer*) {
            auto* env = GetJNIEnv();
            if (env == nullptr)
            {
                RiveLogE(TAG_CQ, "Failed to get command server JNIEnv");
                return;
            }

            // Get the Function0 class and invoke() method
            // Kotlin's () -> Unit compiles to Function0<Unit>, and invoke()
            // returns Object at the JVM level due to generic type erasure
            auto function0Class = GetObjectClass(env, jGlobalWork);
            auto invokeMethod = env->GetMethodID(function0Class.get(),
                                                 "invoke",
                                                 "()Ljava/lang/Object;");

            // Invoke the Kotlin lambda (ignoring the Unit return value)
            env->CallObjectMethod(jGlobalWork, invokeMethod);

            // Check for exceptions. We won't re-throw though, since this is
            // intended to be fire-and-forget, and we don't want to crash the
            // command server thread.
            JNIExceptionHandler::ClearAndLogErrors(
                env,
                TAG_CQ,
                "runOnCommandServer: Exception thrown in Kotlin lambda:");

            // Clean up the global reference
            env->DeleteGlobalRef(jGlobalWork);
        });
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

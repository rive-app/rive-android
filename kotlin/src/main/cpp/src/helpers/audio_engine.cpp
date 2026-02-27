#include "helpers/audio_engine.hpp"

#include "helpers/rive_log.hpp"
#ifdef WITH_RIVE_AUDIO
#include "helpers/general.hpp"
#include "rive/audio/audio_engine.hpp"
#endif

namespace rive_android
{

AudioEngine& AudioEngine::Instance()
{
    static AudioEngine instance;
    return instance;
}

const char* AUDIO_TAG = "RiveN/AudioEngine";

#ifdef WITH_RIVE_AUDIO
void AudioEngine::acquire()
{
    const auto previousCount = m_refCount.fetch_add(1);
    const auto newCount = previousCount + 1;

    // If this is the first reference, start the audio engine
    // Use mutex to ensure thread-safe start() call
    if (previousCount == 0)
    {
        std::lock_guard<std::mutex> lock(m_mutex);
        if (auto engine = rive::AudioEngine::RuntimeEngine(false))
        {
            engine->start();
            RiveLogI(AUDIO_TAG,
                     "AudioEngine: Started (new ref count: %d)",
                     newCount);
        }
    }
    else
    {
        RiveLogD(AUDIO_TAG,
                 "AudioEngine: Acquired (new ref count: %d)",
                 newCount);
    }
}

void AudioEngine::release()
{
    const auto previousCount = m_refCount.fetch_sub(1);
    const auto newCount = previousCount - 1;

    if (newCount < 0)
    {
        RiveLogE(AUDIO_TAG,
                 "AudioEngine: Release called when ref count was already 0");
        // Reset to 0 to prevent underflow
        m_refCount.store(0);
        return;
    }

    // If this was the last reference, stop the audio engine
    // Use mutex to ensure thread-safe stop() call
    if (newCount == 0)
    {
        std::lock_guard<std::mutex> lock(m_mutex);
        if (auto engine = rive::AudioEngine::RuntimeEngine(false))
        {
            engine->stop();
            RiveLogI(AUDIO_TAG, "AudioEngine: Stopped (new ref count: 0)");
        }
    }
    else
    {
        RiveLogD(AUDIO_TAG,
                 "AudioEngine: Released (new ref count: %d)",
                 newCount);
    }
}

#else  // WITH_RIVE_AUDIO
void AudioEngine::acquire()
{
    static bool hasLogged = false;
    if (!hasLogged)
    {
        hasLogged = true;
        RiveLogI(
            AUDIO_TAG,
            "This version of the Rive library does not include audio engine support.");
    }
    // No-op when audio is disabled
}

void AudioEngine::release()
{
    // No-op when audio is disabled
}
#endif // WITH_RIVE_AUDIO

} // namespace rive_android

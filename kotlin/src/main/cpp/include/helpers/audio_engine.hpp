#pragma once

#ifdef WITH_RIVE_AUDIO
#include <atomic>
#include <mutex>
#endif

namespace rive_android
{

/**
 * A thread-safe singleton wrapper around rive::AudioEngine that manages its
 * start/stop state through reference counting.
 *
 * See the AudioEngine Kotlin class for more details.
 *
 * The audio engine is started when the ref count becomes > 0 and stopped when
 * it reaches 0 again. Stakeholders can acquire/release references
 * independently.
 *
 * When WITH_RIVE_AUDIO is not defined, acquire() and release() are no-ops.
 */
class AudioEngine
{
public:
    /** Get the singleton instance. */
    static AudioEngine& Instance();

    /**
     * Acquire a reference to the audio engine. This increments the ref count.
     * If the ref count goes from 0 to 1, the audio engine is started.
     * Thread-safe.
     * No-op when WITH_RIVE_AUDIO is not defined.
     */
    void acquire();

    /**
     * Release a reference to the audio engine. This decrements the ref count.
     * If the ref count goes from 1 to 0, the audio engine is stopped.
     * Thread-safe.
     * No-op when WITH_RIVE_AUDIO is not defined.
     */
    void release();

    // Prevent copying
    AudioEngine(const AudioEngine&) = delete;
    AudioEngine& operator=(const AudioEngine&) = delete;

private:
    AudioEngine() = default;
    ~AudioEngine() = default;

#ifdef WITH_RIVE_AUDIO
    std::atomic<int> m_refCount{0};
    std::mutex m_mutex;
#endif
};

} // namespace rive_android

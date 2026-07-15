package app.rive.core

private const val AUDIO_TAG = "Rive/Audio"

/**
 * Provides control over the Rive audio engine (powered by [miniaudio][https://miniaud.io/]).
 *
 * The audio engine uses reference counting to manage its start/stop state. Multiple "stakeholders"
 * (e.g., CommandQueue instances and the legacy WorkerImpl) can acquire/release references
 * independently. The engine is started when the ref count becomes > 0 and stopped when it
 * reaches 0.
 *
 * This ultimately allows pausing audio (stopping the engine's playback) and resuming it (starting
 * it again) when the app is backgrounded. We ref count so that a stakeholder, e.g. a command queue,
 * can be destroyed without stopping the audio engine for other instances that may be playing.
 *
 * Audio playback and miniaudio may be excluded from the build via a Gradle property, in which case
 * these methods will no-op.
 */
object AudioEngine {
    /**
     * Acquire a reference to the audio engine, incrementing the ref count. If the ref count goes
     * from 0 to 1, the audio engine is started.
     */
    external fun acquire()

    /**
     * Release a reference to the audio engine, decrementing the ref count. If the ref count goes
     * from 1 to 0, the audio engine is stopped.
     */
    external fun release()
}

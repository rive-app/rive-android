package app.rive.core

import androidx.annotation.Keep

/**
 * Holds the native pointers to the listeners used by the CommandQueue. Only one listener of each
 * type is used to simplify lifetime management. This is as opposed to a listener for each handle.
 *
 * This class is constructed from C++.
 */
@Keep // Called from JNI
data class Listeners(
    val fileListener: Long,
    val artboardListener: Long,
    val stateMachineListener: Long,
    val viewModelInstanceListener: Long,
    val imageListener: Long,
    val audioListener: Long,
    val fontListener: Long,
) : NativeListeners {
    private external fun cppDelete(
        fileListener: Long,
        artboardListener: Long,
        stateMachineListener: Long,
        viewModelInstanceListener: Long,
        imageListener: Long,
        audioListener: Long,
        fontListener: Long,
    )

    /**
     * Dispose of the listeners and free their resources. This should be called when the
     * CommandQueue is disposed.
     */
    override fun close() = cppDelete(
        fileListener,
        artboardListener,
        stateMachineListener,
        viewModelInstanceListener,
        imageListener,
        audioListener,
        fontListener,
    )
}

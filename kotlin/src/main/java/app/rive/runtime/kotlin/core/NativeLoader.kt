package app.rive.runtime.kotlin.core

import android.content.Context
import android.os.Build
import com.getkeepsafe.relinker.ReLinker

internal object NativeLoader {

    /**
     * Loads specified library by name and covers edge cases.
     * ReLinker is only needed for API Level < 23.
     *
     * @param context required for ReLinker
     * @param name the name of the library to load
     * @return `true` if successfully loaded
     */
    fun loadLibrary(context: Context, name: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return loadLibrary(name)
        }

        val relinkerLoaded = runCatching {
            ReLinker.loadLibrary(context, name)
        }.isSuccess

        // Ignore ReLinker result first, since it catches some exception and may not be loaded
        // resulting in false-positives
        // Loading twice won't work anyway so there is no problem
        return loadLibrary(name) || relinkerLoaded
    }

    /**
     * Loads specified library by name and covers edge cases.
     * May fail on some API Level < 23 devices.
     *
     * @param name the name of the library to load
     * @return `true` if successfully loaded
     */
    private fun loadLibrary(name: String): Boolean {
        var loaded = true

        // normally it's just the name but some manufacturers mess with this
        systemLoadLibrary(name) {
            systemLoadLibrary("$name.so") {
                systemLoadLibrary("lib$name") {
                    systemLoadLibrary("lib$name.so") {
                        loaded = false
                    }
                }
            }
        }
        return loaded
    }

    private fun systemLoadLibrary(name: String, onError: () -> Unit = { }) {
        if (runCatching {
            System.loadLibrary(name)
        }.isFailure) {
            onError()
        }
    }
}
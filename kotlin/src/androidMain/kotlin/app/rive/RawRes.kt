package app.rive

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.annotation.RawRes as RawResource

/**
 * A [RiveFileSource] backed by an Android raw resource.
 *
 * @param resId The resource ID of the raw Rive file.
 * @param resources The [Resources] used to open the raw resource.
 */
data class RawRes(
    @param:RawResource val resId: Int,
    val resources: Resources
) : RiveFileSource {
    override suspend fun load(): ByteArray =
        // Use an I/O worker to load the raw resource bytes
        withContext(Dispatchers.IO) {
            resources.openRawResource(resId).use { it.readBytes() }
        }

    companion object {
        /**
         * Convenience function for Compose contexts to create a [RawRes] instance.
         *
         * Uses the current Compose [LocalContext] to obtain [Resources], avoiding the need to
         * pass it manually.
         *
         * @param resId The resource ID of the raw Rive file.
         * @return A [RawRes] instance with the given resource ID and the current [Resources].
         */
        @Composable
        fun from(@RawResource resId: Int): RawRes {
            val resources = LocalContext.current.resources
            // Remember keyed on resId only: recomposition can supply a fresh Resources
            // instance (Android Studio's interactive preview does, on every pass), and since
            // RawRes equality includes it, a new identity here makes rememberRiveFile reload
            // the file and restart the animation.
            return remember(resId) { RawRes(resId, resources) }
        }
    }
}

package app.rive.core

import androidx.compose.runtime.Composable

/**
 * This is a no-op implementation of RebuggerWrapper, avoiding the Rebugger dependency in release
 * builds.
 *
 * @suppress
 */
@Composable
fun RebuggerWrapper(
    @Suppress("UNUSED_PARAMETER")
    trackMap: Map<String, Any?>
) {
}

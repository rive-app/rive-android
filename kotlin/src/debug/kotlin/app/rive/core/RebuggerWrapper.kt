package app.rive.core

import androidx.compose.runtime.Composable
import com.theapache64.rebugger.Rebugger

/** A wrapper for Rebugger to enable debugging Compose recompositions in development builds. */
@Composable
fun RebuggerWrapper(
    trackMap: Map<String, Any?>
) {
    Rebugger(trackMap = trackMap)
}

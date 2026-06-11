package app.rive

import android.os.Build
import android.view.View

internal fun View.applyRequestedFrameRateHint(
    frameRate: RiveFrameRate,
    active: Boolean
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        return
    }

    requestedFrameRate = when {
        !active -> View.REQUESTED_FRAME_RATE_CATEGORY_NO_PREFERENCE
        frameRate is RiveFrameRate.Capped -> frameRate.framesPerSecond
        else -> View.REQUESTED_FRAME_RATE_CATEGORY_NO_PREFERENCE
    }
}

package app.rive.core

import app.rive.Fit

/** Internal defaults shared across rendering surfaces to keep behavior aligned. */
internal object RenderingDefaults {
    fun defaultFit(): Fit = Fit.Contain()

    const val CLEAR_COLOR: Int = 0x00000000
}

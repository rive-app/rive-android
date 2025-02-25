package app.rive.runtime.kotlin.core

import app.rive.runtime.kotlin.RiveInitializer

/**
 * Represents the result of the [Rive] initialization.
 * Only used for [RiveInitializer] to be compatible with other initializers.
 *
 * @property success Indicates whether the initialization was successful.
 */
data class RiveInitialized(
    val success: Boolean
)

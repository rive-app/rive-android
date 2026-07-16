package app.rive.core

import app.rive.RenderBackend
import app.rive.RiveInitializationException

internal actual fun createPlatformBridge(): CommandQueueBridge =
    throw RiveInitializationException("Rive is not yet supported on this platform")

internal actual fun createPlatformRenderContext(renderBackend: RenderBackend): RenderContext =
    throw RiveInitializationException("Rive is not yet supported on this platform")

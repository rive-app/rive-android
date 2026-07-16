package app.rive.core

import app.rive.RenderBackend
import app.rive.RiveInitializationException

// Real desktop rendering (offscreen Vulkan via a host dylib) is a follow-up phase; until it
// lands, worker creation on desktop fails gracefully.

internal actual fun createPlatformBridge(): CommandQueueBridge =
    throw RiveInitializationException("Rive desktop rendering is not available yet")

internal actual fun createPlatformRenderContext(renderBackend: RenderBackend): RenderContext =
    throw RiveInitializationException("Rive desktop rendering is not available yet")

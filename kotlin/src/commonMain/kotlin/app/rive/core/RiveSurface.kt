package app.rive.core

import app.rive.RiveLog

/**
 * A backend agnostic collection of surface properties needed for rendering.
 * - A draw key, which uniquely identifies draw operations in the CommandQueue
 * - The dimensions of the surface in pixels
 *
 * ⚠️ This class assumes ownership of all resources and should be [closed][RiveSurface.close] when
 * no longer needed. Closing schedules ordered disposal on the owning [CommandQueue].
 *
 * Alone it is not sufficient for rendering, as it lacks a backend-specific surface, which is
 * provided by subclasses.
 *
 * @param owningCommandQueue Command queue that owns this surface and performs ordered disposal. The
 *    surface acquires its own reference so the queue stays alive until surface disposal has run.
 * @param surfaceNativePointer Opaque native backend surface resource owned by this surface.
 * @param drawKey The key used to uniquely identify the draw operation in the CommandQueue.
 * @param width The width of the surface in pixels.
 * @param height The height of the surface in pixels.
 * @param resizable Whether this surface supports in-place size changes via [resize].
 */
abstract class RiveSurface internal constructor(
    owningCommandQueue: CommandQueue,
    surfaceNativePointer: Long,
    val drawKey: DrawKey,
    width: Int,
    height: Int,
    /**
     * Whether this surface supports in-place size changes.
     *
     * Window surfaces backed by resizable platform resources can resize in place. Fixed-size
     * surfaces, such as off-screen image surfaces, must be recreated at the desired size instead.
     */
    val resizable: Boolean
) : CheckableAutoCloseable {
    /** The width of the surface in pixels. */
    var width: Int = width
        private set

    /** The height of the surface in pixels. */
    var height: Int = height
        private set

    private val commandQueue: CommandQueue = owningCommandQueue.also {
        // Hold a reference to the command queue, ensuring we can schedule work on it for disposal.
        // The matching release is in the closer.
        it.acquire("RiveSurface")
    }

    private val closer = CloseOnce("RiveSurface") {
        commandQueue.cancelDraw(drawKey)
        commandQueue.runOnCommandServer {
            dispose()
        }
        // Matching release to acquire in the constructor. If this is the final reference, release
        // queues command server shutdown after the disposal work above.
        commandQueue.release("RiveSurface", "Surface closed")
    }

    /**
     * Schedules surface disposal on the owning command queue.
     *
     * This is safe to call from the main thread. After calling [close], callers must not use this
     * surface again. Pending coalesced draws for this surface are canceled before native disposal
     * is queued. Draws already admitted to execution may still complete.
     */
    override fun close() = closer.close()
    override val closed: Boolean
        get() = closer.closed

    /**
     * Disposes native surface resources.
     *
     * Subclasses should override this method to dispose of any additional resources, calling
     * `super.dispose()` at the end.
     *
     * Runs on the command server thread. See the note in [close].
     */
    protected open fun dispose() = surfaceNativePointer.close()

    /**
     * Resizes this surface without replacing the platform or backend window surface.
     *
     * Cancels pending coalesced draws before queueing backend render-target invalidation, so a
     * stale draw cannot run after the resize command but before the next draw phase. Render-target
     * invalidation is queued because concrete backend targets are created and used on the command
     * server thread; resizing them from the caller thread would race with draw execution and
     * backend context ownership.
     *
     * @param width New surface width in physical pixels.
     * @param height New surface height in physical pixels.
     * @throws IllegalArgumentException If [width] or [height] is not positive.
     * @throws IllegalStateException If this surface is already closed or does not support resizing.
     */
    internal fun resize(width: Int, height: Int) {
        require(width > 0 && height > 0) {
            "RiveSurface resize requires a positive width and height."
        }
        check(!closed) { "Cannot resize a closed RiveSurface" }
        check(resizable) { "Cannot resize a fixed-size RiveSurface" }
        if (this.width == width && this.height == height) {
            return
        }

        commandQueue.cancelDraw(drawKey)
        this.width = width
        this.height = height
        commandQueue.runOnCommandServer {
            if (!closed) {
                resizeNativeResources(width, height)
            }
        }
    }

    /**
     * Invalidates native resources that are sized from this surface.
     *
     * Runs on the command server thread so render-target disposal and backend-specific surface
     * invalidation remain ordered with draw work.
     */
    protected open fun resizeNativeResources(width: Int, height: Int) {
        commandQueue.resizeSurfaceNative(
            surfaceNativePointer.pointer,
            width,
            height
        )
    }

    /**
     * Opaque native backend surface resource.
     *
     * The concrete backend surface owns its render target and is disposed through command queue
     * ordering when [close] is called.
     */
    val surfaceNativePointer: UniquePointer = UniquePointer(
        surfaceNativePointer,
        "Rive/Surface"
    ) { pointer ->
        RiveLog.d("Rive/Surface") { "Deleting Rive native surface" }
        commandQueue.deleteSurfaceNative(pointer)
    }
}

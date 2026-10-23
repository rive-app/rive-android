package app.rive.core

import android.opengl.EGLSurface
import app.rive.RiveShutdownException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify

private const val NATIVE_SURFACE_POINTER = 30L
private const val DRAW_KEY_HANDLE = 20L

class RiveSurfaceGLTest : FunSpec({
    beforeTest {
        mockkObject(RiveSurface)
        every { RiveSurface.cppDeleteSurface(any()) } just Runs
    }

    afterTest { unmockkObject(RiveSurface) }

    test(
        "Close releases the Android surface and native surface when destroying the EGL surface fails"
    ) {
        val closeableSurface = mockk<CloseableSurface>(relaxed = true)
        val surface = RiveSurfaceGL(
            eglSurface = mockk<EGLSurface>(),
            renderContext = failingRenderContext(),
            closeableSurface = closeableSurface,
            commandQueue = synchronousCommandQueue(),
            nativeSurfacePointer = NATIVE_SURFACE_POINTER,
            drawKey = DrawKey(DRAW_KEY_HANDLE),
            width = 1,
            height = 1,
            resizable = false,
        )

        shouldThrow<RiveShutdownException> { surface.close() }

        verify { closeableSurface.close() }
        verify { RiveSurface.cppDeleteSurface(NATIVE_SURFACE_POINTER) }
    }

    test("Close releases the native surface when releasing the Android surface fails") {
        val closeableSurface = mockk<CloseableSurface> {
            every { close() } throws IllegalStateException("Android surface release failed")
        }
        val surface = RiveSurfaceGL(
            eglSurface = mockk<EGLSurface>(),
            renderContext = mockk<RenderContextGL>(relaxed = true),
            closeableSurface = closeableSurface,
            commandQueue = synchronousCommandQueue(),
            nativeSurfacePointer = NATIVE_SURFACE_POINTER,
            drawKey = DrawKey(DRAW_KEY_HANDLE),
            width = 1,
            height = 1,
            resizable = false,
        )

        shouldThrow<IllegalStateException> { surface.close() }

        verify { RiveSurface.cppDeleteSurface(NATIVE_SURFACE_POINTER) }
    }

    test("Close throws the EGL surface failure when releasing the Android surface also fails") {
        val closeableSurface = mockk<CloseableSurface> {
            every { close() } throws IllegalStateException("Android surface release failed")
        }
        val surface = RiveSurfaceGL(
            eglSurface = mockk<EGLSurface>(),
            renderContext = failingRenderContext(),
            closeableSurface = closeableSurface,
            commandQueue = synchronousCommandQueue(),
            nativeSurfacePointer = NATIVE_SURFACE_POINTER,
            drawKey = DrawKey(DRAW_KEY_HANDLE),
            width = 1,
            height = 1,
            resizable = false,
        )

        val exception = shouldThrow<RiveShutdownException> { surface.close() }

        exception.suppressed.map { it::class } shouldBe listOf(IllegalStateException::class)
    }

    test("Close releases the native surface when destroying the EGL PBuffer surface fails") {
        val surface = RiveSurfaceGLPBuffer(
            eglSurface = mockk<EGLSurface>(),
            renderContext = failingRenderContext(),
            commandQueue = synchronousCommandQueue(),
            nativeSurfacePointer = NATIVE_SURFACE_POINTER,
            drawKey = DrawKey(DRAW_KEY_HANDLE),
            width = 1,
            height = 1,
        )

        shouldThrow<RiveShutdownException> { surface.close() }

        verify { RiveSurface.cppDeleteSurface(NATIVE_SURFACE_POINTER) }
    }
})

/** @return A render context whose EGL surface destruction always fails. */
private fun failingRenderContext() = mockk<RenderContextGL> {
    every { destroySurface(any()) } throws RiveShutdownException("Unable to destroy EGL surface")
}

/** @return A command queue that runs command server work on the calling thread. */
private fun synchronousCommandQueue() = mockk<CommandQueue>(relaxed = true) {
    every { runOnCommandServer(any()) } answers { firstArg<() -> Unit>().invoke() }
}

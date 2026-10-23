package app.rive.core

import android.opengl.EGL14
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.RiveAndroidTest
import app.rive.RiveInitializationException
import app.rive.RiveShutdownException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RenderContextGLTest : RiveAndroidTest() {
    @Test
    fun close_afterEglDestroyContextFails_stillDeletesNativeObject() {
        val renderContext = try {
            RenderContextGL()
        } catch (unsupported: RiveInitializationException) {
            assumeTrue("OpenGL unavailable: ${unsupported.message}", false)
            return
        }
        // Destroy the EGL context up front, so destroying it again in close() fails.
        assertTrue(EGL14.eglDestroyContext(renderContext.display, renderContext.context))

        assertFailsWith<RiveShutdownException> { renderContext.close() }

        // The native object is deleted even though an earlier step failed.
        assertFailsWith<IllegalStateException> { renderContext.nativeObjectPointer }
    }
}

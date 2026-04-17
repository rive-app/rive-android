package app.rive.runtime.kotlin.core

import android.content.Context
import android.graphics.SurfaceTexture
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RiveTextureViewSurfaceTeardownTest {
    private val testUtils = TestUtils()
    private lateinit var appContext: Context

    @Before
    fun setup() {
        appContext = testUtils.context
    }

    @Test
    fun surfaceDestroyed_immediatelyDetachesRenderer() =
        UiThreadStatement.runOnUiThread {
            val view = TestUtils.MockRiveAnimationView(appContext, attachOnInit = false)
            view.mockAttach()
            val renderer = view.artboardRenderer!!
            val surfaceTexture = SurfaceTexture(1)

            view.onSurfaceTextureAvailable(surfaceTexture, 64, 64)
            assertTrue(renderer.isAttached)

            val listenerReleasedTexture = view.onSurfaceTextureDestroyed(surfaceTexture)
            assertFalse(listenerReleasedTexture)
            // We expect that the renderer should immediately be marked as detached when the
            // callback comes through.
            assertFalse(renderer.isAttached)

            surfaceTexture.release()
            view.mockDetach()
        }

    @Test
    fun rapidSurfaceDestroyAndRecreateRemainsStable() =
        UiThreadStatement.runOnUiThread {
            val view = TestUtils.MockRiveAnimationView(appContext, attachOnInit = false)
            view.mockAttach()
            val renderer = view.artboardRenderer!!

            repeat(10) { index ->
                val surfaceTexture = SurfaceTexture(index + 10)
                view.onSurfaceTextureAvailable(surfaceTexture, 64, 64)
                assertTrue(renderer.isAttached)

                val listenerReleasedTexture = view.onSurfaceTextureDestroyed(surfaceTexture)
                assertFalse(listenerReleasedTexture)
                assertFalse(renderer.isAttached)
                surfaceTexture.release()
            }

            view.mockDetach()
        }

    @Test
    fun surfaceDestroyedAndViewDetached_preservesControllerRefCounts() =
        UiThreadStatement.runOnUiThread {
            val view = TestUtils.MockRiveAnimationView(appContext, attachOnInit = false)
            view.mockAttach()
            val surfaceTexture = SurfaceTexture(50)

            view.onSurfaceTextureAvailable(surfaceTexture, 64, 64)
            // This should have no bearing on the controller ref count, which still maintains
            // 1 ref count.
            view.onSurfaceTextureDestroyed(surfaceTexture)
            view.mockDetach(destroy = false)

            assertEquals(1, view.controller.refCount)

            view.mockOnDestroy()
            assertEquals(0, view.controller.refCount)

            surfaceTexture.release()
        }
}

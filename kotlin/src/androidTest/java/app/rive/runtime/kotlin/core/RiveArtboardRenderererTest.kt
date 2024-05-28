package app.rive.runtime.kotlin.core

import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.renderers.RiveArtboardRenderer
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RiveArtboardRenderererTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context

    @Test
    fun createRenderer() {
        val textures = mutableListOf<SurfaceTexture>()
        val surfaces = mutableListOf<Surface>()
        repeat(10) {
            val surfaceTexture = SurfaceTexture(it)
            val surface = Surface(surfaceTexture)
            textures.add(surfaceTexture)
            surfaces.add(surface)
        }

        val controller = RiveFileController()
        RiveArtboardRenderer(controller = controller).apply {
            make()
            surfaces.forEach { setSurface(it) }
        }.let {
            it.stop()
            it.delete()
        }

        textures.forEach { it.release() }
        surfaces.forEach { it.release() }

        controller.release()
    }
}
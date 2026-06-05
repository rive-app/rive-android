package app.rive

import android.os.Build
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RenderBackendUnitTest : FunSpec({
    test("Vulkan is effective on API 29 and newer") {
        effectiveRenderBackend(RenderBackend.Vulkan, Build.VERSION_CODES.Q) shouldBe
                RenderBackend.Vulkan
    }

    test("Vulkan request falls back to OpenGL below API 29") {
        effectiveRenderBackend(RenderBackend.Vulkan, Build.VERSION_CODES.P) shouldBe
                RenderBackend.OpenGL
    }

    test("OpenGL request stays OpenGL on Vulkan-capable API levels") {
        effectiveRenderBackend(RenderBackend.OpenGL, Build.VERSION_CODES.Q) shouldBe
                RenderBackend.OpenGL
    }
})

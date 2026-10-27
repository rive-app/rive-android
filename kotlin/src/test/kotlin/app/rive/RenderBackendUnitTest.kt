package app.rive

import android.os.Build
import app.rive.core.hasKnownOpenGLDriverIssue
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

    test("Only reported FCNT model and firmware pairs prohibit OpenGL") {
        mapOf(
            "A402FC" to "V54RS51B",
            "FCG02" to "V54RK51B",
            "F-52E" to "V54RD51B",
            "M07" to "V54RM51B"
        ).forEach { (model, build) ->
            hasKnownOpenGLDriverIssue("FCNT", model, build, securityPatch = null) shouldBe true
            hasKnownOpenGLDriverIssue("fcnt", model, build, securityPatch = null) shouldBe true
            hasKnownOpenGLDriverIssue("Other", model, build, securityPatch = null) shouldBe false
            hasKnownOpenGLDriverIssue("FCNT", model, "unknown", securityPatch = null) shouldBe false
            hasKnownOpenGLDriverIssue("FCNT", "Other", build, securityPatch = null) shouldBe false
        }
        hasKnownOpenGLDriverIssue("FCNT", "FCG02", "V54RS51B", securityPatch = null) shouldBe false
        hasKnownOpenGLDriverIssue(null, null, null, securityPatch = null) shouldBe false
    }

    test("Affected models remain protected at and after the security patch threshold") {
        listOf("A402FC", "FCG02", "F-52E", "M07").forEach { model ->
            listOf("2026-08-01", "2026-08-05", "2026-11-01", "2028-02-29").forEach { patch ->
                hasKnownOpenGLDriverIssue("FCNT", model, "future-build", patch) shouldBe true
            }
            hasKnownOpenGLDriverIssue("FCNT", model, "future-build", "2026-07-31") shouldBe false
        }
    }

    test("Missing or invalid patch dates only protect exact known builds") {
        listOf(
            null, "", "unknown", "2026-8-01", "2026-08-1", "2026-13-01", "2026-08-32",
            "2027-02-29", "2026-08-01-extra", " 2026-08-01", "2026-07-31"
        ).forEach { patch ->
            hasKnownOpenGLDriverIssue("FCNT", "A402FC", "future-build", patch) shouldBe false
            hasKnownOpenGLDriverIssue("FCNT", "A402FC", "V54RS51B", patch) shouldBe true
        }
    }

    test("Recent patch dates do not broaden manufacturer or model scope") {
        hasKnownOpenGLDriverIssue("Other", "A402FC", "V54RS51B", "2026-08-01") shouldBe false
        hasKnownOpenGLDriverIssue("FCNT", "Other", "V54RS51B", "2026-08-01") shouldBe false
        hasKnownOpenGLDriverIssue(null, null, null, "2026-08-01") shouldBe false
    }
})

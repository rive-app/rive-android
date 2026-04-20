package app.rive

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RiveInitialDrawPolicyUnitTest : FunSpec({
    test("a newly available surface needs one draw before the lifecycle animation loop starts") {
        RiveInitialDrawPolicy.shouldDrawBeforeLifecycleLoop(
            surfaceAvailable = true,
            firstDrawRequestedForSurface = false,
        ) shouldBe true
    }

    test("a missing surface cannot draw before the lifecycle animation loop starts") {
        RiveInitialDrawPolicy.shouldDrawBeforeLifecycleLoop(
            surfaceAvailable = false,
            firstDrawRequestedForSurface = false,
        ) shouldBe false
    }

    test("an already-drawn surface does not need another lifecycle-independent draw") {
        RiveInitialDrawPolicy.shouldDrawBeforeLifecycleLoop(
            surfaceAvailable = true,
            firstDrawRequestedForSurface = true,
        ) shouldBe false
    }
})

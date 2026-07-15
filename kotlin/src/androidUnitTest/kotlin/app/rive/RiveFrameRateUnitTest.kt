package app.rive

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.nanoseconds

class RiveFrameRateUnitTest : FunSpec({
    test("Capped rejects invalid frame rates") {
        shouldThrow<IllegalArgumentException> {
            RiveFrameRate.Capped(0f)
        }
        shouldThrow<IllegalArgumentException> {
            RiveFrameRate.Capped(-1f)
        }
        shouldThrow<IllegalArgumentException> {
            RiveFrameRate.Capped(Float.NaN)
        }
        shouldThrow<IllegalArgumentException> {
            RiveFrameRate.Capped(Float.POSITIVE_INFINITY)
        }
    }

    test("Unbounded pacer does not delay or skip frames") {
        val pacer = RiveFramePacer(RiveFrameRate.Unbounded)

        pacer.delayBeforeNextFrame(0L) shouldBe 0.nanoseconds
        pacer.tryScheduleFrame(0L) shouldBe true

        pacer.delayBeforeNextFrame(1L) shouldBe 0.nanoseconds
        pacer.tryScheduleFrame(1L) shouldBe true
    }

    test("Capped pacer renders the first frame immediately") {
        val frameRate = RiveFrameRate.Capped(30f)
        val pacer = RiveFramePacer(frameRate, earlyWake = 0.nanoseconds)

        pacer.delayBeforeNextFrame(0L) shouldBe 0.nanoseconds
        pacer.tryScheduleFrame(0L) shouldBe true

        pacer.delayBeforeNextFrame(0L) shouldBe frameRate.period
        pacer.tryScheduleFrame(frameRate.period.inWholeNanoseconds - 1L) shouldBe false
        pacer.tryScheduleFrame(frameRate.period.inWholeNanoseconds) shouldBe true
    }

    test("Capped pacer subtracts early wake time from coarse delay") {
        val frameRate = RiveFrameRate.Capped(30f)
        val pacer = RiveFramePacer(frameRate, earlyWake = 1_000_000.nanoseconds)

        pacer.tryScheduleFrame(0L) shouldBe true

        pacer.delayBeforeNextFrame(0L) shouldBe frameRate.period - 1_000_000.nanoseconds
    }

    test("Capped pacer preserves schedule when a frame callback is too early") {
        val frameRate = RiveFrameRate.Capped(24f)
        val pacer = RiveFramePacer(frameRate, earlyWake = 0.nanoseconds)

        pacer.tryScheduleFrame(0L) shouldBe true

        pacer.tryScheduleFrame(frameRate.period.inWholeNanoseconds - 1L) shouldBe false
        pacer.delayBeforeNextFrame(frameRate.period.inWholeNanoseconds - 1L) shouldBe 1.nanoseconds
        pacer.tryScheduleFrame(frameRate.period.inWholeNanoseconds) shouldBe true
    }

    test("Capped pacer advances the target past missed periods") {
        val frameRate = RiveFrameRate.Capped(30f)
        val pacer = RiveFramePacer(frameRate, earlyWake = 0.nanoseconds)
        val lateFrameTimeNs = frameRate.period.inWholeNanoseconds * 100_000L + 5L

        pacer.tryScheduleFrame(0L) shouldBe true
        pacer.tryScheduleFrame(lateFrameTimeNs) shouldBe true

        pacer.delayBeforeNextFrame(lateFrameTimeNs) shouldBe frameRate.period - 5.nanoseconds
    }

    test("Reset makes the next capped frame render immediately") {
        val frameRate = RiveFrameRate.Capped(30f)
        val pacer = RiveFramePacer(frameRate, earlyWake = 0.nanoseconds)

        pacer.tryScheduleFrame(0L) shouldBe true
        pacer.tryScheduleFrame(1L) shouldBe false

        pacer.reset()

        pacer.delayBeforeNextFrame(1L) shouldBe 0.nanoseconds
        pacer.tryScheduleFrame(1L) shouldBe true
    }
})

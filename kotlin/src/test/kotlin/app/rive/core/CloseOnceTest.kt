package app.rive.core

import app.rive.RiveResourceClosedException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class CloseOnceTest : FunSpec({
    test("checkOpen returns while resource is open") {
        val closeOnce = CloseOnce("TestHandle(123)") {}

        closeOnce.checkOpen()
    }

    test("checkOpen throws precise exception after close") {
        val closeOnce = CloseOnce("TestHandle(123)") {}
        closeOnce.close()

        val exception = shouldThrow<RiveResourceClosedException> {
            closeOnce.checkOpen()
        }

        exception.message shouldContain "123"
    }

    test("close remains idempotent") {
        var closeCount = 0
        val closeOnce = CloseOnce("TestHandle(123)") {
            closeCount++
        }

        closeOnce.close()
        closeOnce.close()

        closeCount shouldBe 1
    }
})

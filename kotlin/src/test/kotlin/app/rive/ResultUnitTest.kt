@file:Suppress("DEPRECATION")

package app.rive

import app.rive.Result.Loading.sequence as legacySequence
import app.rive.Result.Loading.zip as legacyZip
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Tests result operations and retained dispatch-receiver compatibility shims. */
class ResultUnitTest : FunSpec({
    test("Map transforms success and forwards loading and error") {
        val error = Result.Error(IllegalStateException("Failed"))
        val loading: Result<Int> = Result.Loading

        Result.Success(2).map { it * 3 } shouldBe Result.Success(6)
        loading.map { it * 3 } shouldBe Result.Loading
        error.map { "unused" } shouldBe error
    }

    test("Zip combines successes and forwards the first incomplete result") {
        val firstError = Result.Error(IllegalStateException("First"))
        val secondError = Result.Error(IllegalStateException("Second"))
        val loading: Result<Int> = Result.Loading

        Result.Success(2).zip(Result.Success(3)) { first, second ->
            first + second
        } shouldBe Result.Success(5)
        loading.zip(secondError) shouldBe Result.Loading
        firstError.zip(secondError) shouldBe firstError
    }

    test("Sequence joins successes and forwards the first incomplete result") {
        val error = Result.Error(IllegalStateException("Failed"))

        listOf(Result.Success(1), Result.Success(2)).sequence() shouldBe
            Result.Success(listOf(1, 2))
        listOf(Result.Success(1), Result.Loading, error).sequence() shouldBe Result.Loading
        listOf(Result.Success(1), error, Result.Loading).sequence() shouldBe error
    }

    test("Legacy member extensions remain source compatible") {
        val successes = listOf<Result<Int>>(Result.Success(1), Result.Success(2))

        Result.Success(1).legacyZip(Result.Success(2)) shouldBe Result.Success(1 to 2)
        successes.legacySequence() shouldBe Result.Success(listOf(1, 2))
    }
})

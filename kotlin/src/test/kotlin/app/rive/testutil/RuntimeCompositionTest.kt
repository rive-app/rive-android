package app.rive.testutil

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest

/** Verifies scheduling and misuse detection for effect-only JVM compositions. */
class RuntimeCompositionTest : FunSpec({
    installTraceMock()

    test("Drains cascading effects until idle and disposes content") {
        runTest {
            var generation by mutableStateOf(0)
            var observed = -1
            var disposed = false
            RuntimeComposition(this).use { composition ->
                composition.setContent {
                    observed = generation
                    LaunchedEffect(generation) { if (generation < 10) generation++ }
                    DisposableEffect(Unit) { onDispose { disposed = true } }
                }
                composition.runUntilIdle()
                observed shouldBe 10
                disposed shouldBe false
            }
            disposed shouldBe true
        }
    }

    test("Idle does not wait for external responses") {
        runTest {
            val response = CompletableDeferred<Int>()
            var value by mutableStateOf(0)
            var observed = -1
            RuntimeComposition(this).use { composition ->
                composition.setContent {
                    observed = value
                    LaunchedEffect(Unit) { value = response.await() }
                }
                composition.runUntilIdle()
                observed shouldBe 0
                response.complete(42)
                composition.runUntilIdle()
                observed shouldBe 42
            }
        }
    }

    test("Continuous frame requests fail within the pass limit") {
        runTest {
            RuntimeComposition(this).use { composition ->
                composition.setContent {
                    LaunchedEffect(Unit) { while (true) withFrameNanos { } }
                }
                shouldThrow<IllegalStateException> { composition.runUntilIdle(maxPasses = 10) }
                    .message shouldBe "Composition did not become idle after 10 passes"
            }
        }
    }

    test("The effect-only applier rejects node operations") {
        val applier = EffectOnlyApplier()
        shouldThrow<IllegalStateException> { applier.insertTopDown(0, Unit) }
        shouldThrow<IllegalStateException> { applier.insertBottomUp(0, Unit) }
        shouldThrow<IllegalStateException> { applier.remove(0, 1) }
        shouldThrow<IllegalStateException> { applier.move(0, 1, 1) }
        applier.clear()
    }
})

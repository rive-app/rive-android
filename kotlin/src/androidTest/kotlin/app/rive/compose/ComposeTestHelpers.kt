package app.rive.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import app.rive.Artboard
import app.rive.Result
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.StateMachine
import app.rive.core.RiveWorker
import app.rive.rememberArtboardResult
import app.rive.rememberRiveFile
import app.rive.rememberStateMachineResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicReference

private const val DEFAULT_COMPOSE_TIMEOUT_MILLIS = 10_000L

/**
 * Fully resolved resources used by Compose instrumentation fixtures.
 *
 * This is intentionally test-only. The public resource-input shape will be designed separately
 * for the 12.0 `Rive` composable API.
 *
 * @param file The loaded file.
 * @param artboard The artboard created from [file].
 * @param stateMachine The state machine created from [artboard].
 */
internal data class TestRiveResources(
    val file: RiveFile,
    val artboard: Artboard,
    val stateMachine: StateMachine,
)

/**
 * Sequentially remembers a file and its selected artboard and state machine for tests.
 *
 * @param source The source of the Rive file.
 * @param riveWorker The worker that owns the resources.
 * @param artboardName The artboard to create, or null for the default.
 * @param stateMachineName The state machine to create, or null for the default.
 * @return The current creation result for the fully resolved test resources.
 */
@Composable
internal fun rememberTestRiveResources(
    source: RiveFileSource,
    riveWorker: RiveWorker,
    artboardName: String? = null,
    stateMachineName: String? = null,
): Result<TestRiveResources> = rememberRiveFile(source, riveWorker).andThen { file ->
    rememberArtboardResult(file, artboardName).andThen { artboard ->
        rememberStateMachineResult(artboard, stateMachineName).map { stateMachine ->
            TestRiveResources(file, artboard, stateMachine)
        }
    }
}

/**
 * Distinguishes a property that emitted `null` from one that has not emitted for timeout
 * diagnostics.
 *
 * @param value The latest value emitted by the property.
 */
private data class ObservedProperty<T>(val value: T)

/**
 * Waits for [condition] while allowing Compose, Android, and background work to progress.
 *
 * This delegates to [ComposeContentTestRule.waitUntil], which observes wall-clock time and yields
 * between checks. Use [awaitWithComposeClock] instead when progress should be driven only by
 * simulated Compose time.
 *
 * @param timeoutMessage Produces the assertion message if the condition times out.
 * @param timeoutMillis The wall-clock timeout for satisfying [condition].
 * @param condition Returns whether the expected test state has been reached.
 * @throws AssertionError If [condition] is not satisfied before the timeout.
 */
internal fun ComposeContentTestRule.awaitWithWallClock(
    timeoutMessage: () -> String,
    timeoutMillis: Long = DEFAULT_COMPOSE_TIMEOUT_MILLIS,
    condition: () -> Boolean,
) = awaitConditionWithDiagnostics(
    waitStrategy = this::waitUntil,
    timeoutMessage = timeoutMessage,
    timeoutMillis = timeoutMillis,
    condition = condition,
)

/**
 * Advances simulated Compose time until [condition] is satisfied.
 *
 * This delegates to [MainTestClock.advanceTimeUntil]. It does not perform Android measure, layout,
 * or draw between checks, so callers must synchronize any required platform work first. Use
 * [awaitWithWallClock] when the condition requires platform or background work.
 *
 * @param timeoutMessage Produces the assertion message if the condition times out.
 * @param timeoutMillis The simulated-time timeout for satisfying [condition].
 * @param condition Returns whether the expected test state has been reached.
 * @throws AssertionError If [condition] is not satisfied before the timeout.
 */
internal fun ComposeContentTestRule.awaitWithComposeClock(
    timeoutMessage: () -> String,
    timeoutMillis: Long = DEFAULT_COMPOSE_TIMEOUT_MILLIS,
    condition: () -> Boolean,
) = awaitConditionWithDiagnostics(
    waitStrategy = mainClock::advanceTimeUntil,
    timeoutMessage = timeoutMessage,
    timeoutMillis = timeoutMillis,
    condition = condition,
)

/**
 * Runs [waitStrategy] and translates its Compose timeout into a diagnostic assertion.
 *
 * @param waitStrategy Waits for a condition using its own time domain.
 * @param timeoutMessage Produces the assertion message if [waitStrategy] times out.
 * @param timeoutMillis The timeout supplied to [waitStrategy].
 * @param condition Returns whether the expected test state has been reached.
 * @throws AssertionError If [condition] is not satisfied before the timeout.
 */
private fun awaitConditionWithDiagnostics(
    waitStrategy: (Long, () -> Boolean) -> Unit,
    timeoutMessage: () -> String,
    timeoutMillis: Long,
    condition: () -> Boolean,
) {
    try {
        waitStrategy(timeoutMillis, condition)
    } catch (timeout: ComposeTimeoutException) {
        throw AssertionError(timeoutMessage(), timeout)
    }
}

/**
 * Waits for a data binding property to equal [expected].
 *
 * The property path is passed to [getFlow], keeping the path available for diagnostics without
 * requiring the caller to repeat it when creating the property flow.
 *
 * @param propertyPath The path to the data binding property.
 * @param getFlow Creates the typed flow for [propertyPath], e.g.
 *    `viewModelInstance::getBooleanFlow`.
 * @param expected The property value to await.
 * @param timeoutMillis The wall-clock timeout for observing [expected].
 * @return The observed property value equal to [expected].
 * @throws AssertionError If the property flow fails or does not emit [expected] before the timeout.
 */
internal fun <T> ComposeContentTestRule.awaitProperty(
    propertyPath: String,
    getFlow: (String) -> Flow<T>,
    expected: T,
    timeoutMillis: Long = DEFAULT_COMPOSE_TIMEOUT_MILLIS,
): T = awaitProperty(
    propertyPath = propertyPath,
    getFlow = getFlow,
    expectation = "equal $expected",
    timeoutMillis = timeoutMillis,
) { it == expected }

/**
 * Waits for a data binding property to satisfy [predicate].
 *
 * Collection runs independently because [ComposeContentTestRule.waitUntil] accepts a synchronous
 * condition. After each unsuccessful condition check, `waitUntil` advances the Compose test clock
 * and gives Android time to perform asynchronous work. Collecting the flow directly in that
 * condition would suspend it before `waitUntil` could advance the next frame.
 *
 * @param propertyPath The path to the data binding property.
 * @param getFlow Creates the typed flow for [propertyPath], e.g.
 *    `viewModelInstance::getNumberFlow`.
 * @param expectation A diagnostic description of the expected property state, e.g. `"be greater
 *    than 0"`.
 * @param timeoutMillis The wall-clock timeout for satisfying [predicate].
 * @param predicate Returns whether an observed property value satisfies the expected state.
 * @return The first property value that satisfies [predicate].
 * @throws AssertionError If the property flow fails or does not satisfy [predicate] before the
 *    timeout.
 */
internal fun <T> ComposeContentTestRule.awaitProperty(
    propertyPath: String,
    getFlow: (String) -> Flow<T>,
    expectation: String,
    timeoutMillis: Long = DEFAULT_COMPOSE_TIMEOUT_MILLIS,
    predicate: (T) -> Boolean,
): T = runBlocking {
    // Collection runs on Dispatchers.Default while timeout reporting runs on the instrumentation
    // thread, so publish only the latest diagnostic value through an atomic slot.
    val latest = AtomicReference<ObservedProperty<T>?>(null)
    val matchingValue = async(Dispatchers.Default) {
        try {
            kotlin.Result.success(
                getFlow(propertyPath).first { value ->
                    latest.set(ObservedProperty(value))
                    predicate(value)
                }
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            // Report collection failures on the instrumentation thread after waitUntil completes.
            kotlin.Result.failure(failure)
        }
    }

    // Keep the collector alive while waitUntil polls it, and cancel it if waiting exits early.
    try {
        // Poll completion rather than awaiting here: after each false result, waitUntil can
        // advance Compose and yield to Android while the flow continues collecting. Keeping the
        // timeout inside awaitWithWallClock also prevents a similarly typed flow or predicate
        // failure from being misreported as a wait timeout.
        this@awaitProperty.awaitWithWallClock(
            timeoutMillis = timeoutMillis,
            timeoutMessage = {
                val latestDescription = latest.get()?.value?.toString() ?: "<no value>"
                "Property '$propertyPath' did not $expectation within ${timeoutMillis}ms; " +
                    "latest value was $latestDescription"
            },
        ) {
            matchingValue.isCompleted
        }

        // waitUntil establishes that collection completed, so await returns immediately. Unwrap
        // its result separately to preserve whether failure came from waiting or collection.
        matchingValue.await().getOrElse { failure ->
            throw AssertionError(
                "Failed while awaiting property '$propertyPath' to $expectation",
                failure
            )
        }
    } finally {
        matchingValue.cancelAndJoin()
    }
}

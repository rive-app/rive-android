package app.rive

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.test.R
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

private const val FILE_B_VIEW_MODEL = "FileBMain"
private const val DUMMY_PROPERTY = "dummy"
private const val LABEL_PROPERTY = "label"
private const val INITIAL_LABEL = "File A Initial"
private const val ABSOLUTE_ARTBOARD = "Absolute"
private const val RELATIVE_ARTBOARD = "Relative"
private const val RELATIVE_TEXT = "File A Relative"
private const val PROPERTY_TIMEOUT_MILLIS = 2_000L
private const val OBSERVATION_ARTBOARD = "Test Observation"
private const val NUMBER_PROPERTY = "Test Num"
private const val STRING_PROPERTY = "Test String"
private const val BOOLEAN_PROPERTY = "Test Bool"
private const val ENUM_PROPERTY = "Test Enum"
private const val COLOR_PROPERTY = "Test Color"
private const val TRIGGER_PROPERTY = "Test Trigger"
private const val NESTED_NUMBER_PROPERTY = "Test Nested/Nested Number"

@RunWith(AndroidJUnit4::class)
class DataBindingTest : RiveAndroidTest() {
    /** Verifies that every primitive mutation round-trips through the Android command queue. */
    @Test
    fun propertyMutations_roundTripPrimitiveValues() = runBlocking {
        RiveFile.load(
            RiveFileSource.RawRes(R.raw.data_bind_test_impl, context.resources),
            riveWorker,
        ).use { file ->
            ViewModelInstance.create(
                file,
                ViewModelSource.Named("Test All").namedInstance("Test Default"),
            ).use { viewModelInstance ->
                val trigger = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeout(PROPERTY_TIMEOUT_MILLIS) {
                        viewModelInstance.getTriggerFlow(TRIGGER_PROPERTY).first()
                    }
                }

                viewModelInstance.setNumber(NUMBER_PROPERTY, 42.5f)
                viewModelInstance.setString(STRING_PROPERTY, "Android")
                viewModelInstance.setBoolean(BOOLEAN_PROPERTY, false)
                viewModelInstance.setEnum(ENUM_PROPERTY, "Value 2")
                viewModelInstance.setColor(COLOR_PROPERTY, 0xFF123456.toInt())
                viewModelInstance.setNumber(NESTED_NUMBER_PROPERTY, 314f)
                viewModelInstance.fireTrigger(TRIGGER_PROPERTY)

                assertEquals(
                    42.5f,
                    withTimeout(PROPERTY_TIMEOUT_MILLIS) {
                        viewModelInstance.getNumberFlow(NUMBER_PROPERTY).first()
                    },
                )
                assertEquals(
                    "Android",
                    withTimeout(PROPERTY_TIMEOUT_MILLIS) {
                        viewModelInstance.getStringFlow(STRING_PROPERTY).first()
                    },
                )
                assertEquals(
                    false,
                    withTimeout(PROPERTY_TIMEOUT_MILLIS) {
                        viewModelInstance.getBooleanFlow(BOOLEAN_PROPERTY).first()
                    },
                )
                assertEquals(
                    "Value 2",
                    withTimeout(PROPERTY_TIMEOUT_MILLIS) {
                        viewModelInstance.getEnumFlow(ENUM_PROPERTY).first()
                    },
                )
                assertEquals(
                    0xFF123456.toInt(),
                    withTimeout(PROPERTY_TIMEOUT_MILLIS) {
                        viewModelInstance.getColorFlow(COLOR_PROPERTY).first()
                    },
                )
                assertEquals(
                    314f,
                    withTimeout(PROPERTY_TIMEOUT_MILLIS) {
                        viewModelInstance.getNumberFlow(NESTED_NUMBER_PROPERTY).first()
                    },
                )
                trigger.await()
            }
        }
    }

    /** Verifies that every primitive property flow receives target-to-source binding updates. */
    @Test
    fun propertyFlows_receiveTargetToSourceUpdates() = runBlocking {
        RiveFile.load(
            RiveFileSource.RawRes(R.raw.data_bind_test_impl, context.resources),
            riveWorker,
        ).use { file ->
            Artboard.create(file, OBSERVATION_ARTBOARD).use { artboard ->
                StateMachine.create(artboard).use { stateMachine ->
                    ViewModelInstance.create(
                        file,
                        ViewModelSource.Named("Test All").namedInstance("Test Default"),
                    ).use { viewModelInstance ->
                        val numbers = Channel<Float>(Channel.UNLIMITED)
                        val secondNumbers = Channel<Float>(Channel.UNLIMITED)
                        val strings = Channel<String>(Channel.UNLIMITED)
                        val booleans = Channel<Boolean>(Channel.UNLIMITED)
                        val enums = Channel<String>(Channel.UNLIMITED)
                        val colors = Channel<Int>(Channel.UNLIMITED)
                        val triggers = Channel<Unit>(Channel.UNLIMITED)
                        val nestedNumbers = Channel<Float>(Channel.UNLIMITED)
                        val observers = listOf(
                            launch(start = CoroutineStart.UNDISPATCHED) {
                                viewModelInstance.getNumberFlow(NUMBER_PROPERTY)
                                    .collect(numbers::send)
                            },
                            launch(start = CoroutineStart.UNDISPATCHED) {
                                // A second collector exercises fan-out from the cached property
                                // flow.
                                viewModelInstance.getNumberFlow(NUMBER_PROPERTY)
                                    .collect(secondNumbers::send)
                            },
                            launch(start = CoroutineStart.UNDISPATCHED) {
                                viewModelInstance.getStringFlow(STRING_PROPERTY)
                                    .collect(strings::send)
                            },
                            launch(start = CoroutineStart.UNDISPATCHED) {
                                viewModelInstance.getBooleanFlow(BOOLEAN_PROPERTY)
                                    .collect(booleans::send)
                            },
                            launch(start = CoroutineStart.UNDISPATCHED) {
                                viewModelInstance.getEnumFlow(ENUM_PROPERTY)
                                    .collect(enums::send)
                            },
                            launch(start = CoroutineStart.UNDISPATCHED) {
                                viewModelInstance.getColorFlow(COLOR_PROPERTY)
                                    .collect(colors::send)
                            },
                            launch(start = CoroutineStart.UNDISPATCHED) {
                                viewModelInstance.getTriggerFlow(TRIGGER_PROPERTY)
                                    .collect(triggers::send)
                            },
                            launch(start = CoroutineStart.UNDISPATCHED) {
                                viewModelInstance.getNumberFlow(NESTED_NUMBER_PROPERTY)
                                    .collect(nestedNumbers::send)
                            },
                        )
                        try {
                            // Await initial getter responses to ensure every observer is active.
                            assertEquals("World", strings.awaitProperty(STRING_PROPERTY))
                            assertEquals(true, booleans.awaitProperty(BOOLEAN_PROPERTY))
                            assertEquals("Value 1", enums.awaitProperty(ENUM_PROPERTY))
                            assertEquals(
                                0xFFFF0000.toInt(),
                                colors.awaitProperty(COLOR_PROPERTY),
                            )
                            assertEquals(
                                100f,
                                nestedNumbers.awaitProperty(NESTED_NUMBER_PROPERTY),
                            )
                            assertEquals(123f, numbers.awaitProperty(NUMBER_PROPERTY))
                            assertEquals(123f, secondNumbers.awaitProperty(NUMBER_PROPERTY))

                            stateMachine.bindViewModels(viewModelInstance, emptyMap())
                            // Entry and its target-to-source writes settle across two advances.
                            stateMachine.advance(0.milliseconds)
                            stateMachine.advance(0.milliseconds)

                            assertEquals(456f, numbers.awaitProperty(NUMBER_PROPERTY))
                            assertEquals(456f, secondNumbers.awaitProperty(NUMBER_PROPERTY))
                            assertEquals("Moon", strings.awaitProperty(STRING_PROPERTY))
                            assertEquals(false, booleans.awaitProperty(BOOLEAN_PROPERTY))
                            assertEquals("Value 2", enums.awaitProperty(ENUM_PROPERTY))
                            assertEquals(
                                0xFF00FF00.toInt(),
                                colors.awaitProperty(COLOR_PROPERTY),
                            )
                            triggers.awaitProperty(TRIGGER_PROPERTY)
                            assertEquals(
                                200f,
                                nestedNumbers.awaitProperty(NESTED_NUMBER_PROPERTY),
                            )
                        } finally {
                            observers.forEach { observer -> observer.cancelAndJoin() }
                        }
                    }
                }
            }
        }
    }

    /** Verifies that a relative binding resolves a same-worker VMI from another file. */
    @Test
    fun relativeBinding_resolvesAcrossDifferentFileSchemas() = runBlocking {
        assertCrossFileBinding(RELATIVE_ARTBOARD, RELATIVE_TEXT)
    }

    /** Verifies that an absolute binding does not resolve across different file schemas. */
    @Test
    fun absoluteBinding_doesNotResolveAcrossDifferentFileSchemas() = runBlocking {
        assertCrossFileBinding(ABSOLUTE_ARTBOARD, INITIAL_LABEL)
    }

    /**
     * Binds a File B view model instance to a state machine from the requested File A artboard.
     *
     * The fixture's target-to-source binding copies its text run into [LABEL_PROPERTY] when the
     * authored binding path resolves.
     *
     * @param artboardName The File A artboard whose binding mode is under test.
     * @param expectedLabel The expected File B label after binding and advancing.
     */
    private suspend fun assertCrossFileBinding(
        artboardName: String,
        expectedLabel: String,
    ) {
        RiveFile.load(
            RiveFileSource.RawRes(R.raw.cross_file_binding_a, context.resources),
            riveWorker,
        ).use { hostFile ->
            assertContains(
                hostFile.getArtboardNames(),
                artboardName,
                "Cross-file binding fixture is missing its $artboardName artboard",
            )
            RiveFile.load(
                RiveFileSource.RawRes(R.raw.cross_file_binding_b, context.resources),
                riveWorker,
            ).use { sourceFile ->
                // The dummy property deliberately precedes label so File B's numeric property path
                // differs from File A's while relative name-based resolution remains possible.
                assertEquals(
                    listOf(DUMMY_PROPERTY, LABEL_PROPERTY),
                    sourceFile.getViewModelProperties(FILE_B_VIEW_MODEL).map { it.name },
                    "File B must retain its intentionally shifted property order",
                )
                Artboard.create(hostFile, artboardName).use { artboard ->
                    StateMachine.create(artboard).use { stateMachine ->
                        ViewModelInstance.create(
                            sourceFile,
                            ViewModelSource.Named(FILE_B_VIEW_MODEL).blankInstance(),
                        ).use { viewModelInstance ->
                            viewModelInstance.setString(LABEL_PROPERTY, INITIAL_LABEL)
                            stateMachine.bindViewModels(viewModelInstance, emptyMap())
                            stateMachine.advance(0.milliseconds)

                            val actualLabel = withTimeout(PROPERTY_TIMEOUT_MILLIS) {
                                viewModelInstance.getStringFlow(LABEL_PROPERTY).first()
                            }
                            assertEquals(expectedLabel, actualLabel)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Awaits the next property value emitted to this channel.
 *
 * @param propertyPath The property path used in timeout diagnostics.
 * @return The next emitted property value.
 * @throws IllegalStateException If no value is emitted before the test timeout.
 */
private suspend fun <T> Channel<T>.awaitProperty(propertyPath: String): T =
    checkNotNull(withTimeoutOrNull(PROPERTY_TIMEOUT_MILLIS) { receive() }) {
        "Property '$propertyPath' did not emit within ${PROPERTY_TIMEOUT_MILLIS}ms"
    }

package app.rive

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.GlobalViewModelTestFixture.ADVANCE_TRIGGER
import app.rive.GlobalViewModelTestFixture.ALTERNATE_GLOBAL
import app.rive.GlobalViewModelTestFixture.ALTERNATE_INSTANCE
import app.rive.GlobalViewModelTestFixture.ALTERNATE_MAIN
import app.rive.GlobalViewModelTestFixture.BASE_GLOBAL_1
import app.rive.GlobalViewModelTestFixture.BASE_GLOBAL_2
import app.rive.GlobalViewModelTestFixture.DEFAULT_GLOBAL
import app.rive.GlobalViewModelTestFixture.DEFAULT_GLOBAL_2
import app.rive.GlobalViewModelTestFixture.DEFAULT_INSTANCE
import app.rive.GlobalViewModelTestFixture.DEFAULT_MAIN
import app.rive.GlobalViewModelTestFixture.GLOBAL_STRING
import app.rive.GlobalViewModelTestFixture.GLOBAL_STRING_2
import app.rive.GlobalViewModelTestFixture.GLOBAL_VIEW_MODEL
import app.rive.GlobalViewModelTestFixture.GLOBAL_VIEW_MODEL_2
import app.rive.GlobalViewModelTestFixture.INVALID_GLOBAL_VIEW_MODEL
import app.rive.GlobalViewModelTestFixture.InstanceSpec
import app.rive.GlobalViewModelTestFixture.MAIN_STRING
import app.rive.GlobalViewModelTestFixture.MAIN_VIEW_MODEL
import app.rive.GlobalViewModelTestFixture.OBSERVATION_ARTBOARD
import app.rive.GlobalViewModelTestFixture.SET_GLOBAL_1
import app.rive.GlobalViewModelTestFixture.SET_GLOBAL_2
import app.rive.GlobalViewModelTestFixture.withInstances
import app.rive.runtime.kotlin.test.R
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

private const val GLOBAL_PROPERTY_TIMEOUT_MILLIS = 2_000L

@RunWith(AndroidJUnit4::class)
class GlobalViewModelBindingTest : RiveAndroidTest() {
    /** Verifies the named instances and values on which the binding tests depend. */
    @Test
    fun fixtureInstances_exposeAuthoredValues() = runBlocking {
        RiveFile.load(
            RiveFileSource.RawRes(R.raw.data_bind_test_impl, context.resources),
            riveWorker,
        ).use { file ->
            val cases = listOf(
                InstanceValue(MAIN_VIEW_MODEL, DEFAULT_INSTANCE, MAIN_STRING, DEFAULT_MAIN),
                InstanceValue(MAIN_VIEW_MODEL, ALTERNATE_INSTANCE, MAIN_STRING, ALTERNATE_MAIN),
                InstanceValue(
                    GLOBAL_VIEW_MODEL,
                    DEFAULT_INSTANCE,
                    GLOBAL_STRING,
                    DEFAULT_GLOBAL,
                ),
                InstanceValue(
                    GLOBAL_VIEW_MODEL,
                    ALTERNATE_INSTANCE,
                    GLOBAL_STRING,
                    ALTERNATE_GLOBAL,
                ),
                InstanceValue(
                    GLOBAL_VIEW_MODEL_2,
                    DEFAULT_INSTANCE,
                    GLOBAL_STRING_2,
                    DEFAULT_GLOBAL_2,
                ),
            )
            cases.forEach { case ->
                createInstance(file, case.viewModel, case.instance).use { viewModelInstance ->
                    assertString(viewModelInstance, case.property, case.value)
                }
            }
        }
    }

    /** Verifies that state-machine writes are observed from an explicitly bound global VMI. */
    @Test
    fun bindingGlobal_emitsTargetToSourceValues() = runBlocking {
        withGlobalObservation { file, stateMachine ->
            withInstances(
                file,
                InstanceSpec(MAIN_VIEW_MODEL, DEFAULT_INSTANCE),
                InstanceSpec(GLOBAL_VIEW_MODEL, ALTERNATE_INSTANCE),
            ) { (main, global) ->
                val values = Channel<String>(Channel.UNLIMITED)
                val observer = launch(start = CoroutineStart.UNDISPATCHED) {
                    global.getStringFlow(GLOBAL_STRING).collect(values::send)
                }
                try {
                    assertEquals(
                        ALTERNATE_GLOBAL,
                        withTimeout(GLOBAL_PROPERTY_TIMEOUT_MILLIS) { values.receive() },
                    )

                    stateMachine.bindViewModels(main, mapOf(GLOBAL_VIEW_MODEL to global))
                    stateMachine.advance(0.milliseconds)
                    assertEquals(
                        BASE_GLOBAL_1,
                        withTimeout(GLOBAL_PROPERTY_TIMEOUT_MILLIS) { values.receive() },
                    )

                    main.fireTrigger(ADVANCE_TRIGGER)
                    stateMachine.advance(0.milliseconds)
                    assertEquals(
                        SET_GLOBAL_1,
                        withTimeout(GLOBAL_PROPERTY_TIMEOUT_MILLIS) { values.receive() },
                    )
                } finally {
                    observer.cancelAndJoin()
                }
            }
        }
    }

    /** Verifies that omitting an explicit main still applies authored binding defaults. */
    @Test
    fun bindingWithoutExplicitMain_appliesAuthoredDefaults() = runBlocking {
        withGlobalObservation { file, stateMachine ->
            withInstances(
                file,
                InstanceSpec(GLOBAL_VIEW_MODEL, ALTERNATE_INSTANCE),
            ) { (global) ->
                stateMachine.bindViewModels(null, mapOf(GLOBAL_VIEW_MODEL to global))
                stateMachine.advance(0.milliseconds)

                assertString(global, GLOBAL_STRING, BASE_GLOBAL_1)
            }
        }
    }

    /** Verifies that an invalid global name is ignored and a corrected configuration recovers. */
    @Test
    fun invalidGlobalName_correctedBindingStillApplies() = runBlocking {
        withGlobalObservation { file, stateMachine ->
            withInstances(
                file,
                InstanceSpec(MAIN_VIEW_MODEL, DEFAULT_INSTANCE),
                InstanceSpec(GLOBAL_VIEW_MODEL, ALTERNATE_INSTANCE),
            ) { (main, global) ->
                stateMachine.bindViewModels(main, mapOf(INVALID_GLOBAL_VIEW_MODEL to global))
                stateMachine.advance(0.milliseconds)

                // Invalid names are reported asynchronously through command queue logging.
                assertString(global, GLOBAL_STRING, ALTERNATE_GLOBAL)

                stateMachine.bindViewModels(main, mapOf(GLOBAL_VIEW_MODEL to global))
                stateMachine.advance(0.milliseconds)

                assertString(global, GLOBAL_STRING, BASE_GLOBAL_1)
            }
        }
    }

    /** Verifies that one VMI may be supplied to more than one global slot. */
    @Test
    fun sameInstanceInMultipleGlobalSlots_remainsActive() = runBlocking {
        withGlobalObservation { file, stateMachine ->
            withInstances(
                file,
                InstanceSpec(MAIN_VIEW_MODEL, DEFAULT_INSTANCE),
                InstanceSpec(GLOBAL_VIEW_MODEL, ALTERNATE_INSTANCE),
            ) { (main, global) ->
                stateMachine.bindViewModels(
                    main,
                    mapOf(
                        GLOBAL_VIEW_MODEL to global,
                        GLOBAL_VIEW_MODEL_2 to global,
                    ),
                )
                stateMachine.advance(0.milliseconds)
                assertString(global, GLOBAL_STRING, BASE_GLOBAL_1)

                main.fireTrigger(ADVANCE_TRIGGER)
                stateMachine.advance(0.milliseconds)
                assertString(global, GLOBAL_STRING, SET_GLOBAL_1)
            }
        }
    }

    /** Verifies that replacing a global redirects later state-machine writes to the replacement. */
    @Test
    fun replacingGlobal_redirectsTargetToSourceUpdates() = runBlocking {
        withGlobalObservation { file, stateMachine ->
            withInstances(
                file,
                InstanceSpec(MAIN_VIEW_MODEL, DEFAULT_INSTANCE),
                InstanceSpec(GLOBAL_VIEW_MODEL, DEFAULT_INSTANCE),
                InstanceSpec(GLOBAL_VIEW_MODEL, ALTERNATE_INSTANCE),
            ) { (main, original, replacement) ->
                stateMachine.bindViewModels(
                    main,
                    mapOf(GLOBAL_VIEW_MODEL to original),
                )
                stateMachine.advance(0.milliseconds)
                assertString(original, GLOBAL_STRING, BASE_GLOBAL_1)

                stateMachine.bindViewModels(
                    main,
                    mapOf(GLOBAL_VIEW_MODEL to replacement),
                )
                stateMachine.advance(0.milliseconds)
                assertString(replacement, GLOBAL_STRING, BASE_GLOBAL_1)

                main.fireTrigger(ADVANCE_TRIGGER)
                stateMachine.advance(0.milliseconds)

                assertString(replacement, GLOBAL_STRING, SET_GLOBAL_1)
                assertString(original, GLOBAL_STRING, BASE_GLOBAL_1)
            }
        }
    }

    /** Verifies that removing and later restoring an explicit global changes its participation. */
    @Test
    fun removingGlobal_stopsUpdatesUntilItIsRestored() = runBlocking {
        withGlobalObservation { file, stateMachine ->
            withInstances(
                file,
                InstanceSpec(MAIN_VIEW_MODEL, DEFAULT_INSTANCE),
                InstanceSpec(GLOBAL_VIEW_MODEL, ALTERNATE_INSTANCE),
            ) { (main, global) ->
                stateMachine.bindViewModels(main, mapOf(GLOBAL_VIEW_MODEL to global))
                stateMachine.advance(0.milliseconds)
                assertString(global, GLOBAL_STRING, BASE_GLOBAL_1)

                stateMachine.bindViewModels(main, emptyMap())
                main.fireTrigger(ADVANCE_TRIGGER)
                stateMachine.advance(0.milliseconds)

                // The removed instance no longer receives the second state's text value.
                assertString(global, GLOBAL_STRING, BASE_GLOBAL_1)

                stateMachine.bindViewModels(main, mapOf(GLOBAL_VIEW_MODEL to global))
                stateMachine.advance(0.milliseconds)

                // Rebinding synchronizes the instance to the text value in the current state.
                assertString(global, GLOBAL_STRING, SET_GLOBAL_1)
            }
        }
    }

    /** Verifies that removing one global does not disrupt another global's binding. */
    @Test
    fun removingGlobal_preservesOtherGlobalBinding() = runBlocking {
        withGlobalObservation { file, stateMachine ->
            withInstances(
                file,
                InstanceSpec(MAIN_VIEW_MODEL, DEFAULT_INSTANCE),
                InstanceSpec(GLOBAL_VIEW_MODEL, ALTERNATE_INSTANCE),
                InstanceSpec(GLOBAL_VIEW_MODEL_2, DEFAULT_INSTANCE),
            ) { (main, global1, global2) ->
                stateMachine.bindViewModels(
                    main,
                    mapOf(
                        GLOBAL_VIEW_MODEL to global1,
                        GLOBAL_VIEW_MODEL_2 to global2,
                    ),
                )
                stateMachine.advance(0.milliseconds)
                assertString(global1, GLOBAL_STRING, BASE_GLOBAL_1)
                assertString(global2, GLOBAL_STRING_2, BASE_GLOBAL_2)

                stateMachine.bindViewModels(
                    main,
                    mapOf(GLOBAL_VIEW_MODEL_2 to global2),
                )
                main.fireTrigger(ADVANCE_TRIGGER)
                stateMachine.advance(0.milliseconds)

                // Only the removed instance stops receiving state-machine writes.
                assertString(global1, GLOBAL_STRING, BASE_GLOBAL_1)
                assertString(global2, GLOBAL_STRING_2, SET_GLOBAL_2)
            }
        }
    }

    /**
     * Runs [block] with the observation artboard and state machine from the global test fixture.
     *
     * @param block The test operation to run with the loaded file and state machine.
     * @return The value returned by [block].
     */
    private suspend fun <T> withGlobalObservation(
        block: suspend (RiveFile, StateMachine) -> T,
    ): T = RiveFile.load(
        RiveFileSource.RawRes(R.raw.data_bind_test_impl, context.resources),
        riveWorker,
    ).use { file ->
        Artboard.create(file, OBSERVATION_ARTBOARD).use { artboard ->
            StateMachine.create(artboard).use { stateMachine ->
                block(file, stateMachine)
            }
        }
    }

    /**
     * Creates a named instance of [viewModelName] from [file].
     *
     * @param file The file containing the view model and instance.
     * @param viewModelName The authored view model name.
     * @param instanceName The authored instance name.
     * @return The confirmed view model instance.
     */
    private suspend fun createInstance(
        file: RiveFile,
        viewModelName: String,
        instanceName: String,
    ): ViewModelInstance = ViewModelInstance.create(
        file,
        ViewModelSource.Named(viewModelName).namedInstance(instanceName),
    )

    /**
     * Asserts the current string value at [propertyPath].
     *
     * @param instance The instance containing the string property.
     * @param propertyPath The path to the string property.
     * @param expected The expected property value.
     */
    private suspend fun assertString(
        instance: ViewModelInstance,
        propertyPath: String,
        expected: String,
    ) {
        val actual = withTimeout(GLOBAL_PROPERTY_TIMEOUT_MILLIS) {
            instance.getStringFlow(propertyPath).first()
        }
        assertEquals(expected, actual)
    }
}

/** One named string instance and the authored value expected from it. */
private data class InstanceValue(
    val viewModel: String,
    val instance: String,
    val property: String,
    val value: String,
)

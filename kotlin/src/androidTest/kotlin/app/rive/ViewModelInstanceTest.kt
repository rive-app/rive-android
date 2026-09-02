package app.rive

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.test.R
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val PROPERTY_FLOW_TIMEOUT_MILLIS = 2_000L
private const val TEST_NUMBER_PROPERTY = "Test Num"
private const val UPDATED_NUMBER_VALUE = 42f

@RunWith(AndroidJUnit4::class)
class ViewModelInstanceTest : RiveAndroidTest() {
    /** Verifies instance and view model names for each public creation source. */
    @Test
    fun names_matchFixtureForAllCreationSources() {
        runBlocking {
            val file = RiveFile.load(
                RiveFileSource.RawRes(R.raw.data_bind_test_impl, context.resources),
                riveWorker,
            )
            val instances = mutableListOf<ViewModelInstance>()
            var listArtboard: Artboard? = null

            try {
                val viewModel = ViewModelSource.Named("Test All")
                val namedInstance = ViewModelInstance.create(
                    file,
                    viewModel.namedInstance("Test Alternate")
                ).also(instances::add)
                val defaultInstance = ViewModelInstance.create(
                    file,
                    viewModel.defaultInstance()
                ).also(instances::add)
                val blankInstance = ViewModelInstance.create(
                    file,
                    viewModel.blankInstance()
                ).also(instances::add)
                val nestedInstance = ViewModelInstance.create(
                    file,
                    ViewModelInstanceSource.Reference(defaultInstance, "Test Nested")
                ).also(instances::add)

                listArtboard = Artboard.create(file, "Test List")
                val listOwnerInstance = ViewModelInstance.create(
                    file,
                    ViewModelSource.DefaultForArtboard(listArtboard).defaultInstance()
                ).also(instances::add)
                val listItemInstance = ViewModelInstance.create(
                    file,
                    ViewModelInstanceSource.ReferenceListItem(
                        listOwnerInstance,
                        "Test List",
                        0
                    )
                ).also(instances::add)

                assertNames(namedInstance, "Test All", "Test Alternate")
                assertNames(defaultInstance, "Test All", "Test Default")
                assertNames(blankInstance, "Test All", "")
                assertNames(nestedInstance, "Nested VM", "Default Nested")
                assertNames(listItemInstance, "Test List Item VM", "Test Item 1")

                blankInstance.close()
                instances.remove(blankInstance)
                assertFailsWith<RuntimeException> { blankInstance.getName() }
                assertFailsWith<RuntimeException> { blankInstance.getViewModelName() }
            } finally {
                instances.asReversed().forEach(ViewModelInstance::close)
                listArtboard?.close()
                file.close()
            }
        }
    }

    /**
     * Verifies collector reference counting across the real Kotlin, JNI, and native pipeline.
     *
     * Cancelling one of two collectors must leave the shared native subscription active for the
     * other collector. Cancelling the last collector invokes native unsubscribe, after which a new
     * collector must be able to establish a fresh subscription and read the current value.
     */
    @Test
    fun propertyFlow_unsubscribesAfterLastCollector() = runBlocking {
        RiveFile.load(
            RiveFileSource.RawRes(R.raw.data_bind_test_impl, context.resources),
            riveWorker,
        ).use { file ->
            ViewModelInstance.create(
                file,
                ViewModelSource.Named("Test All").blankInstance(),
            ).use { instance ->
                withTimeout(PROPERTY_FLOW_TIMEOUT_MILLIS) {
                    assertPropertySubscriptionLifecycle(instance)
                }
            }
        }
    }

    /**
     * Exercises first-collector subscribe and last-collector unsubscribe transitions.
     *
     * @param instance The real native-backed view model instance under test.
     */
    private suspend fun assertPropertySubscriptionLifecycle(
        instance: ViewModelInstance,
    ) = coroutineScope {
        val propertyFlow = instance.getNumberFlow(TEST_NUMBER_PROPERTY)
        val firstValues = Channel<Float>(Channel.UNLIMITED)
        val secondValues = Channel<Float>(Channel.UNLIMITED)
        val firstCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            propertyFlow.collect(firstValues::send)
        }
        val secondCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            propertyFlow.collect(secondValues::send)
        }

        try {
            // Both initial getter responses prove collection has started.
            firstValues.receive()
            secondValues.receive()

            firstCollector.cancelAndJoin()
            instance.setNumber(TEST_NUMBER_PROPERTY, UPDATED_NUMBER_VALUE)
            assertEquals(UPDATED_NUMBER_VALUE, secondValues.receive())

            secondCollector.cancelAndJoin() // Last collector crosses the JNI unsubscribe path.
            assertEquals(UPDATED_NUMBER_VALUE, propertyFlow.first())
        } finally {
            firstCollector.cancelAndJoin()
            secondCollector.cancelAndJoin()
            firstValues.close()
            secondValues.close()
        }
    }

    /**
     * Asserts the names attached to an instance by the fixture.
     *
     * @param instance The instance whose names are queried.
     * @param expectedViewModelName The expected view model definition name.
     * @param expectedInstanceName The expected instance name.
     */
    private suspend fun assertNames(
        instance: ViewModelInstance,
        expectedViewModelName: String,
        expectedInstanceName: String
    ) {
        assertEquals(expectedViewModelName, instance.getViewModelName())
        assertEquals(expectedInstanceName, instance.getName())
    }
}

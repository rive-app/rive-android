package app.rive

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.RawRes
import app.rive.core.withPolling
import app.rive.test.R
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class ViewModelInstanceTest : RiveAndroidTest() {
    /** Verifies instance and view model names for each public creation source. */
    @Test
    fun names_matchFixtureForAllCreationSources() {
        riveWorker.withPolling {
            runBlocking {
                val file = loadFixture()
                val instances = mutableListOf<ViewModelInstance>()
                var listArtboard: Artboard? = null

                try {
                    val viewModel = ViewModelSource.Named("Test All")
                    val namedInstance = ViewModelInstance.fromFile(
                        file,
                        viewModel.namedInstance("Test Alternate")
                    ).also(instances::add)
                    val defaultInstance = ViewModelInstance.fromFile(
                        file,
                        viewModel.defaultInstance()
                    ).also(instances::add)
                    val blankInstance = ViewModelInstance.fromFile(
                        file,
                        viewModel.blankInstance()
                    ).also(instances::add)
                    val nestedInstance = ViewModelInstance.fromFile(
                        file,
                        ViewModelInstanceSource.Reference(defaultInstance, "Test Nested")
                    ).also(instances::add)

                    listArtboard = Artboard.fromFile(file, "Test List")
                    val listOwnerInstance = ViewModelInstance.fromFile(
                        file,
                        ViewModelSource.DefaultForArtboard(listArtboard).defaultInstance()
                    ).also(instances::add)
                    val listItemInstance = ViewModelInstance.fromFile(
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
    }

    /** @return The data-binding fixture loaded through the public file API. */
    private suspend fun loadFixture(): RiveFile = when (
        val result = RiveFile.fromSource(
            RawRes(R.raw.data_bind_test_impl, context.resources),
            riveWorker
        )
    ) {
        is Result.Success -> result.value
        is Result.Error ->
            throw AssertionError("Failed to load data-binding fixture", result.throwable)

        is Result.Loading ->
            throw AssertionError("RiveFile.fromSource should not return Loading")
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

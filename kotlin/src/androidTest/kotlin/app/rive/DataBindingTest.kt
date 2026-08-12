package app.rive

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.test.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

@RunWith(AndroidJUnit4::class)
class DataBindingTest : RiveAndroidTest() {
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
                            riveWorker.bindViewModelInstance(
                                stateMachine.stateMachineHandle,
                                viewModelInstance.instanceHandle,
                            )
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

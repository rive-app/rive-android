package app.rive.compose

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.ExperimentalRiveGlobalViewModels
import app.rive.Rive
import app.rive.RiveAndroidTest
import app.rive.RiveIncompatibleResourceException
import app.rive.RiveResourceClosedException
import app.rive.ViewModelInstance
import app.rive.core.FileHandle
import app.rive.core.RiveWorker
import app.rive.core.ViewModelInstanceHandle
import app.rive.runtime.kotlin.test.R
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class RiveResourceValidationComposeTest : RiveAndroidTest() {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rive_rejectsClosedResources() = runBlocking<Unit> {
        val resources = loadRiveResources(R.raw.empty)
        resources.file.close()

        assertFailsWith<RiveResourceClosedException> {
            composeRule.setContent {
                Rive(file = resources.file)
            }
        }
    }

    @Test
    fun rive_rejectsArtboardFromAnotherFile() = runBlocking<Unit> {
        val first = loadRiveResources(R.raw.empty)
        val second = loadRiveResources(R.raw.empty)

        assertFailsWith<RiveIncompatibleResourceException> {
            composeRule.setContent {
                Rive(file = first.file, artboard = second.artboard)
            }
        }
    }

    @Test
    fun rive_rejectsStateMachineFromAnotherArtboard() = runBlocking<Unit> {
        val first = loadRiveResources(R.raw.empty)
        val second = loadRiveResources(R.raw.empty)

        assertFailsWith<RiveIncompatibleResourceException> {
            composeRule.setContent {
                Rive(
                    file = first.file,
                    artboard = first.artboard,
                    stateMachine = second.stateMachine,
                )
            }
        }
    }

    @Test
    fun rive_rejectsViewModelInstanceFromAnotherWorker() = runBlocking<Unit> {
        val resources = loadRiveResources(R.raw.empty)
        val foreignWorker = RiveWorker()
        val foreignInstance = ViewModelInstance(
            ViewModelInstanceHandle(1L),
            foreignWorker,
            FileHandle(1L),
        )

        try {
            assertFailsWith<RiveIncompatibleResourceException> {
                composeRule.setContent {
                    Rive(
                        file = resources.file,
                        artboard = resources.artboard,
                        stateMachine = resources.stateMachine,
                        viewModelInstance = foreignInstance,
                    )
                }
            }
        } finally {
            foreignWorker.release(javaClass.simpleName, "Test cleanup")
        }
    }

    @Test
    @OptIn(ExperimentalRiveGlobalViewModels::class)
    fun rive_rejectsGlobalViewModelInstanceFromAnotherWorker() = runBlocking<Unit> {
        val resources = loadRiveResources(R.raw.empty)
        val foreignWorker = RiveWorker()
        val foreignInstance = ViewModelInstance(
            ViewModelInstanceHandle(1L),
            foreignWorker,
            FileHandle(1L),
        )

        try {
            assertFailsWith<RiveIncompatibleResourceException> {
                composeRule.setContent {
                    Rive(
                        file = resources.file,
                        artboard = resources.artboard,
                        stateMachine = resources.stateMachine,
                        globalViewModelInstances = mapOf("Theme" to foreignInstance),
                    )
                }
            }
        } finally {
            foreignWorker.release(javaClass.simpleName, "Test cleanup")
        }
    }
}

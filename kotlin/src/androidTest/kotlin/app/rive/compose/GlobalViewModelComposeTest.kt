package app.rive.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.ExperimentalRiveGlobalViewModels
import app.rive.GlobalViewModelTestFixture.ADVANCE_TRIGGER
import app.rive.GlobalViewModelTestFixture.ALTERNATE_INSTANCE
import app.rive.GlobalViewModelTestFixture.BASE_GLOBAL_1
import app.rive.GlobalViewModelTestFixture.BASE_GLOBAL_2
import app.rive.GlobalViewModelTestFixture.DEFAULT_INSTANCE
import app.rive.GlobalViewModelTestFixture.GLOBAL_STRING
import app.rive.GlobalViewModelTestFixture.GLOBAL_STRING_2
import app.rive.GlobalViewModelTestFixture.GLOBAL_VIEW_MODEL
import app.rive.GlobalViewModelTestFixture.GLOBAL_VIEW_MODEL_2
import app.rive.GlobalViewModelTestFixture.MAIN_VIEW_MODEL
import app.rive.GlobalViewModelTestFixture.OBSERVATION_ARTBOARD
import app.rive.GlobalViewModelTestFixture.SET_GLOBAL_1
import app.rive.Result
import app.rive.Rive
import app.rive.RiveAndroidTest
import app.rive.RiveFileSource
import app.rive.ViewModelInstance
import app.rive.ViewModelSource
import app.rive.rememberViewModelInstanceResult
import app.rive.runtime.kotlin.test.R
import app.rive.sequence
import org.junit.Rule
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRiveGlobalViewModels::class)
class GlobalViewModelComposeTest : RiveAndroidTest() {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Verifies that recomposition replaces and removes globals before subsequent playback. */
    @Test
    fun rive_updatesGlobalBindingsOnRecomposition() {
        lateinit var updateBindings: MutableState<Boolean>
        val main = AtomicReference<ViewModelInstance?>(null)
        val original = AtomicReference<ViewModelInstance?>(null)
        val replacement = AtomicReference<ViewModelInstance?>(null)
        val removed = AtomicReference<ViewModelInstance?>(null)

        composeRule.setContent {
            updateBindings = remember { mutableStateOf(false) }
            val resourcesResult = rememberTestRiveResources(
                source = RiveFileSource.RawRes.from(R.raw.data_bind_test_impl),
                riveWorker = riveWorker,
                artboardName = OBSERVATION_ARTBOARD,
            )
            val contentResult = resourcesResult.andThen { resources ->
                val mainResult = rememberViewModelInstanceResult(
                    resources.file,
                    ViewModelSource.Named(MAIN_VIEW_MODEL).namedInstance(DEFAULT_INSTANCE),
                )
                val originalResult = rememberViewModelInstanceResult(
                    resources.file,
                    ViewModelSource.Named(GLOBAL_VIEW_MODEL).namedInstance(ALTERNATE_INSTANCE),
                )
                val replacementResult = rememberViewModelInstanceResult(
                    resources.file,
                    ViewModelSource.Named(GLOBAL_VIEW_MODEL).namedInstance(DEFAULT_INSTANCE),
                )
                val removedResult = rememberViewModelInstanceResult(
                    resources.file,
                    ViewModelSource.Named(GLOBAL_VIEW_MODEL_2).namedInstance(DEFAULT_INSTANCE),
                )
                listOf(mainResult, originalResult, replacementResult, removedResult)
                    .sequence()
                    .map { (rememberedMain, rememberedOriginal, rememberedReplacement, rememberedRemoved) ->
                        GlobalComposeContent(
                            resources,
                            rememberedMain,
                            rememberedOriginal,
                            rememberedReplacement,
                            rememberedRemoved,
                        )
                    }
            }
            if (contentResult !is Result.Success) {
                return@setContent
            }

            val content = contentResult.value
            main.set(content.main)
            original.set(content.original)
            replacement.set(content.replacement)
            removed.set(content.removed)
            Rive(
                file = content.resources.file,
                modifier = Modifier.size(200.dp),
                artboard = content.resources.artboard,
                stateMachine = content.resources.stateMachine,
                viewModelInstance = content.main,
                globalViewModelInstances = if (updateBindings.value) {
                    mapOf(GLOBAL_VIEW_MODEL to content.replacement)
                } else {
                    mapOf(
                        GLOBAL_VIEW_MODEL to content.original,
                        GLOBAL_VIEW_MODEL_2 to content.removed,
                    )
                },
            )
        }

        composeRule.awaitWithWallClock(
            timeoutMessage = { "Global view model instances were not created" },
        ) {
            original.get() != null && replacement.get() != null && removed.get() != null
        }
        val originalGlobal = checkNotNull(original.get())
        val replacementGlobal = checkNotNull(replacement.get())
        val removedGlobal = checkNotNull(removed.get())
        composeRule.awaitProperty(
            propertyPath = GLOBAL_STRING,
            getFlow = originalGlobal::getStringFlow,
            expected = BASE_GLOBAL_1,
        )
        composeRule.awaitProperty(
            propertyPath = GLOBAL_STRING_2,
            getFlow = removedGlobal::getStringFlow,
            expected = BASE_GLOBAL_2,
        )

        composeRule.runOnUiThread { updateBindings.value = true }
        composeRule.awaitProperty(
            propertyPath = GLOBAL_STRING,
            getFlow = replacementGlobal::getStringFlow,
            expected = BASE_GLOBAL_1,
        )
        composeRule.runOnUiThread {
            checkNotNull(main.get()).fireTrigger(ADVANCE_TRIGGER)
        }
        composeRule.awaitProperty(
            propertyPath = GLOBAL_STRING,
            getFlow = replacementGlobal::getStringFlow,
            expected = SET_GLOBAL_1,
        )
        composeRule.awaitProperty(
            propertyPath = GLOBAL_STRING,
            getFlow = originalGlobal::getStringFlow,
            expected = BASE_GLOBAL_1,
        )
        composeRule.awaitProperty(
            propertyPath = GLOBAL_STRING_2,
            getFlow = removedGlobal::getStringFlow,
            expected = BASE_GLOBAL_2,
        )
    }
}

private data class GlobalComposeContent(
    val resources: TestRiveResources,
    val main: ViewModelInstance,
    val original: ViewModelInstance,
    val replacement: ViewModelInstance,
    val removed: ViewModelInstance,
)

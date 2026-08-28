@file:OptIn(app.rive.ExperimentalRiveSemantics::class)

package app.rive.runtime.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.rive.Fit
import app.rive.Result
import app.rive.Rive
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.RiveLog
import app.rive.RiveSemanticsMode
import app.rive.core.RiveWorker
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorker
import app.rive.rememberViewModelInstanceResult
import android.graphics.Color as AndroidColor

/** Manual device harness for the semantic fixtures used by automated runtime tests. */
class SemanticsHarnessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
        )
        RiveLog.logger = RiveLog.LogcatLogger()

        setContent { SemanticsHarness() }
    }
}

/** Describes one fixture page and the behavior it is intended to exercise. */
private data class SemanticsHarnessCase(
    @RawRes val resourceId: Int,
    val title: String,
    val description: String,
)

private val semanticsHarnessCases = listOf(
    SemanticsHarnessCase(
        R.raw.simpsons,
        "Simpsons tabs and lists",
        "Tab selection, filtered list content, roles, labels, and semantic tap actions.",
    ),
    SemanticsHarnessCase(
        R.raw.tabtest,
        "Compact tab fixture",
        "Initial publication, authored tab order, selected state, actions, and fit bounds.",
    ),
    SemanticsHarnessCase(
        R.raw.data_binding_lists,
        "Data-bound dropdown",
        "Expanded state, list traversal, collapse action, and removal of hidden descendants.",
    ),
    SemanticsHarnessCase(
        R.raw.data_binding_lists_items,
        "Nested list items",
        "Nested-artboard list topology, authored item order, and parent-relative bounds.",
    ),
    SemanticsHarnessCase(
        R.raw.focus_nodes_list_order,
        "Authored focus order",
        "Four buttons whose authored traversal order intentionally differs from their node IDs.",
    ),
    SemanticsHarnessCase(
        R.raw.semantic_list_scroll_focus_fixed,
        "Focus-driven scrolling",
        "Accessibility focus gain, transfer, clear, and geometry changes caused by scrolling.",
    ),
)

/** Displays fixture navigation, lifecycle controls, and the currently selected Rive file. */
@Composable
private fun SemanticsHarness() {
    val riveWorker = rememberRiveWorker()
    var pageIndex by rememberSaveable { mutableStateOf(0) }
    var semanticsRequested by rememberSaveable { mutableStateOf(true) }
    var playing by rememberSaveable { mutableStateOf(true) }
    val testCase = semanticsHarnessCases[pageIndex]

    Scaffold(containerColor = Color(0xFF111820)) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${pageIndex + 1}/${semanticsHarnessCases.size}: ${testCase.title}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = testCase.description,
                    color = Color(0xFFCBD5E1),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        enabled = pageIndex > 0,
                        onClick = { pageIndex-- },
                    ) {
                        Text("Previous")
                    }
                    Button(
                        enabled = pageIndex < semanticsHarnessCases.lastIndex,
                        onClick = { pageIndex++ },
                    ) {
                        Text("Next")
                    }
                }
                HarnessToggle(
                    label = "Publish semantics",
                    checked = semanticsRequested,
                    onCheckedChange = { semanticsRequested = it },
                )
                HarnessToggle(
                    label = "Advance with frame time",
                    checked = playing,
                    onCheckedChange = { playing = it },
                )
                Text(
                    text = if (semanticsRequested) {
                        "Semantics mode: automatic"
                    } else {
                        "Semantics mode: off"
                    },
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            HorizontalDivider(color = Color(0xFF334155))

            // Give each page a distinct composition lifecycle so its Rive resources are disposed.
            key(testCase.resourceId) {
                SemanticsFixture(
                    testCase = testCase,
                    riveWorker = riveWorker,
                    playing = playing,
                    semantics = if (semanticsRequested) {
                        RiveSemanticsMode.Automatic
                    } else {
                        RiveSemanticsMode.Off
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Displays a labelled switch used to exercise semantics and playback lifecycle changes. */
@Composable
private fun HarnessToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Loads one fixture and binds its default view-model instance when the file provides one. */
@Composable
private fun SemanticsFixture(
    testCase: SemanticsHarnessCase,
    riveWorker: RiveWorker,
    playing: Boolean,
    semantics: RiveSemanticsMode,
    modifier: Modifier = Modifier,
) {
    when (
        val fileResult = rememberRiveFile(
            RiveFileSource.RawRes.from(testCase.resourceId),
            riveWorker,
        )
    ) {
        Result.Loading -> LoadingIndicator(modifier)
        is Result.Error -> ErrorMessage(fileResult.throwable, modifier)
        is Result.Success -> LoadedSemanticsFixture(
            file = fileResult.value,
            playing = playing,
            semantics = semantics,
            modifier = modifier,
        )
    }
}

/** Renders a loaded fixture after resolving its optional default data-binding instance. */
@Composable
private fun LoadedSemanticsFixture(
    file: RiveFile,
    playing: Boolean,
    semantics: RiveSemanticsMode,
    modifier: Modifier = Modifier,
) {
    when (val viewModelResult = rememberViewModelInstanceResult(file)) {
        Result.Loading -> LoadingIndicator(modifier)
        is Result.Success -> Rive(
            file = file,
            modifier = modifier.fillMaxSize(),
            playing = playing,
            viewModelInstance = viewModelResult.value,
            fit = Fit.Contain(),
            semantics = semantics,
        )

        is Result.Error -> Box(modifier = modifier.fillMaxSize()) {
            Rive(
                file = file,
                modifier = Modifier.fillMaxSize(),
                playing = playing,
                fit = Fit.Contain(),
                semantics = semantics,
            )
            Text(
                text = "Default data binding unavailable: ${viewModelResult.throwable.message}",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
                color = Color(0xFFFFB4AB),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

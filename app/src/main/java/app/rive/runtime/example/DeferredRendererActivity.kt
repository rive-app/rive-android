package app.rive.runtime.example

import android.graphics.Color as AndroidColor
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.rive.ExperimentalDeferredRendering
import app.rive.RenderBackend
import app.rive.Result
import app.rive.Rive
import app.rive.RiveFileSource
import app.rive.RiveLog
import app.rive.core.RiveWorker
import app.rive.rememberDeferredRiveWorker
import app.rive.rememberViewModelInstanceResult

/** Accessibility descriptions that identify ready deferred-renderer content and its backend. */
internal object DeferredRendererContentDescriptions {
    /**
     * Creates the description applied after a file's resource tree is ready.
     *
     * @param fileName The displayed Rive filename.
     * @param renderBackend The requested rendering backend.
     * @return A description identifying the ready file and backend.
     */
    fun forFile(fileName: String, renderBackend: RenderBackend): String =
        "Rendering $fileName with ${renderBackend.displayName}"
}

/** Human-readable name used by the backend selector and accessibility descriptions. */
private val RenderBackend.displayName: String
    get() = when (this) {
        RenderBackend.OpenGL -> "OpenGL"
        RenderBackend.Vulkan -> "Vulkan"
    }

/** Demonstrates the deferred renderer with the bundled GPU Canvas test files. */
class DeferredRendererActivity : ComponentActivity() {
    /** Creates the deferred worker demo and its file selector. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.BLACK)
        )
        RiveLog.logger = RiveLog.LogcatLogger()

        setContent { DeferredRendererDemo() }
    }
}

/** A deferred-rendering sample bundled with the app. */
private enum class DeferredRendererDemoFile(
    @RawRes val resourceId: Int,
    val displayName: String,
) {
    Ore(R.raw.ore, "ore.riv"),
    MultiStage(R.raw.multi_stage, "multi-stage.riv"),
}

/** Displays the deferred-renderer file selector and currently selected file. */
@OptIn(ExperimentalDeferredRendering::class)
@Composable
private fun DeferredRendererDemo() {
    var selectedFile by rememberSaveable {
        mutableStateOf(DeferredRendererDemoFile.Ore)
    }
    var selectedBackend by rememberSaveable {
        mutableStateOf(RenderBackend.OpenGL)
    }
    val riveWorker = rememberDeferredRiveWorker(renderBackend = selectedBackend)

    Scaffold(containerColor = Color(0xFF0C1935)) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DeferredRendererDemoFile.entries.forEach { demoFile ->
                    val modifier = Modifier
                        .weight(1f)
                        .semantics { selected = demoFile == selectedFile }
                    if (demoFile == selectedFile) {
                        Button(
                            modifier = modifier,
                            onClick = { selectedFile = demoFile },
                        ) {
                            Text(demoFile.displayName)
                        }
                    } else {
                        OutlinedButton(
                            modifier = modifier,
                            onClick = { selectedFile = demoFile },
                        ) {
                            Text(demoFile.displayName)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(RenderBackend.OpenGL, RenderBackend.Vulkan).forEach { renderBackend ->
                    val modifier = Modifier
                        .weight(1f)
                        .semantics { selected = renderBackend == selectedBackend }
                    if (renderBackend == selectedBackend) {
                        Button(
                            modifier = modifier,
                            onClick = { selectedBackend = renderBackend },
                        ) {
                            Text(renderBackend.displayName)
                        }
                    } else {
                        OutlinedButton(
                            modifier = modifier,
                            onClick = { selectedBackend = renderBackend },
                        ) {
                            Text(renderBackend.displayName)
                        }
                    }
                }
            }

            val contentModifier = Modifier
                .weight(1f)
                .fillMaxWidth()
            key(selectedFile, selectedBackend) {
                DeferredRendererFile(
                    demoFile = selectedFile,
                    renderBackend = selectedBackend,
                    riveWorker = riveWorker,
                    modifier = contentModifier,
                )
            }
        }
    }
}

/**
 * Loads and displays one deferred-renderer test file.
 *
 * @param demoFile The bundled file to display.
 * @param renderBackend The backend requested by the worker that owns the resources.
 * @param riveWorker The deferred worker that owns the file and its resources.
 * @param modifier The modifier applied to the content area.
 */
@Composable
private fun DeferredRendererFile(
    demoFile: DeferredRendererDemoFile,
    renderBackend: RenderBackend,
    riveWorker: RiveWorker,
    modifier: Modifier = Modifier,
) {
    val source = RiveFileSource.RawRes.from(demoFile.resourceId)
    val resourcesResult = rememberScriptingResources(source, riveWorker)
    val contentResult = resourcesResult.andThen { resources ->
        rememberViewModelInstanceResult(resources.file).map { vmi -> resources to vmi }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (contentResult) {
            is Result.Loading -> LoadingIndicator()
            is Result.Error -> ErrorMessage(contentResult.throwable)
            is Result.Success -> {
                val (resources, vmi) = contentResult.value
                Rive(
                    file = resources.file,
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics {
                            contentDescription = DeferredRendererContentDescriptions.forFile(
                                demoFile.displayName,
                                renderBackend,
                            )
                        },
                    artboard = resources.artboard,
                    stateMachine = resources.stateMachine,
                    viewModelInstance = vmi,
                )
            }
        }
    }
}

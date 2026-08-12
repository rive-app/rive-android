package app.rive.runtime.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.rive.Artboard
import app.rive.Result
import app.rive.Rive
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.RiveLog
import app.rive.StateMachine
import app.rive.core.RiveWorker
import app.rive.rememberArtboardResult
import app.rive.rememberRiveFile
import app.rive.rememberStateMachineResult
import app.rive.rememberRiveWorker
import app.rive.rememberViewModelInstanceResult
import android.graphics.Color as AndroidColor

class ScriptingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.BLACK)
        )
        RiveLog.logger = RiveLog.LogcatLogger()

        setContent {
            val riveWorker = rememberRiveWorker()
            val resourcesResult = rememberScriptingResources(
                RiveFileSource.RawRes.from(R.raw.blinko),
                riveWorker
            )
            val contentResult = resourcesResult.andThen { resources ->
                rememberViewModelInstanceResult(resources.file).map { vmi -> resources to vmi }
            }

            Scaffold(containerColor = Color(0xFF0C1935)) { innerPadding ->
                when (contentResult) {
                    is Result.Loading -> LoadingIndicator()
                    is Result.Error -> ErrorMessage(contentResult.throwable)
                    is Result.Success -> {
                        val (resources, vmi) = contentResult.value
                        Rive(
                            resources.file,
                            Modifier.padding(innerPadding),
                            artboard = resources.artboard,
                            stateMachine = resources.stateMachine,
                            viewModelInstance = vmi
                        )
                    }
                }
            }
        }
    }
}

/**
 * Resources resolved by the scripting example's user-land Compose helper.
 *
 * @param file The loaded file.
 * @param artboard The artboard created from [file].
 * @param stateMachine The state machine created from [artboard].
 */
private data class ScriptingResources(
    val file: RiveFile,
    val artboard: Artboard,
    val stateMachine: StateMachine,
)

/**
 * Sequentially remembers the resources needed by the scripting example.
 *
 * This deliberately demonstrates an application-owned convenience wrapper rather than exposing
 * the temporary resource-triplet shape from the runtime API.
 *
 * @param source The source of the Rive file.
 * @param riveWorker The worker that owns the resources.
 * @return The current creation result for the resolved resources.
 */
@Composable
private fun rememberScriptingResources(
    source: RiveFileSource,
    riveWorker: RiveWorker,
): Result<ScriptingResources> = rememberRiveFile(source, riveWorker).andThen { file ->
    rememberArtboardResult(file).andThen { artboard ->
        rememberStateMachineResult(artboard).map { stateMachine ->
            ScriptingResources(file, artboard, stateMachine)
        }
    }
}

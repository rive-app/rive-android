package app.rive.runtime.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
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
import app.rive.ExperimentalDeferredRendering
import app.rive.rememberDeferredRiveWorker
import app.rive.rememberStateMachineResult
import app.rive.rememberRiveWorker
import app.rive.rememberViewModelInstanceResult
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Color as AndroidColor

class ScriptingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.BLACK)
        )
        RiveLog.logger = RiveLog.LogcatLogger()

        // demoRiv/deferred extras let adb drive any riv through this harness.
        val demoPath = intent.getStringExtra("demoRiv")
        val deferred = intent.getBooleanExtra("deferred", false)

        setContent {
            @OptIn(ExperimentalDeferredRendering::class)
            val riveWorker = if (deferred) {
                rememberDeferredRiveWorker()
            } else {
                rememberRiveWorker()
            }
            val sourceResult = rememberScriptingSource(demoPath)
            val resourcesResult = sourceResult.andThen { source ->
                rememberScriptingResources(source, riveWorker)
            }
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
 * Resolves the Rive file source used by the scripting harness.
 *
 * Files supplied through adb are not Android resources, so they are read as bytes on an I/O
 * dispatcher. Filesystem failures become [Result.Error] values for the activity to display.
 *
 * @param demoPath Optional filesystem path supplied through the `demoRiv` intent extra.
 * @return The current source-loading result.
 */
@Composable
internal fun rememberScriptingSource(demoPath: String?): Result<RiveFileSource> {
    if (demoPath == null) {
        return Result.Success(RiveFileSource.RawRes.from(R.raw.blinko))
    }

    return produceState<Result<RiveFileSource>>(Result.Loading, demoPath) {
        value = try {
            val bytes = withContext(Dispatchers.IO) {
                File(demoPath).readBytes()
            }
            Result.Success(RiveFileSource.Bytes(bytes))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.Error(e)
        }
    }.value
}

/**
 * Resources resolved by the scripting example's user-land Compose helper.
 *
 * @param file The loaded file.
 * @param artboard The artboard created from [file].
 * @param stateMachine The state machine created from [artboard].
 */
internal data class ScriptingResources(
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
internal fun rememberScriptingResources(
    source: RiveFileSource,
    riveWorker: RiveWorker,
): Result<ScriptingResources> = rememberRiveFile(source, riveWorker).andThen { file ->
    rememberArtboardResult(file).andThen { artboard ->
        rememberStateMachineResult(artboard).map { stateMachine ->
            ScriptingResources(file, artboard, stateMachine)
        }
    }
}

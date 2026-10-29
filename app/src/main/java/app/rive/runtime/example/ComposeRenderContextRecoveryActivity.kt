package app.rive.runtime.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rive.Fit
import app.rive.ImageAsset
import app.rive.Result
import app.rive.Rive
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.RiveLog
import app.rive.ViewModelInstance
import app.rive.core.ComposeFrameTicker
import app.rive.rememberImage
import app.rive.rememberRiveFile
import app.rive.rememberViewModelInstanceResult

/** Recovers from a lost render context in Compose, loading everything again on a new worker. */
class ComposeRenderContextRecoveryActivity : ComponentActivity() {
    private val viewModel: RenderContextRecoveryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.BLACK)
        )
        RiveLog.logger = RiveLog.LogcatLogger()

        setContent {
            val workerAndImageResult by viewModel.workerAndImage.collectAsStateWithLifecycle()
            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect(lifecycleOwner, workerAndImageResult) {
                val (riveWorker, _) = (workerAndImageResult as? Result.Success)?.value
                    ?: return@LaunchedEffect
                riveWorker.beginPolling(lifecycleOwner.lifecycle, ComposeFrameTicker)
            }

            val contentResult = workerAndImageResult.andThen { (riveWorker, imageBytes) ->
                // Keyed on the worker, so a new worker loads these again and closes the old ones.
                val riveFileResult = rememberRiveFile(
                    RiveFileSource.RawRes.from(R.raw.image_db_cats),
                    riveWorker
                )
                val imageResult = rememberImage(riveWorker, imageBytes)
                riveFileResult.andThen { riveFile ->
                    rememberViewModelInstanceResult(riveFile).zip(imageResult) { vmi, image ->
                        CardContent(riveFile, image, vmi)
                    }
                }
            }

            Scaffold(
                containerColor = Color.Black,
                contentColor = Color.White,
                bottomBar = {
                    Button(
                        onClick = viewModel::simulateLoss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(8.dp)
                    ) {
                        Text("Simulate loss")
                    }
                }
            ) { innerPadding ->
                when (contentResult) {
                    is Result.Loading -> LoadingIndicator(Modifier.padding(innerPadding))

                    is Result.Error -> ErrorMessage(
                        contentResult.throwable,
                        Modifier.padding(innerPadding)
                    )

                    is Result.Success -> {
                        val (riveFile, image, vmi) = contentResult.value
                        LaunchedEffect(vmi, image) {
                            vmi.setImage("property of Card/Image property", image)
                        }
                        Rive(
                            riveFile,
                            Modifier.padding(innerPadding),
                            viewModelInstance = vmi,
                            fit = Fit.Layout(1.4f),
                        )
                    }
                }
            }
        }
    }
}

private data class CardContent(
    val riveFile: RiveFile,
    val image: ImageAsset,
    val vmi: ViewModelInstance,
)

package app.rive.runtime.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rive.Fit
import app.rive.Result
import app.rive.Rive
import app.rive.RiveFileSource
import app.rive.RiveLog
import app.rive.rememberImage
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorker
import app.rive.rememberViewModelInstanceResult
import app.rive.sequence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.withContext
import android.graphics.Color as AndroidColor

class ComposeImageBindingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.BLACK)
        )
        RiveLog.logger = RiveLog.LogcatLogger()

        setContent {
            val context = LocalContext.current

            val riveWorker = rememberRiveWorker()
            val riveFileResult = rememberRiveFile(
                RiveFileSource.RawRes.from(R.raw.image_db_cats),
                riveWorker
            )

            // Convert the list of image resources into a Result<List<ImageAsset>>
            val imagesResult = listOf(
                R.raw.cat1,
                R.raw.cat2,
                R.raw.cat3,
                R.raw.cat4,
                R.raw.cat5,
                R.raw.cat6,
                R.raw.cat7
            ).map { id ->
                produceState<Result<ByteArray>>(Result.Loading, id) {
                    value = withContext(Dispatchers.IO) {
                        context.resources.openRawResource(id)
                            .use { Result.Success(it.readBytes()) }
                    }
                }.value.andThen { bytes -> rememberImage(riveWorker, bytes) }
            }.sequence()

            val fileAndImagesResult = riveFileResult.zip(imagesResult)
            val contentResult = fileAndImagesResult.andThen { (file, loadedImages) ->
                rememberViewModelInstanceResult(file).andThen { vmi ->
                    Result.Success(Triple(file, loadedImages, vmi))
                }
            }

            Scaffold(containerColor = Color.Black) { innerPadding ->
                when (contentResult) {
                    is Result.Loading -> LoadingIndicator()
                    is Result.Error -> ErrorMessage(contentResult.throwable)
                    is Result.Success -> {
                        val (riveFile, images, vmi) = contentResult.value

                        // Convert updates to wrapped incrementing indices
                        val imageIndex by remember(vmi, images.size) {
                            vmi.getBooleanFlow("Update")
                                .filter { it } // Only respond when it's true (i.e. card is face down)
                                .runningFold(0) { idx, _ -> if (images.isEmpty()) 0 else (idx + 1) % images.size }
                        }.collectAsStateWithLifecycle(initialValue = 0)

                        // Update the image when the index changes
                        LaunchedEffect(riveFile, images, imageIndex) {
                            if (images.isEmpty()) return@LaunchedEffect

                            vmi.setImage("property of Card/Image property", images[imageIndex])
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

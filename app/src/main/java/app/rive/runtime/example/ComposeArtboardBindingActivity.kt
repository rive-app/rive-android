package app.rive.runtime.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.rive.Artboard
import app.rive.Fit
import app.rive.Result
import app.rive.Rive
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.RiveLog
import app.rive.ViewModelInstance
import app.rive.rememberArtboardResult
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorker
import app.rive.rememberViewModelInstanceResult
import android.graphics.Color as AndroidColor

class ComposeArtboardBindingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.BLACK)
        )
        RiveLog.logger = RiveLog.LogcatLogger()

        setContent {
            val riveWorker = rememberRiveWorker()
            val mainRiveFile = rememberRiveFile(
                RiveFileSource.RawRes.from(R.raw.swap_character_main),
                riveWorker
            )
            val assetRiveFile = rememberRiveFile(
                RiveFileSource.RawRes.from(R.raw.swap_character_assets),
                riveWorker
            )
            val bothFiles = mainRiveFile.zip(assetRiveFile)
            val contentResult = bothFiles.andThen { (mainFile, assetFile) ->
                val artboardsResult = rememberArtboardResult(assetFile, "Character 1").zip(
                    rememberArtboardResult(assetFile, "Character 2")
                )
                rememberViewModelInstanceResult(mainFile).zip(artboardsResult) {
                        vmi, (dragonArtboard, crocodileArtboard) ->
                    ArtboardBindingContent(
                        mainFile,
                        vmi,
                        dragonArtboard,
                        crocodileArtboard
                    )
                }
            }

            var useDragon by remember { mutableStateOf(true) }

            Scaffold(containerColor = Color.Black) { innerPadding ->
                Column(modifier = Modifier.padding(innerPadding)) {
                    when (contentResult) {
                        is Result.Loading -> LoadingIndicator()
                        is Result.Error -> ErrorMessage(contentResult.throwable)
                        is Result.Success -> {
                            val (mainFile, vmi, dragonArtboard, crocodileArtboard) =
                                contentResult.value

                            LaunchedEffect(mainFile, dragonArtboard, useDragon) {
                                if (useDragon) {
                                    vmi.setArtboard("CharacterArtboard", dragonArtboard)
                                } else {
                                    vmi.setArtboard("CharacterArtboard", crocodileArtboard)
                                }
                            }

                            Rive(
                                mainFile,
                                Modifier.weight(1f),
                                viewModelInstance = vmi,
                                fit = Fit.Layout(),
                            )
                        }
                    }
                    Row(
                        Modifier.windowInsetsPadding(
                            WindowInsets.safeGestures.only(
                                WindowInsetsSides.Horizontal
                            )
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Use Dragon Artboard",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = useDragon,
                            onCheckedChange = { checked ->
                                useDragon = checked
                            }
                        )
                    }
                }
            }
        }
    }
}

private data class ArtboardBindingContent(
    val file: RiveFile,
    val vmi: ViewModelInstance,
    val dragonArtboard: Artboard,
    val crocodileArtboard: Artboard,
)

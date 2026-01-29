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
import app.rive.Fit
import app.rive.Result
import app.rive.Result.Loading.zip
import app.rive.Rive
import app.rive.RiveFileSource
import app.rive.RiveLog
import app.rive.rememberArtboard
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorker
import app.rive.rememberViewModelInstance
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

            var useDragon by remember { mutableStateOf(true) }

            Scaffold(containerColor = Color.Black) { innerPadding ->
                Column(modifier = Modifier.padding(innerPadding)) {
                    when (bothFiles) {
                        is Result.Loading -> LoadingIndicator()
                        is Result.Error -> ErrorMessage(bothFiles.throwable)
                        is Result.Success -> {
                            val (mainFile, assetFile) = bothFiles.value
                            val vmi = rememberViewModelInstance(mainFile)
                            val dragonArtboard = rememberArtboard(assetFile, "Character 1")
                            val crocodileArtboard = rememberArtboard(assetFile, "Character 2")

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

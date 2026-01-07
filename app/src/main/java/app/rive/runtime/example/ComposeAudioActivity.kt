package app.rive.runtime.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.rive.ExperimentalRiveComposeAPI
import app.rive.Result
import app.rive.Rive
import app.rive.RiveFileSource
import app.rive.RiveLog
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorker
import android.graphics.Color as AndroidColor

class ComposeAudioActivity : ComponentActivity() {
    @OptIn(ExperimentalRiveComposeAPI::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.BLACK)
        )
        RiveLog.logger = RiveLog.LogcatLogger()

        setContent {
            val riveWorker = rememberRiveWorker()
            val riveFile = rememberRiveFile(
                RiveFileSource.RawRes.from(R.raw.lip_sync_test),
                riveWorker
            )

            Scaffold(containerColor = Color.Black) { innerPadding ->
                when (riveFile) {
                    is Result.Loading -> LoadingIndicator()
                    is Result.Error -> ErrorMessage(riveFile.throwable)
                    is Result.Success -> Rive(
                        riveFile.value,
                        Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

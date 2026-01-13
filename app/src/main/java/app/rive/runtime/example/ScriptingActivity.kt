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
import app.rive.Result
import app.rive.Rive
import app.rive.RiveFileSource
import app.rive.RiveLog
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorker
import app.rive.rememberViewModelInstance
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
            val riveFile = rememberRiveFile(
                RiveFileSource.RawRes.from(R.raw.blinko),
                riveWorker
            )

            Scaffold(containerColor = Color(0xFF0C1935)) { innerPadding ->
                when (riveFile) {
                    is Result.Loading -> LoadingIndicator()
                    is Result.Error -> ErrorMessage(riveFile.throwable)
                    is Result.Success -> {
                        val file = riveFile.value
                        val vmi = rememberViewModelInstance(file)
                        Rive(
                            riveFile.value,
                            Modifier.padding(innerPadding),
                            viewModelInstance = vmi
                        )
                    }
                }
            }
        }
    }
}

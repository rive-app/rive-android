package app.rive.runtime.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.rive.ExperimentalRiveComposeAPI
import app.rive.Fit
import app.rive.Result
import app.rive.Rive
import app.rive.RiveFileSource
import app.rive.RiveLog
import app.rive.RivePointerInputMode
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorker
import app.rive.rememberViewModelInstance
import android.graphics.Color as AndroidColor

class ComposeTouchPassThroughActivity : ComponentActivity() {
    @OptIn(ExperimentalRiveComposeAPI::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.BLACK)
        )
        RiveLog.logger = RiveLog.LogcatLogger()

        setContent {
            LocalContext.current

            val riveWorker = rememberRiveWorker()
            val riveFile = rememberRiveFile(
                RiveFileSource.RawRes.from(R.raw.touch_passthrough),
                riveWorker
            )

            var inputMode by remember { mutableStateOf(RivePointerInputMode.Consume) }

            Scaffold(containerColor = Color.Black) { innerPadding ->
                Column(modifier = Modifier.padding(innerPadding)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Button({
                            RiveLog.i("TouchPassThrough") { "Clicked!" }
                        }) {
                            Text("Click Me")
                        }

                        when (riveFile) {
                            is Result.Loading -> LoadingIndicator()
                            is Result.Error -> ErrorMessage(riveFile.throwable)
                            is Result.Success -> {
                                val vmi = rememberViewModelInstance(riveFile.value)
                                Rive(
                                    riveFile.value,
                                    modifier = Modifier.matchParentSize(),
                                    viewModelInstance = vmi,
                                    fit = Fit.Layout(2f),
                                    pointerInputMode = inputMode
                                )
                            }
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
                            text = "Allow touch pass-through",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = inputMode == RivePointerInputMode.PassThrough,
                            onCheckedChange = {
                                inputMode =
                                    if (it) RivePointerInputMode.PassThrough else RivePointerInputMode.Consume
                            }
                        )
                    }
                }
            }
        }
    }
}

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.rive.ExperimentalRiveComposeAPI
import app.rive.Result
import app.rive.RiveFileSource
import app.rive.RiveLog
import app.rive.RiveUI
import app.rive.rememberCommandQueue
import app.rive.rememberRiveFile
import app.rive.runtime.kotlin.core.Fit
import java.util.Locale
import android.graphics.Color as AndroidColor

class ComposeLayoutActivity : ComponentActivity() {
    @OptIn(ExperimentalRiveComposeAPI::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.BLACK)
        )
        RiveLog.logger = RiveLog.LogcatLogger()

        setContent {
            val context = LocalContext.current

            val commandQueue = rememberCommandQueue()
            val riveFile = rememberRiveFile(
                RiveFileSource.RawRes(R.raw.layouts_demo, context.resources),
                commandQueue
            )

            var useLayout by rememberSaveable { mutableStateOf(true) }
            var scaleFactor by rememberSaveable { mutableStateOf(1f) }
            val fit = if (useLayout) Fit.LAYOUT else Fit.CONTAIN

            Scaffold(containerColor = Color.Black) { innerPadding ->
                Column(modifier = Modifier.padding(innerPadding)) {
                    // Rive UI content
                    Box(modifier = Modifier.weight(1f)) {
                        when (riveFile) {
                            is Result.Loading -> LoadingIndicator()
                            is Result.Error -> ErrorMessage(riveFile.throwable)
                            is Result.Success -> {
                                RiveUI(
                                    riveFile.value,
                                    fit = fit,
                                    layoutScaleFactor = scaleFactor
                                )
                            }
                        }
                    }

                    // Controls at the bottom
                    Column(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeGestures),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Use Layout toggle
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Use Layout",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = useLayout,
                                onCheckedChange = { useLayout = it }
                            )
                        }

                        // Scale Factor slider
                        Column {
                            val scaleFactorString =
                                String.format(Locale.getDefault(), "%.2f", scaleFactor)
                            Text(
                                text = "Scale Factor: $scaleFactorString",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            val minSize = 0.5f
                            val maxSize = 2f
                            val stepSize = 0.05f
                            val steps = ((maxSize - minSize) / stepSize).toInt() - 1
                            Slider(
                                value = scaleFactor,
                                enabled = useLayout,
                                onValueChange = { scaleFactor = it },
                                valueRange = minSize..maxSize,
                                steps = steps,
                                modifier = Modifier
                                    .windowInsetsPadding(WindowInsets.safeGestures)
                            )
                        }
                    }
                }
            }
        }
    }
}

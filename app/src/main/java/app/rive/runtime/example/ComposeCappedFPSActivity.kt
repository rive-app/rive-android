package app.rive.runtime.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.rive.Fit
import app.rive.Result
import app.rive.Rive
import app.rive.RiveFileSource
import app.rive.RiveFrameRate
import app.rive.RiveLog
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorker
import java.util.Locale
import android.graphics.Color as AndroidColor

private val FPS_PRESETS = listOf(12f, 24f, 30f, 60f, 90f, 120f, 240f)
private val FPS_PRESET_ROWS = listOf(
    FPS_PRESETS.take(4),
    FPS_PRESETS.drop(4)
)

class ComposeCappedFPSActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.BLACK)
        )
        RiveLog.logger = RiveLog.LogcatLogger()

        setContent {
            val riveWorker = rememberRiveWorker()
            val riveFile = rememberRiveFile(RiveFileSource.RawRes.from(R.raw.marty), riveWorker)
            var framesPerSecond by rememberSaveable { mutableFloatStateOf(30f) }
            var isUncapped by rememberSaveable { mutableStateOf(false) }
            val frameRate = if (isUncapped) {
                RiveFrameRate.Unbounded
            } else {
                RiveFrameRate.Capped(framesPerSecond)
            }

            Scaffold(containerColor = Color.Black) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when (riveFile) {
                            is Result.Loading -> LoadingIndicator()
                            is Result.Error -> ErrorMessage(riveFile.throwable)
                            is Result.Success -> {
                                Rive(
                                    file = riveFile.value,
                                    fit = Fit.Contain(),
                                    frameRate = frameRate,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeGestures)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isUncapped) {
                                "FPS Cap: Uncapped"
                            } else {
                                String.format(Locale.US, "FPS Cap: %.1f", framesPerSecond)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Slider(
                            value = framesPerSecond,
                            onValueChange = {
                                framesPerSecond = it
                                isUncapped = false
                            },
                            valueRange = 1f..240f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FPS_PRESET_ROWS.forEachIndexed { index, presets ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    presets.forEach { preset ->
                                        Button(
                                            onClick = {
                                                framesPerSecond = preset
                                                isUncapped = false
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(preset.toInt().toString())
                                        }
                                    }
                                    if (index == FPS_PRESET_ROWS.lastIndex) {
                                        Button(
                                            onClick = { isUncapped = true },
                                            modifier = Modifier
                                                .weight(1f)
                                                .semantics { contentDescription = "Uncapped" }
                                        ) {
                                            Text("🚀")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

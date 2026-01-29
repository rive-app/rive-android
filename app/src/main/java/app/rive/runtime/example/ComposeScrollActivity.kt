package app.rive.runtime.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.rive.Fit
import app.rive.Result
import app.rive.Rive
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.RiveLog
import app.rive.RivePointerInputMode
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorker
import android.graphics.Color as AndroidColor

/**
 * Demonstrates the difference between [RivePointerInputMode.Consume] and
 * [RivePointerInputMode.Observe] by nesting Android scrolling and Rive scrolling.
 *
 * When set to [RivePointerInputMode.Consume], the Android scroll region can only be scrolled by
 * dragging the left or right margins outside of the Rive compositions. Scrolling the Rive scrolling
 * region will not trigger Android's scroll behavior.
 *
 * When set to [RivePointerInputMode.Observe], the opposite is true. Dragging anywhere will scroll
 * the Android scrolling region. If that drag also begins on the Rive composition, the Rive
 * scrolling region will also scroll at the same time.
 */
class ComposeScrollActivity : ComponentActivity() {
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
                RiveFileSource.RawRes.from(R.raw.skull_scroll),
                riveWorker
            )
            var consumePointerEvents by remember { mutableStateOf(true) }

            Scaffold(containerColor = Color(0xFF180A06)) { innerPadding ->
                Column(modifier = Modifier.padding(innerPadding)) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .windowInsetsPadding(WindowInsets.safeGestures)
                            .weight(1f)
                    ) {
                        when (riveFile) {
                            is Result.Loading -> LoadingIndicator()
                            is Result.Error -> ErrorMessage(riveFile.throwable)
                            is Result.Success -> {
                                RiveScroll(riveFile.value, consumePointerEvents)
                                RiveScroll(riveFile.value, consumePointerEvents)
                                RiveScroll(riveFile.value, consumePointerEvents)
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
                            text = "Consume pointer events",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = consumePointerEvents,
                            onCheckedChange = { consumePointerEvents = !consumePointerEvents }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RiveScroll(file: RiveFile, consumePointerEvents: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(300.dp)
            .border(1.dp, Color.White)
    ) {
        Rive(
            file,
            fit = Fit.Cover(),
            pointerInputMode = if (consumePointerEvents) {
                RivePointerInputMode.Consume
            } else {
                RivePointerInputMode.Observe
            }
        )
    }
}

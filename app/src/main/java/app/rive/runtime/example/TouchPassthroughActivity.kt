package app.rive.runtime.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Fit

/**
 * Demonstrates how a full‑screen RiveAnimationView can optionally allow touches to "pass through"
 * to Views or Composables drawn underneath it.
 *
 * Note: The [Button] below zeroes out its elevation because, by default, it has built in elevation
 * that puts it above the RiveAnimationView for touch priority despite the rendering order.
 */
class TouchPassthroughActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var clickCount by remember { mutableIntStateOf(0) }
            var passThrough by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {

                // 1 - Background button to test clicks
                Button(
                    onClick = { clickCount++ },
                    elevation = ButtonDefaults.buttonElevation(
                        // Zero out elevation
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        disabledElevation = 0.dp,
                    ),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text(text = "Click Count: $clickCount")
                }

                // 2 - Full‑screen RiveAnimationView over the button
                AndroidView(
                    factory = { context ->
                        RiveAnimationView(context).apply {
                            setRiveResource(R.raw.touchpassthrough, fit = Fit.FILL)
                            touchPassThrough = passThrough
                        }
                    },
                    update = { it.touchPassThrough = passThrough },
                    modifier = Modifier.matchParentSize()
                )

                // 3 - Toggle + status text (drawn above everything)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(vertical = 32.dp, horizontal = 16.dp)
                ) {
                    Text(text = "Touch pass‑through: ${if (passThrough) "On" else "Off"}")
                    Switch(
                        checked = passThrough,
                        onCheckedChange = { passThrough = it },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

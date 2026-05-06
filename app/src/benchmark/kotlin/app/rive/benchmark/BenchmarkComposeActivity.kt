package app.rive.benchmark

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.rive.Fit
import app.rive.Result
import app.rive.Rive
import app.rive.RiveFileSource
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorker
import app.rive.runtime.example.R

class BenchmarkComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BenchmarkComposeScreen()
        }
    }
}

@Composable
private fun BenchmarkComposeScreen() {
    val riveWorker = rememberRiveWorker()
    val riveFileResult = rememberRiveFile(
        RiveFileSource.RawRes.from(R.raw.basketball),
        riveWorker
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (riveFileResult is Result.Success) {
            Rive(
                file = riveFileResult.value,
                modifier = Modifier.fillMaxSize(),
                fit = Fit.Contain()
            )
        }
    }
}

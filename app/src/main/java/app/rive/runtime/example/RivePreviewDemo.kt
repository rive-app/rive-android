package app.rive.runtime.example

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.rive.RawRes
import app.rive.Result
import app.rive.Rive
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorker

/**
 * Demonstrates real Rive rendering inside Android Studio previews.
 *
 * Previews run on the host JVM (layoutlib), where frames render through the desktop Rive
 * library provided by the `:rive-preview` dependency. If the static preview appears blank
 * (file loading is asynchronous), use interactive preview mode.
 */
@Preview(showBackground = true)
@Composable
fun RiveBasketballPreview() {
    val worker = rememberRiveWorker()
    when (val file = rememberRiveFile(RawRes.from(R.raw.basketball), worker)) {
        is Result.Success -> Rive(file.value, modifier = Modifier.size(300.dp))
        else -> {}
    }
}

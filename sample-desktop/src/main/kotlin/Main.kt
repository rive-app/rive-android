package app.rive.sample

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.rive.RenderBackend
import app.rive.Result
import app.rive.Rive
import app.rive.RiveFileSource
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorker

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Rive Desktop Sample") {
        val worker = rememberRiveWorker(renderBackend = RenderBackend.Vulkan)
        val bytes = remember {
            checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream("basketball.riv")) {
                "basketball.riv resource missing"
            }.readBytes()
        }
        when (val file = rememberRiveFile(RiveFileSource.Bytes(bytes), worker)) {
            is Result.Success -> Rive(file.value, modifier = Modifier.fillMaxSize())
            else -> {}
        }
    }
}

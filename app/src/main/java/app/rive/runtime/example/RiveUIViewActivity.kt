package app.rive.runtime.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.rive.ExperimentalRiveComposeAPI
import app.rive.Result
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.RiveLog
import app.rive.RiveUIView
import app.rive.core.CommandQueue
import app.rive.runtime.example.utils.setEdgeToEdgeContent
import kotlinx.coroutines.launch

@OptIn(ExperimentalRiveComposeAPI::class)
class RiveUIViewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RiveLog.logger = RiveLog.LogcatLogger()
        val rive = RiveUIView(this)
        setEdgeToEdgeContent(rive)

        val commandQueue = CommandQueue().also {
            it.withLifecycle(this, "RiveUIViewActivity")

            lifecycleScope.launch {
                it.beginPolling(lifecycle)
            }
        }

        lifecycleScope.launch {
            val riveFile = RiveFile.fromSource(
                RiveFileSource.RawRes(
                    resId = R.raw.basketball,
                    resources = resources
                ), commandQueue
            )

            when (riveFile) {
                is Result.Success -> rive.setRiveFile(riveFile.value)
                is Result.Error -> {}
                is Result.Loading -> {}
            }
        }
    }
}

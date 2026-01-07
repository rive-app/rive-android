package app.rive.runtime.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.rive.ExperimentalRiveComposeAPI
import app.rive.Result
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.RiveLog
import app.rive.RiveView
import app.rive.core.RiveWorker
import app.rive.runtime.example.utils.setEdgeToEdgeContent
import kotlinx.coroutines.launch

@OptIn(ExperimentalRiveComposeAPI::class)
class RiveViewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RiveLog.logger = RiveLog.LogcatLogger()
        val rive = RiveView(this)
        setEdgeToEdgeContent(rive)

        val riveWorker = RiveWorker().also {
            it.withLifecycle(this, "RiveViewActivity")

            lifecycleScope.launch {
                it.beginPolling(lifecycle)
            }
        }

        lifecycleScope.launch {
            val riveFile = RiveFile.fromSource(
                RiveFileSource.RawRes(
                    resId = R.raw.basketball,
                    resources = resources
                ), riveWorker
            )

            when (riveFile) {
                is Result.Success -> rive.setRiveFile(riveFile.value)
                is Result.Error -> {}
                is Result.Loading -> {}
            }
        }
    }
}

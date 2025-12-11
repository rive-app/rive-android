package app.rive.snapshot

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import app.rive.ExperimentalRiveComposeAPI
import app.rive.Result
import app.rive.RiveFileSource
import app.rive.RiveLog
import app.rive.RiveUI
import app.rive.ViewModelSource
import app.rive.rememberArtboard
import app.rive.rememberCommandQueue
import app.rive.rememberRiveFile
import app.rive.rememberStateMachine
import app.rive.rememberViewModelInstance
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.test.R
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import app.rive.runtime.kotlin.core.Alignment as RiveAlignment

/**
 * Activity that renders a Rive file using Compose for instrumentation tests.
 *
 * The rendered bitmap is returned through [resultBitmap] so that tests can assert on the output.
 * The [resultLatch] is used to signal when the bitmap is ready.
 */
@OptIn(ExperimentalRiveComposeAPI::class)
class SnapshotComposeActivity : ComponentActivity(), SnapshotActivityResult {
    companion object {
        /**
         * Creates an Intent to launch this activity with the specified configuration.
         *
         * @param context The context to use for creating the Intent.
         * @param config The configuration for the snapshot activity.
         */
        fun createIntent(
            context: android.content.Context,
            config: SnapshotActivityConfig
        ): Intent = Intent(context, SnapshotComposeActivity::class.java).apply {
            SnapshotActivityConfig.intoIntent(this, config)
        }
    }

    override lateinit var resultBitmap: Bitmap
    override val resultLatch = CountDownLatch(1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        RiveLog.logger = RiveLog.LogcatLogger()
        Rive.init(this)

        val config = SnapshotActivityConfig.fromIntent(intent)

        setContent {
            val context = LocalContext.current

            val commandQueue = rememberCommandQueue()
            val riveFileResult = rememberRiveFile(
                RiveFileSource.RawRes(R.raw.snapshot_test, context.resources),
                commandQueue
            )

            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                when (riveFileResult) {
                    is Result.Loading -> {}
                    is Result.Error -> {}
                    is Result.Success -> {
                        val riveFile = riveFileResult.value

                        // Create an artboard and state machine to manually advance
                        val artboard = rememberArtboard(riveFile, config.artboardName)
                        val stateMachine = rememberStateMachine(artboard)
                        val vmi = rememberViewModelInstance(
                            riveFile,
                            ViewModelSource.DefaultForArtboard(artboard).defaultInstance()
                        )

                        // Handle scenario-specific logic
                        when (config) {
                            is SnapshotActivityConfig.Sweep -> {
                                // Map percentage (0f-1f) to milliseconds (0-1000ms)
                                val advanceTime = (config.percentage * 1000).toLong().milliseconds
                                LaunchedEffect(stateMachine, advanceTime) {
                                    // Advance once by 0 to leave Entry state, then by the specified time
                                    RiveLog.i("SnapshotComposeActivity") {
                                        "Advancing state machine by ${advanceTime}ms (${config.percentage * 100}%)"
                                    }
                                    stateMachine.advance(0.nanoseconds)
                                    stateMachine.advance(advanceTime)
                                }
                            }

                            is SnapshotActivityConfig.DataBind -> {
                                val stringToBind = config.value
                                LaunchedEffect(stateMachine, stringToBind) {
                                    if (stringToBind != SnapshotTest.NO_BINDING) {
                                        RiveLog.i("SnapshotComposeActivity") {
                                            "Binding string value: \"$stringToBind\""
                                        }
                                        vmi.setString("Text", stringToBind)
                                        stateMachine.advance(0.nanoseconds)
                                    } else {
                                        RiveLog.i("SnapshotComposeActivity") {
                                            "Skipping VMI binding (NO_BINDING sentinel)"
                                        }
                                    }
                                }
                            }

                            else -> {
                                // No additional setup needed for other scenarios beyond initial advance
                                stateMachine.advance(0.nanoseconds)
                            }
                        }

                        val dpSize = with(LocalDensity.current) { 100.toDp() }

                        RiveUI(
                            file = riveFile,
                            playing = false,
                            artboard = artboard,
                            stateMachine = stateMachine,
                            viewModelInstance = vmi,
                            fit = Fit.NONE,
                            alignment = RiveAlignment.CENTER,
                            modifier = Modifier.requiredSize(dpSize),
                            onBitmapAvailable = { bitmapFn ->
                                RiveLog.i("SnapshotComposeActivity") { "Bitmap available" }
                                // Get the bitmap and store it
                                val bitmap = bitmapFn()

                                // Store result for test access
                                resultBitmap = bitmap

                                // Signal that result is ready
                                resultLatch.countDown()
                            }
                        )
                    }
                }
            }
        }
    }
}

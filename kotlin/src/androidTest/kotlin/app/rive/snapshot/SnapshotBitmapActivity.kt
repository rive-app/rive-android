package app.rive.snapshot

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.rive.Artboard
import app.rive.ExperimentalRiveComposeAPI
import app.rive.RenderBuffer
import app.rive.Result
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.RiveLog
import app.rive.StateMachine
import app.rive.ViewModelInstance
import app.rive.ViewModelSource
import app.rive.core.CommandQueue
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.test.R
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration.Companion.milliseconds

private const val BITMAP_TAG = "Rive/BitmapSnapshotActivity"

/**
 * Activity that renders a Rive file to an off-screen bitmap for instrumentation tests.
 *
 * The rendered bitmap is returned through [resultBitmap] so that tests can assert on the output.
 * The [resultLatch] is used to signal when the bitmap is ready.
 */
@OptIn(ExperimentalRiveComposeAPI::class)
class SnapshotBitmapActivity : ComponentActivity(), SnapshotActivityResult {
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
        ): Intent = Intent(context, SnapshotBitmapActivity::class.java).apply {
            config.applyToIntent(this)
        }
    }

    override lateinit var resultBitmap: Bitmap
    override val resultLatch = CountDownLatch(1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        RiveLog.logger = RiveLog.LogcatLogger()
        Rive.init(this)

        lifecycleScope.launch {
            val commandQueue = CommandQueue().also { queue ->
                queue.withLifecycle(this@SnapshotBitmapActivity, BITMAP_TAG)
                lifecycleScope.launch {
                    queue.beginPolling(lifecycle)
                }
            }
            val riveFileResult = RiveFile.fromSource(
                RiveFileSource.RawRes(R.raw.snapshot_test, resources),
                commandQueue
            )

            when (riveFileResult) {
                is Result.Loading -> error("Rive file should only be loaded or error.")
                is Result.Success -> {
                    val file = riveFileResult.value
                    renderBitmap(file)
                    resultLatch.countDown()
                    file.close()
                }

                is Result.Error -> {
                    RiveLog.e(BITMAP_TAG) {
                        "Failed to load Rive file: ${riveFileResult.throwable.message ?: "unknown error"}"
                    }
                    resultLatch.countDown()
                }
            }
        }
    }

    private fun renderBitmap(file: RiveFile) {
        val config = SnapshotActivityConfig.fromIntent(intent)

        val (width, height) = 100 to 100
        val fit = when (config) {
            is SnapshotActivityConfig.Layout -> if (config.useLayout) {
                Fit.LAYOUT
            } else {
                Fit.NONE
            }

            else -> Fit.NONE // Default to no layout for other scenarios
        }
        val layoutScale = when (config) {
            is SnapshotActivityConfig.Layout -> config.layoutScale
            else -> 1f // Default of 1 for other scenarios
        }

        RenderBuffer(width, height, file.commandQueue).use { buffer ->
            Artboard.fromFile(file, config.artboardName).use { artboard ->
                StateMachine.fromArtboard(artboard).use { stateMachine ->
                    ViewModelInstance.fromFile(
                        file,
                        ViewModelSource.DefaultForArtboard(artboard).defaultInstance()
                    ).use { vmi ->
                        file.commandQueue.bindViewModelInstance(
                            stateMachine.stateMachineHandle,
                            vmi.instanceHandle
                        )

                        when (config) {
                            is SnapshotActivityConfig.Sweep -> {
                                // Map percentage (0f-1f) to milliseconds (0-1000ms)
                                val advanceTime = (config.percentage * 1000).toLong().milliseconds
                                RiveLog.i("SnapshotComposeActivity") {
                                    "Advancing state machine by ${advanceTime}ms (${config.percentage * 100}%)"
                                }
                                stateMachine.advance(0.milliseconds)
                                stateMachine.advance(advanceTime)
                            }

                            is SnapshotActivityConfig.DataBind -> {
                                val stringToBind = config.value
                                if (stringToBind != SnapshotTest.NO_BINDING) {
                                    RiveLog.i(BITMAP_TAG) {
                                        "Binding string value: \"$stringToBind\""
                                    }
                                    vmi.setString("Text", stringToBind)
                                } else {
                                    RiveLog.i(BITMAP_TAG) {
                                        "Skipping VMI binding (NO_BINDING sentinel)"
                                    }
                                }
                                stateMachine.advance(0.milliseconds)
                            }

                            is SnapshotActivityConfig.Layout -> {
                                if (config.useLayout) {
                                    artboard.resizeArtboard(buffer.surface, config.layoutScale)
                                }
                                stateMachine.advance(0.milliseconds)
                            }
                        }
                        resultBitmap =
                            buffer.snapshot(artboard, stateMachine, fit, scaleFactor = layoutScale)
                                .toBitmap()
                    }
                }
            }
        }
    }
}

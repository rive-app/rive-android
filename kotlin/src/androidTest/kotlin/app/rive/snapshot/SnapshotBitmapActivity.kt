package app.rive.snapshot

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.rive.Artboard
import app.rive.Fit
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.RiveLog
import app.rive.SoftwareRenderBuffer
import app.rive.StateMachine
import app.rive.ViewModelInstance
import app.rive.ViewModelSource
import app.rive.core.RiveWorker
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.test.R
import kotlinx.coroutines.CancellationException
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
            val riveWorker = RiveWorker().also { worker ->
                worker.withLifecycle(this@SnapshotBitmapActivity, BITMAP_TAG)
                lifecycleScope.launch {
                    worker.beginPolling(lifecycle)
                }
            }
            val file = try {
                RiveFile.load(
                    RiveFileSource.RawRes(R.raw.snapshot_test, resources),
                    riveWorker
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                RiveLog.e(BITMAP_TAG, e) {
                    "Failed to load Rive file: ${e.message ?: "unknown error"}"
                }
                resultLatch.countDown()
                return@launch
            }
            file.use {
                renderBitmap(it)
                resultLatch.countDown()
            }
        }
    }

    private suspend fun renderBitmap(file: RiveFile) {
        val config = SnapshotActivityConfig.fromIntent(intent)

        val (width, height) = 100 to 100
        val fit = when (config) {
            is SnapshotActivityConfig.Layout -> if (config.useLayout) {
                Fit.Layout(config.layoutScale)
            } else {
                Fit.None()
            }

            else -> Fit.None() // Default to no layout for other scenarios
        }

        SoftwareRenderBuffer(width, height, file.riveWorker).use { buffer ->
            Artboard.create(file, config.artboardName).use { artboard ->
                StateMachine.create(artboard).use { stateMachine ->
                    ViewModelInstance.create(
                        file,
                        ViewModelSource.DefaultForArtboard(artboard).defaultInstance()
                    ).use { vmi ->
                        file.riveWorker.bindViewModelInstance(
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
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        resultBitmap = buffer.renderInto(
                            bitmap = bitmap,
                            artboard = artboard,
                            stateMachine = stateMachine,
                            fit = fit
                        )
                    }
                }
            }
        }
    }
}

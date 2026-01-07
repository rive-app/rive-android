package app.rive.runtime.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.rive.Artboard
import app.rive.ExperimentalRiveComposeAPI
import app.rive.Fit
import app.rive.RenderBuffer
import app.rive.Result
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.RiveLog
import app.rive.StateMachine
import app.rive.core.RiveWorker
import app.rive.runtime.example.utils.setEdgeToEdgeContent
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "RiveSnapshotActivity"

@OptIn(ExperimentalRiveComposeAPI::class)
class RiveSnapshotActivity : ComponentActivity() {
    private lateinit var snapshotView: SnapshotCanvasView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RiveLog.logger = RiveLog.LogcatLogger()
        snapshotView = SnapshotCanvasView(this)
        setEdgeToEdgeContent(snapshotView)

        val riveWorker = RiveWorker().also {
            it.withLifecycle(this, TAG)
            lifecycleScope.launch {
                it.beginPolling(lifecycle)
            }
        }

        lifecycleScope.launch {
            when (val riveFile =
                RiveFile.fromSource(
                    RiveFileSource.RawRes(R.raw.snapshot_test, resources),
                    riveWorker
                )) {
                is Result.Loading -> Unit
                is Result.Success -> renderSnapshot(riveFile.value)
                is Result.Error -> RiveLog.e(TAG) {
                    "Failed to load Rive file: ${riveFile.throwable.message ?: "unknown error"}"
                }
            }
        }
    }

    private suspend fun renderSnapshot(file: RiveFile) {
        val (width, height) = snapshotView.awaitSize()
        RenderBuffer(width, height, file.riveWorker).use { buffer ->
            Artboard.fromFile(file).use { artboard ->
                StateMachine.fromArtboard(artboard).use { stateMachine ->
                    val targetTime = 500.milliseconds
                    // Advance once by 0 to exit the "Entry" state and apply initial values
                    stateMachine.advance(0.milliseconds)
                    stateMachine.advance(targetTime)

                    val bitmap = buffer.snapshot(
                        artboard = artboard,
                        stateMachine = stateMachine,
                        fit = Fit.Contain(),
                        clearColor = Color.WHITE
                    ).toBitmap()

                    snapshotView.updateBitmap(bitmap)
                }
            }
        }
    }
}

private class SnapshotCanvasView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val drawRect = Rect()
    private var bitmap: Bitmap? = null

    fun updateBitmap(newBitmap: Bitmap) {
        bitmap = newBitmap
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val bmp = bitmap ?: return
        drawRect.set(0, 0, width, height)
        canvas.drawBitmap(bmp, null, drawRect, paint)
    }
}

private suspend fun View.awaitSize(): Pair<Int, Int> =
    suspendCancellableCoroutine { continuation ->
        val availableImmediately = width > 0 && height > 0
        if (availableImmediately) {
            continuation.resume(width to height)
            return@suspendCancellableCoroutine
        }

        val listener = object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int
            ) {
                if (width > 0 && height > 0) {
                    removeOnLayoutChangeListener(this)
                    if (!continuation.isCancelled) {
                        continuation.resume(width to height)
                    }
                }
            }
        }

        addOnLayoutChangeListener(listener)
        continuation.invokeOnCancellation {
            removeOnLayoutChangeListener(listener)
        }
    }

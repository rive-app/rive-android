package app.rive.benchmark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.rive.Artboard
import app.rive.Fit
import app.rive.HardwareRenderBuffer
import app.rive.Result
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.StateMachine
import app.rive.core.RiveWorker
import app.rive.core.traceSection
import app.rive.core.withFrameNanosChoreographer
import app.rive.runtime.example.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

private const val BENCHMARK_CANVAS_TAG = "BenchmarkHardwareBitmapCanvasActivity"

class BenchmarkHardwareBitmapCanvasActivity : ComponentActivity() {
    private lateinit var renderView: BenchmarkHardwareBitmapCanvasView
    private var renderLoopJob: Job? = null
    private var renderSession: BenchmarkRenderSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        renderView = BenchmarkHardwareBitmapCanvasView(this)
        setContentView(renderView)

        val riveWorker = RiveWorker().also { worker ->
            worker.withLifecycle(this, BENCHMARK_CANVAS_TAG)
            lifecycleScope.launch { worker.beginPolling(lifecycle) }
        }

        lifecycleScope.launch {
            when (
                val riveFile = RiveFile.fromSource(
                    RiveFileSource.RawRes(R.raw.basketball, resources),
                    riveWorker
                )
            ) {
                is Result.Success -> startRenderLoop(riveFile.value)
                is Result.Error -> throw IllegalStateException("Failed to load benchmark Rive file", riveFile.throwable)
                is Result.Loading -> Unit
            }
        }
    }

    override fun onDestroy() {
        renderLoopJob?.cancel()
        renderLoopJob = null
        renderSession?.close()
        renderSession = null
        super.onDestroy()
    }

    private fun startRenderLoop(file: RiveFile) {
        val session = try {
            val artboard = Artboard.fromFile(file)
            val stateMachine = StateMachine.fromArtboard(artboard)
            BenchmarkRenderSession(file, artboard, stateMachine)
        } catch (e: Exception) {
            file.close()
            throw IllegalStateException("Failed to initialize benchmark renderer", e)
        }

        renderSession?.close()
        renderSession = session
        renderLoopJob?.cancel()
        renderLoopJob = lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var lastFrameTime = 0L
                session.stateMachine.advance(0.milliseconds)

                while (isActive) {
                    var skipFrame = false
                    val deltaTime = withFrameNanosChoreographer { frameTimeNs ->
                        val delta = if (lastFrameTime == 0L) 0L else frameTimeNs - lastFrameTime
                        lastFrameTime = frameTimeNs
                        delta.nanoseconds
                    }

                    traceSection("Rive/Frame") {
                        traceSection("Rive/Frame/Advance") {
                            session.stateMachine.advance(deltaTime)
                        }

                        val viewWidth = renderView.width
                        val viewHeight = renderView.height
                        if (viewWidth <= 0 || viewHeight <= 0) {
                            skipFrame = true
                            return@traceSection
                        }

                        traceSection("Rive/Frame/Draw") {
                            session.ensureBufferSize(viewWidth, viewHeight)
                            val bitmap = session.snapshotToBitmap(
                                fit = Fit.Contain(),
                                clearColor = Color.TRANSPARENT
                            )
                            if (bitmap != null) {
                                renderView.setBitmap(bitmap)
                            }
                        }
                    }
                    if (skipFrame) continue
                }
            }
        }
    }
}

private class BenchmarkRenderSession(
    private val file: RiveFile,
    val artboard: Artboard,
    val stateMachine: StateMachine
) : AutoCloseable {
    private var buffer: HardwareRenderBuffer? = null

    fun ensureBufferSize(width: Int, height: Int) {
        val currentBuffer = buffer
        if (currentBuffer != null && currentBuffer.width == width && currentBuffer.height == height) {
            return
        }

        currentBuffer?.close()
        buffer = HardwareRenderBuffer(width, height, file.riveWorker)
    }

    fun snapshotToBitmap(
        fit: Fit = Fit.Contain(),
        clearColor: Int = Color.TRANSPARENT
    ): Bitmap? {
        val currentBuffer = buffer ?: return null
        currentBuffer.render(
            artboard = artboard,
            stateMachine = stateMachine,
            fit = fit,
            clearColor = clearColor
        )
        return currentBuffer.consumeLatestBitmap()
    }

    override fun close() {
        buffer?.close()
        buffer = null
        stateMachine.close()
        artboard.close()
        file.close()
    }
}

private class BenchmarkHardwareBitmapCanvasView(context: Context) : View(context) {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val drawRect = Rect()

    private var bitmap: Bitmap? = null

    fun setBitmap(newBitmap: Bitmap) {
        if (bitmap === newBitmap) {
            return
        }
        bitmap = newBitmap
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)

        val currentBitmap = bitmap ?: return
        drawRect.set(0, 0, width, height)
        traceSection("Rive/Frame/Present/DrawBitmap") {
            canvas.drawBitmap(currentBitmap, null, drawRect, bitmapPaint)
        }
    }
}

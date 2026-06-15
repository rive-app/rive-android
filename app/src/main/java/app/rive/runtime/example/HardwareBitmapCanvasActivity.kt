package app.rive.runtime.example

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.rive.Artboard
import app.rive.ExperimentalHardwareBitmapRendering
import app.rive.Result
import app.rive.RiveCanvasSession
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.RiveLog
import app.rive.StateMachine
import app.rive.core.RiveWorker
import app.rive.runtime.example.utils.setEdgeToEdgeContent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private const val TAG = "HardwareBitmapCanvasActivity"
private const val UNSUPPORTED_MESSAGE = "Hardware bitmap rendering requires API 29+."

@OptIn(ExperimentalHardwareBitmapRendering::class)
class HardwareBitmapCanvasActivity : ComponentActivity() {
    private lateinit var renderView: HardwareBitmapCanvasView
    private var playJob: Job? = null
    private var frameAvailableJob: Job? = null
    private var ownedSession: OwnedSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RiveLog.logger = RiveLog.LogcatLogger()

        if (!RiveCanvasSession.isSupported()) {
            setEdgeToEdgeContent(
                TextView(this).apply {
                    gravity = Gravity.CENTER
                    text = UNSUPPORTED_MESSAGE
                    textSize = 18f
                }
            )
            return
        }

        renderView = HardwareBitmapCanvasView(this)
        setEdgeToEdgeContent(renderView)

        val riveWorker = RiveWorker().also { worker ->
            worker.withLifecycle(this, TAG)
            lifecycleScope.launch { worker.beginPolling(lifecycle) }
        }

        lifecycleScope.launch {
            when (
                val riveFile = RiveFile.fromSource(
                    RiveFileSource.RawRes(R.raw.basketball, resources),
                    riveWorker
                )
            ) {
                is Result.Success -> startSession(riveFile.value, riveWorker)
                is Result.Error -> renderView.setError(
                    "Failed to load Rive file: ${riveFile.throwable.message ?: "unknown error"}"
                )

                is Result.Loading -> Unit
            }
        }
    }

    override fun onDestroy() {
        playJob?.cancel()
        playJob = null
        frameAvailableJob?.cancel()
        frameAvailableJob = null
        ownedSession?.close()
        ownedSession = null
        super.onDestroy()
    }

    private fun startSession(file: RiveFile, riveWorker: RiveWorker) {
        val resources = try {
            val artboard = Artboard.fromFile(file)
            val stateMachine = StateMachine.fromArtboard(artboard)
            val session = RiveCanvasSession(
                riveWorker = riveWorker,
                artboard = artboard,
                stateMachine = stateMachine,
            )
            OwnedSession(file, artboard, stateMachine, session)
        } catch (e: Exception) {
            file.close()
            renderView.setError("Failed to initialize renderer: ${e.message ?: "unknown error"}")
            return
        }

        ownedSession?.close()
        ownedSession = resources

        renderView.setSession(resources.session)

        playJob?.cancel()
        frameAvailableJob?.cancel()
        frameAvailableJob = lifecycleScope.launch {
            resources.session.frameAvailable.collect {
                renderView.postInvalidateOnAnimation()
            }
        }
        playJob = lifecycleScope.launch {
            try {
                resources.session.beginPlaying(lifecycle)
            } catch (e: Exception) {
                renderView.setError("Render failed: ${e.message ?: "unknown error"}")
            }
        }
    }

    private data class OwnedSession(
        val file: RiveFile,
        val artboard: Artboard,
        val stateMachine: StateMachine,
        val session: RiveCanvasSession,
    ) : AutoCloseable {
        override fun close() {
            session.close()
            stateMachine.close()
            artboard.close()
            file.close()
        }
    }
}

@OptIn(ExperimentalHardwareBitmapRendering::class)
private class HardwareBitmapCanvasView(context: Context) : View(context) {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        textAlign = Paint.Align.CENTER
    }

    private var session: RiveCanvasSession? = null
    private var errorMessage: String? = null

    fun setSession(newSession: RiveCanvasSession) {
        session = newSession
        errorMessage = null
        if (width > 0 && height > 0) {
            newSession.setRegion(Rect(0, 0, width, height))
        }
        postInvalidateOnAnimation()
    }

    fun setError(message: String) {
        if (errorMessage == message) {
            return
        }
        errorMessage = message
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        session?.setRegion(Rect(0, 0, w, h))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean =
        session?.onTouchEvent(event) ?: super.onTouchEvent(event)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val currentError = errorMessage
        if (currentError != null) {
            canvas.drawColor(Color.BLACK)
            drawCenteredMessage(canvas, currentError)
            return
        }

        val currentSession = session ?: run {
            canvas.drawColor(Color.BLACK)
            drawCenteredMessage(canvas, "Waiting for session...")
            return
        }

        try {
            currentSession.draw(canvas)
        } catch (e: Exception) {
            canvas.drawColor(Color.BLACK)
            drawCenteredMessage(canvas, e.message ?: "Draw failed")
        }
    }

    private fun drawCenteredMessage(canvas: Canvas, message: String) {
        val lines = message.split('\n')
        val lineHeight = textPaint.fontSpacing
        val totalHeight = lineHeight * lines.size
        val startY = (height / 2f) - (totalHeight / 2f) + lineHeight
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, width / 2f, startY + (lineHeight * index), textPaint)
        }
    }
}

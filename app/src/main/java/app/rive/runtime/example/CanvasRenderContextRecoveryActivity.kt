@file:OptIn(ExperimentalHardwareBitmapRendering::class)

package app.rive.runtime.example

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import app.rive.Artboard
import app.rive.ExperimentalHardwareBitmapRendering
import app.rive.ImageAsset
import app.rive.Result
import app.rive.RiveCanvasSession
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.RiveLog
import app.rive.StateMachine
import app.rive.ViewModelInstance
import app.rive.ViewModelSource
import app.rive.core.RiveWorker
import app.rive.runtime.example.databinding.ActivityCanvasRenderContextRecoveryBinding
import app.rive.runtime.example.utils.setEdgeToEdgeContent
import java.lang.AutoCloseable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Recovers from a lost render context while drawing with [RiveCanvasSession]. */
class CanvasRenderContextRecoveryActivity : ComponentActivity() {
    private val viewModel: RenderContextRecoveryViewModel by viewModels()
    private lateinit var binding: ActivityCanvasRenderContextRecoveryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RiveLog.logger = RiveLog.LogcatLogger()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            setEdgeToEdgeContent(
                TextView(this).apply {
                    gravity = Gravity.CENTER
                    text = "Hardware bitmap rendering requires API 29+."
                    textSize = 18f
                }
            )
            return
        }

        binding = ActivityCanvasRenderContextRecoveryBinding.inflate(layoutInflater)
        setEdgeToEdgeContent(binding.root)
        binding.simulateLossButton.setOnClickListener { viewModel.simulateLoss() }

        lifecycleScope.launch { pollWorker() }
        lifecycleScope.launch { showContent() }
    }

    private suspend fun pollWorker() {
        viewModel.workerAndImage.collectLatest { result ->
            val (worker, _) = (result as? Result.Success)?.value ?: return@collectLatest
            worker.beginPolling(lifecycle)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun showContent() {
        // Load everything again on each new worker.
        viewModel.workerAndImage.collectLatest { result ->
            when (result) {
                is Result.Success -> {
                    val (worker, imageBytes) = result.value
                    startSession(worker, imageBytes)
                }

                is Result.Error -> show(result)

                Result.Loading -> show(Result.Loading)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun startSession(worker: RiveWorker, imageBytes: ByteArray) {
        show(Result.Loading)
        val content = try {
            load(worker, imageBytes)
        } catch (ce: CancellationException) {
            // Rethrow so cancellation stops this load.
            throw ce
        } catch (e: Exception) {
            RiveLog.e(TAG, e) { "Failed to load the Rive content" }
            show(Result.Error(e))
            return
        }
        try {
            show(Result.Success(content.session))
            play(content.session)
        } catch (ce: CancellationException) {
            // Rethrow so cancellation stops playing.
            throw ce
        } catch (e: Exception) {
            RiveLog.e(TAG, e) { "Failed to render the Rive content" }
            show(Result.Error(e))
        } finally {
            // Stop drawing, then close everything loaded on the worker, so a replaced worker can
            // shut down.
            binding.canvasView.session = null
            content.close()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun load(worker: RiveWorker, imageBytes: ByteArray): LoadedContent {
        val file = RiveFile.load(RiveFileSource.RawRes(R.raw.image_db_cats, resources), worker)
        var image: ImageAsset? = null
        var artboard: Artboard? = null
        var stateMachine: StateMachine? = null
        var viewModelInstance: ViewModelInstance? = null
        try {
            image = ImageAsset.create(worker, imageBytes)
            artboard = Artboard.create(file)
            stateMachine = StateMachine.create(artboard)
            viewModelInstance = ViewModelInstance.create(
                file,
                ViewModelSource.DefaultForArtboard(artboard).defaultInstance(),
            )
            viewModelInstance.setImage("property of Card/Image property", image)
            val session = RiveCanvasSession(
                context = this,
                riveWorker = worker,
                artboard = artboard,
                stateMachine = stateMachine,
                viewModelInstance = viewModelInstance,
            )
            return LoadedContent(file, image, artboard, stateMachine, viewModelInstance, session)
        } catch (t: Throwable) {
            // Close what was created before the failure, in reverse creation order.
            viewModelInstance?.close()
            stateMachine?.close()
            artboard?.close()
            image?.close()
            file.close()
            throw t
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun play(session: RiveCanvasSession): Nothing = coroutineScope {
        // Resize from this coroutine, so a failed resize reaches the caller like a render failure.
        val regions = binding.canvasView.region.filterNotNull()
        // beginPlaying() expects a region to be set first.
        session.setRegion(regions.first())
        launch { regions.collect { session.setRegion(it) } }
        launch { session.frameAvailable.collect { binding.canvasView.postInvalidateOnAnimation() } }
        session.beginPlaying(lifecycle)
        awaitCancellation()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun show(content: Result<RiveCanvasSession>) {
        binding.message.visibility = if (content is Result.Success) View.GONE else View.VISIBLE
        binding.message.text = when (content) {
            Result.Loading -> "Loading"
            is Result.Error -> content.throwable.message
            is Result.Success -> null
        }
        binding.canvasView.session = (content as? Result.Success)?.value
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private class LoadedContent(
        private val file: RiveFile,
        private val image: ImageAsset,
        private val artboard: Artboard,
        private val stateMachine: StateMachine,
        private val viewModelInstance: ViewModelInstance,
        val session: RiveCanvasSession,
    ) : AutoCloseable {
        override fun close() {
            session.close()
            viewModelInstance.close()
            stateMachine.close()
            artboard.close()
            image.close()
            file.close()
        }
    }

    companion object {
        private const val TAG = "CanvasRenderContextRecovery"
    }
}

/** Draws a [RiveCanvasSession], sends touches to it, and reports the [region] it should fill. */
@RequiresApi(Build.VERSION_CODES.Q)
class CanvasSessionView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val _region = MutableStateFlow<Rect?>(null)
    val region: StateFlow<Rect?> = _region.asStateFlow()

    var session: RiveCanvasSession? = null
        set(value) {
            field = value
            postInvalidateOnAnimation()
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        _region.value = Rect(0, 0, w, h)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean =
        session?.onTouchEvent(event) ?: super.onTouchEvent(event)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        session?.draw(canvas)
    }
}

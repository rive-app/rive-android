package app.rive.runtime.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rive.RenderContextStatus
import app.rive.Result
import app.rive.RiveInitializationException
import app.rive.RiveLog
import app.rive.core.RiveWorker
import java.io.IOException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Replaces the worker after a render context loss, and downloads the card's image for each one. */
class RenderContextRecoveryViewModel : ViewModel() {
    private val currentWorker = MutableStateFlow(createWorker())

    private val _workerAndImage = MutableStateFlow<Result<WorkerAndImage>>(Result.Loading)
    val workerAndImage: StateFlow<Result<WorkerAndImage>> = _workerAndImage.asStateFlow()

    init {
        // Download the image again for each new worker.
        viewModelScope.launch {
            currentWorker.collectLatest { result ->
                val worker = when (result) {
                    is Result.Success -> result.value

                    is Result.Error -> {
                        _workerAndImage.value = result
                        return@collectLatest
                    }

                    Result.Loading -> return@collectLatest
                }
                _workerAndImage.value = Result.Loading
                _workerAndImage.value = try {
                    Result.Success(WorkerAndImage(worker, downloadImage()))
                } catch (e: IOException) {
                    RiveLog.e(TAG, e) { "Failed to download the image" }
                    Result.Error(e)
                }
            }
        }

        // When the worker loses its render context, replace it with a new one. A lost context
        // can't be restored, so the screen loads everything again on the new worker.
        viewModelScope.launch {
            currentWorker.collectLatest { result ->
                val worker = (result as? Result.Success)?.value ?: return@collectLatest
                worker.renderContextStatus.first { it == RenderContextStatus.Lost }
                replaceWorker()
            }
        }
    }

    /** A loss can't be triggered on demand, so this skips detection and runs the same recovery. */
    fun simulateLoss() = replaceWorker()

    override fun onCleared() {
        (currentWorker.value as? Result.Success)?.value
            ?.release(TAG, "View model cleared")
    }

    private fun replaceWorker() {
        val oldWorker = (currentWorker.value as? Result.Success)?.value
        currentWorker.value = createWorker()
        // The old worker shuts down once the screen closes what it loaded on it.
        oldWorker?.release(TAG, "Replaced")
    }

    private fun createWorker(): Result<RiveWorker> = try {
        Result.Success(RiveWorker())
    } catch (e: RiveInitializationException) {
        RiveLog.e(TAG, e) { "Failed to create a Rive worker" }
        Result.Error(e)
    }

    private suspend fun downloadImage(): ByteArray = withContext(Dispatchers.IO) {
        // A real app would use its HTTP client here. The timeouts keep a stalled download from
        // blocking the load on the next worker.
        URL(IMAGE_URL).openConnection().run {
            connectTimeout = DOWNLOAD_TIMEOUT_MS
            readTimeout = DOWNLOAD_TIMEOUT_MS
            getInputStream().use { it.readBytes() }
        }
    }

    companion object {
        private const val TAG = "RenderContextRecovery"
        private const val IMAGE_URL = "https://cdn.rive.app/rive_logo_dark_bg.png"
        private const val DOWNLOAD_TIMEOUT_MS = 10_000
    }
}

data class WorkerAndImage(val worker: RiveWorker, val imageBytes: ByteArray)

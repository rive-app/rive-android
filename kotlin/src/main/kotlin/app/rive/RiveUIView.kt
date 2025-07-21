package app.rive

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Fit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Note: This class is more experimental than others. It is not recommended for use at this time.
 */
@ExperimentalRiveComposeAPI()
class RiveUIView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {
    var fileSpec by mutableStateOf<RiveFileSource?>(null)
    var artboardName by mutableStateOf<String?>(null)
    var stateMachineName by mutableStateOf<String?>(null)
    var fit by mutableStateOf(Fit.CONTAIN)
    var alignment by mutableStateOf(Alignment.CENTER)

    private val _fileFlow = MutableStateFlow<Result<RiveFile>>(Result.Loading)
    val fileFlow: StateFlow<Result<RiveFile>> get() = _fileFlow

    var loadingContent: (@Composable () -> Unit)? = null
    var errorContent: (@Composable (Throwable) -> Unit)? = null

    @OptIn(ExperimentalRiveComposeAPI::class)
    private val compose = ComposeView(context).apply {
        // Dispose when this RiveView is detached
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            val spec = fileSpec
            if (spec != null) {
                val fileState = rememberRiveFile(spec)
                LaunchedEffect(fileState.value) {
                    _fileFlow.value = fileState.value
                }

                when (val r = fileState.value) {
                    is Result.Loading -> loadingContent?.invoke()

                    is Result.Error -> errorContent?.invoke(Throwable()) // TODO: Pass error

                    is Result.Success -> {
                        val artboard = rememberArtboard(r.value, artboardName)

                        RiveUI(
                            file = r.value,
                            artboard = artboard,
                            stateMachineName = stateMachineName,
                            fit = fit,
                            alignment = alignment,
                        )
                    }
                }
            }
        }
    }

    init {
        addView(compose, LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }
}
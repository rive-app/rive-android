package app.rive.runtime.example

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.rive.Fit
import app.rive.RawRes as RiveRawRes
import app.rive.Result
import app.rive.Rive
import app.rive.ViewModelInstanceSource
import app.rive.ViewModelSource
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorker
import app.rive.rememberViewModelInstance

/**
 * Real Rive rendering inside Android Studio previews.
 *
 * Previews run on the host JVM (layoutlib), where frames render through the desktop Rive
 * library provided by the `:rive-preview` dependency. If a static preview appears blank
 * (file loading is asynchronous), use interactive preview mode.
 *
 * Preview composables should reserve their final size while the file loads (see the Box in
 * [RivePreviewBox]) or declare explicit `widthDp`/`heightDp` on `@Preview`; otherwise
 * shrink-to-content previews are measured while empty and Android Studio stretches the
 * late-arriving render to the wrong aspect ratio in interactive mode.
 */
@Composable
private fun RivePreviewBox(
    @RawRes resId: Int,
    fit: Fit = Fit.Contain(),
    size: Dp = 250.dp,
    viewModelSource: ViewModelInstanceSource? = null,
) {
    val worker = rememberRiveWorker()
    // The Box keeps the preview at its final size from the first composition. File loading is
    // asynchronous, and default-size previews are measured by layoutlib's shrink-to-content mode
    // before the file resolves; composing nothing during that window makes Android Studio size
    // the interactive viewport from empty content and stretch the late-arriving render.
    Box(Modifier.size(size)) {
        when (val file = rememberRiveFile(RiveRawRes.from(resId), worker)) {
            is Result.Success -> Rive(
                file.value,
                modifier = Modifier.fillMaxSize(),
                fit = fit,
                viewModelInstance = viewModelSource?.let {
                    rememberViewModelInstance(file.value, it)
                },
            )

            else -> {}
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RiveBasketballPreview() {
    RivePreviewBox(R.raw.basketball)
}

/** Character rig with skeletal animation. */
@Preview(showBackground = true)
@Composable
fun RiveMartyPreview() {
    RivePreviewBox(R.raw.marty)
}

/** Vector text rendering with embedded fonts. */
@Preview(showBackground = true)
@Composable
fun RiveTextPreview() {
    RivePreviewBox(R.raw.hello_world_text)
}

/** Responsive Rive layouts resize the artboard to the surface via [Fit.Layout]. */
@Preview(showBackground = true, widthDp = 420, heightDp = 260)
@Composable
fun RiveLayoutPreview() {
    RivePreviewBox(R.raw.layouts_demo, fit = Fit.Layout(), size = 400.dp)
}

/**
 * State-machine driven rating meter. Tapping stars works because the artboard's tap listeners
 * write to view-model properties, which requires the view model instance to be bound.
 */
@Preview(showBackground = true)
@Composable
fun RiveRatingPreview() {
    RivePreviewBox(
        R.raw.rating_animation_all,
        viewModelSource = ViewModelSource.Named("Rating Animation").defaultInstance(),
    )
}

/**
 * The same file under different fit modes: the artboard is wider than the square box, so
 * [Fit.Cover] crops the sides while [Fit.Contain] letterboxes top and bottom.
 */
@Preview(showBackground = true)
@Composable
fun RiveFitCoverPreview() {
    RivePreviewBox(R.raw.skills, fit = Fit.Cover(), size = 200.dp)
}

@Preview(showBackground = true)
@Composable
fun RiveFitContainPreview() {
    RivePreviewBox(R.raw.skills, fit = Fit.Contain(), size = 200.dp)
}

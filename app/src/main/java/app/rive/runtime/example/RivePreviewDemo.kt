package app.rive.runtime.example

import androidx.annotation.RawRes
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
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorker

/**
 * Real Rive rendering inside Android Studio previews.
 *
 * Previews run on the host JVM (layoutlib), where frames render through the desktop Rive
 * library provided by the `:rive-preview` dependency. If a static preview appears blank
 * (file loading is asynchronous), use interactive preview mode.
 */
@Composable
private fun RivePreviewBox(
    @RawRes resId: Int,
    fit: Fit = Fit.Contain(),
    size: Dp = 250.dp,
) {
    val worker = rememberRiveWorker()
    when (val file = rememberRiveFile(RiveRawRes.from(resId), worker)) {
        is Result.Success -> Rive(
            file.value,
            modifier = Modifier.size(size),
            fit = fit,
        )

        else -> {}
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

/** State-machine driven rating meter. */
@Preview(showBackground = true)
@Composable
fun RiveRatingPreview() {
    RivePreviewBox(R.raw.rating_animation_all)
}

/** The same file under different fit modes. */
@Preview(showBackground = true)
@Composable
fun RiveFitCoverPreview() {
    RivePreviewBox(R.raw.off_road_car_blog, fit = Fit.Cover(), size = 200.dp)
}

@Preview(showBackground = true)
@Composable
fun RiveFitContainPreview() {
    RivePreviewBox(R.raw.off_road_car_blog, fit = Fit.Contain(), size = 200.dp)
}

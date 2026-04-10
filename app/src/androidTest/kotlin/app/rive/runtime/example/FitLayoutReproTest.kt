package app.rive.runtime.example

import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.File
import app.rive.runtime.kotlin.core.Fit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

/**
 * Reproducer for the Fit.LAYOUT race condition:
 *
 * When setRiveFile(fit = Fit.LAYOUT) is called on a 0×0 view (before the
 * first measure/layout pass), the artboard sometimes stays at its intrinsic
 * size instead of resizing to match the view.
 *
 * This happens because setupScene() nulls activeArtboard then sets
 * requireArtboardResize=true, and the render thread can consume that flag
 * while activeArtboard is still null.
 */
@RunWith(AndroidJUnit4::class)
class FitLayoutReproTest {

    /**
     * Programmatically add a RiveAnimationView and call setRiveFile with
     * Fit.LAYOUT before the view has been measured. Verify the artboard
     * resizes to the view size (not stuck at intrinsic size).
     */
    @Test
    fun fitLayoutResizesWhenSetBeforeMeasure() {
        val activityScenario = ActivityScenario.launch(EmptyActivity::class.java)
        lateinit var riveView: RiveAnimationView
        val laidOutLatch = CountDownLatch(1)

        val viewWidthPx = 800
        val viewHeightPx = 400

        activityScenario.onActivity { activity ->
            val riveBytes = activity.resources
                .openRawResource(R.raw.layout_test)
                .readBytes()
            val riveFile = File(riveBytes)

            riveView = RiveAnimationView(activity)
            riveView.layoutParams = FrameLayout.LayoutParams(viewWidthPx, viewHeightPx)

            // Add to container — triggers measure/layout asynchronously
            activity.container.addView(riveView)

            // Call setRiveFile immediately, before the view has been measured (still 0×0)
            riveView.setRiveFile(
                riveFile,
                fit = Fit.LAYOUT,
                autoplay = true,
            )

            riveView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                laidOutLatch.countDown()
            }
        }

        // Wait for the view to be laid out
        assertTrue(
            "Timed out waiting for RiveAnimationView layout",
            laidOutLatch.await(3, TimeUnit.SECONDS)
        )

        // Give the render thread a few frames to process the resize
        Thread.sleep(500)

        activityScenario.onActivity {
            val artboard = riveView.controller.activeArtboard
            assertNotNull("activeArtboard should not be null", artboard)

            val density = riveView.resources.displayMetrics.density
            val expectedWidth = viewWidthPx / density
            val expectedHeight = viewHeightPx / density

            // The artboard should have been resized to match the view (in dp).
            // If the bug is present, the artboard stays at intrinsic size (e.g. 500×500 for layout_test).
            assertEquals(
                "Artboard width should match view width / density",
                expectedWidth,
                artboard!!.width,
                1.0f
            )
            assertEquals(
                "Artboard height should match view height / density",
                expectedHeight,
                artboard.height,
                1.0f
            )
        }

        activityScenario.close()
    }

    /**
     * Run the same test multiple times to catch the intermittent nature.
     * The race condition reportedly has ~50% repro rate per process restart.
     * Within the same process the outcome is usually consistent, but running
     * multiple iterations increases confidence.
     */
    @Test
    fun fitLayoutResizesRepeated() {
        repeat(5) { iteration ->
            val activityScenario = ActivityScenario.launch(EmptyActivity::class.java)
            lateinit var riveView: RiveAnimationView
            val laidOutLatch = CountDownLatch(1)

            val viewWidthPx = 800
            val viewHeightPx = 400

            activityScenario.onActivity { activity ->
                val riveBytes = activity.resources
                    .openRawResource(R.raw.layout_test)
                    .readBytes()
                val riveFile = File(riveBytes)

                riveView = RiveAnimationView(activity)
                riveView.layoutParams = FrameLayout.LayoutParams(viewWidthPx, viewHeightPx)
                activity.container.addView(riveView)

                riveView.setRiveFile(
                    riveFile,
                    fit = Fit.LAYOUT,
                    autoplay = true,
                )

                riveView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    laidOutLatch.countDown()
                }
            }

            assertTrue(
                "Iteration $iteration: timed out waiting for layout",
                laidOutLatch.await(3, TimeUnit.SECONDS)
            )
            Thread.sleep(500)

            activityScenario.onActivity {
                val artboard = riveView.controller.activeArtboard
                assertNotNull("Iteration $iteration: artboard null", artboard)

                val density = riveView.resources.displayMetrics.density
                val expectedWidth = viewWidthPx / density
                val expectedHeight = viewHeightPx / density

                assertEquals(
                    "Iteration $iteration: artboard width mismatch",
                    expectedWidth,
                    artboard!!.width,
                    1.0f
                )
                assertEquals(
                    "Iteration $iteration: artboard height mismatch",
                    expectedHeight,
                    artboard.height,
                    1.0f
                )
            }

            activityScenario.close()
        }
    }
}

package app.rive.snapshot

import android.content.Context
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.platform.app.InstrumentationRegistry
import app.rive.RiveLog
import com.dropbox.dropshots.Dropshots
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.TimeUnit
import kotlin.test.Ignore
import kotlin.test.assertTrue

/**
 * ℹ️ Currently disabled until these tests can be integrated with the existing golden script system.
 *
 * Parameterized test that runs snapshot tests against both [SnapshotBitmapActivity] and
 * [SnapshotComposeActivity], which share a common [SnapshotActivityResult] interface.
 *
 * The Bitmap tests are rendered to an off-screen EGL PBuffer, and so has fewer layers of
 * abstraction. The Compose tests use the RiveUI Composable, ensuring that the Compose integration
 * renders correctly. Despite the different approaches, both paths should produce identical output
 * for the same Rive file and configuration.
 *
 * This test class is parameterized by activity type. Individual test methods handle their own
 * scenario-specific configuration using [SnapshotActivityConfig].
 *
 * Because these tests are relatively expensive, they are not meant to be exhaustive. Rather, they
 * should smoke test key scenarios that can catch regressions in rendering or integration.
 *
 * Snapshots are generated with Dropshots through Gradle with:
 *
 * `./gradlew :kotlin:recordDebugAndroidTestScreenshots \
 * -Pandroid.testInstrumentationRunnerArguments.class=app.rive.snapshot.SnapshotTest`.
 *
 * Screenshots are stored in `androidTest/screenshots/[bitmap|compose]/`.
 */
@RunWith(Parameterized::class)
@Ignore
class SnapshotTest(
    private val activityType: ActivityType
) {
    enum class ActivityType {
        BITMAP,
        COMPOSE
    }

    companion object {
        private const val RIVE_LOAD_TIMEOUT_S = 5L

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<Array<Any>> {
            return ActivityType.entries.map { arrayOf(it) }
        }

        const val NO_BINDING = "NO_BIND"
    }

    @get:Rule
    val dropshots = Dropshots()

    @Test
    fun testSweep() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val sweepPercentages = listOf(0f, 0.25f, 0.5f, 1f)
        sweepPercentages.forEach { percentage ->
            val config = SnapshotActivityConfig.Sweep(percentage)
            val testName = "sweep${(percentage * 100).toInt()}Percent"

            RiveLog.i("SnapshotTest") {
                "Testing ${activityType.name.lowercase()} sweep at ${percentage * 100}% (${percentage * 1000}ms)"
            }

            runSnapshotTest(context, config, testName)
        }
    }

    @Test
    fun testDataBind() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val dataBindValues = listOf("Hello", "", NO_BINDING)
        dataBindValues.forEach { value ->
            val config = SnapshotActivityConfig.DataBind(value)
            val caseName = when (value) {
                "" -> "Empty"
                NO_BINDING -> "NoBinding"
                else -> value
            }
            val testName = "dataBind$caseName"

            RiveLog.i("SnapshotTest") {
                "Testing ${activityType.name.lowercase()} data bind with value: $value"
            }

            runSnapshotTest(context, config, testName)
        }
    }

    /**
     * Common test run for a snapshot test with the given [SnapshotActivityConfig] and compares the
     * resulting bitmap against the stored snapshot using Dropshots.
     *
     * @param context The context to use for creating the intent.
     * @param config The snapshot activity configuration to use.
     * @param testName The name of the test case for snapshot identification.
     */
    private fun runSnapshotTest(
        context: Context,
        config: SnapshotActivityConfig,
        testName: String
    ) {
        val scenario = when (activityType) {
            ActivityType.BITMAP -> {
                val intent = SnapshotBitmapActivity.createIntent(context, config)
                launch<SnapshotBitmapActivity>(intent)
            }

            ActivityType.COMPOSE -> {
                val intent = SnapshotComposeActivity.createIntent(context, config)
                launch<SnapshotComposeActivity>(intent)
            }
        }

        // Wait for the result - both activities implement the same interface
        lateinit var activity: SnapshotActivityResult
        scenario.onActivity { activity = it as SnapshotActivityResult }

        val bitmapReady = activity.resultLatch.await(
            RIVE_LOAD_TIMEOUT_S,
            TimeUnit.SECONDS
        )
        assertTrue(bitmapReady, "Timed out waiting for the bitmap result.")

        val resultBitmap = activity.resultBitmap
        val snapshotFolder = when (activityType) {
            ActivityType.BITMAP -> "bitmap"
            ActivityType.COMPOSE -> "compose"
        }

        RiveLog.i("SnapshotTest") { "Asserting snapshot for $testName in $snapshotFolder folder" }
        dropshots.assertSnapshot(
            resultBitmap,
            name = testName,
            filePath = snapshotFolder
        )
    }
}

package app.rive

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.core.DefaultViewModelInfo
import app.rive.runtime.kotlin.test.R
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith

private const val IMAGE_ASSET_TYPE_KEY = 105
private const val FONT_ASSET_TYPE_KEY = 141

@RunWith(AndroidJUnit4::class)
class RiveFileTest : RiveAndroidTest() {
    /** Verifies that native file asset metadata includes exact global registration keys. */
    @Test
    fun getFileAssets_returnsRegistrationKeys() = runBlocking {
        RiveFile.load(
            RiveFileSource.RawRes(R.raw.asset_load_check, context.resources),
            riveWorker,
        ).use { file ->
            val assets = file.getFileAssets()

            assertTrue(assets.isNotEmpty())
            assertContains(assets.map(RiveFileAsset::typeKey), IMAGE_ASSET_TYPE_KEY)
            assertContains(assets.map(RiveFileAsset::typeKey), FONT_ASSET_TYPE_KEY)
            assets.forEach { asset ->
                val nameWithoutExtension = asset.name.substringBeforeLast(
                    delimiter = ".",
                    missingDelimiterValue = asset.name,
                )
                assertEquals(
                    "$nameWithoutExtension-${asset.assetId}",
                    asset.registrationKey,
                )
            }
        }
    }

    /** Verifies that an artboard without view models gets null through JNI and logs no error. */
    @Test
    fun getDefaultViewModelInfoOrNull_withoutViewModels_returnsNull() = runBlocking {
        val errorLogs = mutableListOf<String>()
        val previousLogger = RiveLog.logger
        RiveLog.logger = object : RiveLog.Logger {
            override fun e(tag: String, t: Throwable?, msg: () -> String) {
                synchronized(errorLogs) { errorLogs.add(msg()) }
            }
        }
        try {
            RiveFile.load(
                RiveFileSource.RawRes(R.raw.no_db, context.resources),
                riveWorker,
            ).use { file ->
                Artboard.create(file).use { artboard ->
                    assertNull(file.getDefaultViewModelInfoOrNull(artboard))
                }
            }
        } finally {
            RiveLog.logger = previousLogger
        }
        synchronized(errorLogs) { assertTrue(errorLogs.isEmpty(), "Error logs: $errorLogs") }
    }

    /** Verifies that an artboard with a default view model still gets its info through JNI. */
    @Test
    fun getDefaultViewModelInfoOrNull_withDefaultViewModel_returnsInfo() = runBlocking<Unit> {
        RiveFile.load(
            RiveFileSource.RawRes(R.raw.data_bind_test_impl, context.resources),
            riveWorker,
        ).use { file ->
            Artboard.create(file).use { artboard ->
                assertEquals(
                    DefaultViewModelInfo("Test All", "Test Default"),
                    file.getDefaultViewModelInfoOrNull(artboard),
                )
            }
        }
    }
}

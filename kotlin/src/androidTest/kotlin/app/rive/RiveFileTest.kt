package app.rive

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.test.R
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
}

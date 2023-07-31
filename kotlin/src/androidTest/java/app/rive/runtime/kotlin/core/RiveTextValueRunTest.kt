package app.rive.runtime.kotlin.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.core.errors.TextValueRunException
import app.rive.runtime.kotlin.test.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class RiveTextValueRunTest {
    private val testUtils = TestUtils()
    private val appContext = testUtils.context
    private lateinit var file: File

    @Before
    fun init() {
        file = File(
            appContext.resources.openRawResource(R.raw.hello_world_text).readBytes()
        )
    }

    @Test
    fun read_and_update_text_run() {
        val textRun = file.firstArtboard.textRun("name")
        assertEquals("world", textRun.text)
        var updateValue = "username"
        textRun.text = updateValue
        assertEquals(updateValue, textRun.text)
    }

    @Test(expected = TextValueRunException::class)
    fun read_non_existing_text_run() {
        file.firstArtboard.textRun("wrong-name")
    }
}
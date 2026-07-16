package app.rive

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.runtime.kotlin.core.NativeStringTestHelper
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class JniStringTest : RiveAndroidTest() {
    /** Verifies that standard four-byte UTF-8 becomes a Java surrogate pair. */
    @Test
    fun makeJString_preservesSupplementaryUnicodeCharacter() {
        assertEquals(
            "Deleting EGLThreadState! 🧨",
            NativeStringTestHelper.cppMakeEmojiString(),
        )
    }

    /** Verifies that the length-aware native overload does not truncate at a null byte. */
    @Test
    fun makeJString_preservesEmbeddedNullCharacter() {
        assertEquals(
            "Rive\u0000Android",
            NativeStringTestHelper.cppMakeEmbeddedNullString(),
        )
    }

    /** Verifies that both directions agree on standard UTF-8. */
    @Test
    fun nativeStringConversion_roundTripsUnicodeAndEmbeddedNull() {
        val expected = "Rïve 🧨\u0000Android"

        assertEquals(expected, NativeStringTestHelper.cppRoundTripString(expected))
    }
}

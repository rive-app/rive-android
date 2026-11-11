package app.rive.runtime.kotlin.core

import android.graphics.BitmapFactory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify

class ImageDecoderUnitTest : FunSpec({
    beforeTest {
        mockkStatic(BitmapFactory::class)
        mockkObject(ImageDecoder)
        every { ImageDecoder.decodeOptions() } returns mockk(relaxed = true)
    }

    afterTest {
        unmockkObject(ImageDecoder)
        unmockkStatic(BitmapFactory::class)
    }

    test("decodeToRGBA returns null when decoding runs out of memory") {
        every {
            BitmapFactory.decodeByteArray(any(), any(), any(), any())
        } throws OutOfMemoryError("Failed to allocate")

        ImageDecoder.decodeToRGBA(byteArrayOf(1, 2, 3)).shouldBeNull()
        verify(exactly = 1) { BitmapFactory.decodeByteArray(any(), any(), any(), any()) }
    }
})

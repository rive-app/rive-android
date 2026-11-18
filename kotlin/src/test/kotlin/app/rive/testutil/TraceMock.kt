package app.rive.testutil

import android.os.Trace
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic

/**
 * Stubs Android trace sections for JVM tests that run Compose without an Android host.
 * Installs the static mock before each test and removes it after each test.
 */
internal fun FunSpec.installTraceMock() {
    beforeTest {
        mockkStatic(Trace::class)
        every { Trace.beginSection(any()) } just runs
        every { Trace.endSection() } just runs
    }
    afterTest { unmockkStatic(Trace::class) }
}

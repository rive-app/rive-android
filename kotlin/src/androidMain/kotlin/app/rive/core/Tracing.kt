package app.rive.core

import android.os.Trace

/**
 * Runs [block] inside an Android trace section.
 *
 * `try/finally` is required so [Trace.endSection] is always called, even when [block] throws.
 * Without this, begin/end calls can become unbalanced and later work appears in the wrong section.
 */
inline fun <T> traceSection(sectionName: String, block: () -> T): T {
    Trace.beginSection(sectionName)
    return try {
        block()
    } finally {
        Trace.endSection()
    }
}

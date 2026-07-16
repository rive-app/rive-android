package app.rive.core

/** Begins a platform trace section. Balanced by [traceEnd]. */
@PublishedApi
internal expect fun traceBegin(sectionName: String)

/** Ends the most recent platform trace section started with [traceBegin]. */
@PublishedApi
internal expect fun traceEnd()

/**
 * Runs [block] inside a platform trace section.
 *
 * `try/finally` is required so [traceEnd] is always called, even when [block] throws. Without
 * this, begin/end calls can become unbalanced and later work appears in the wrong section.
 */
inline fun <T> traceSection(sectionName: String, block: () -> T): T {
    traceBegin(sectionName)
    return try {
        block()
    } finally {
        traceEnd()
    }
}

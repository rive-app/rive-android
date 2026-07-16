package app.rive.core

import android.os.Trace

@PublishedApi
internal actual fun traceBegin(sectionName: String) = Trace.beginSection(sectionName)

@PublishedApi
internal actual fun traceEnd() = Trace.endSection()

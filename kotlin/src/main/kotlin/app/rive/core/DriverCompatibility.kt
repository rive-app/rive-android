package app.rive.core

import android.os.Build
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Identifies FCNT firmware affected or conservatively covered by the IMGeglMakeCurrent workaround.
 * See https://github.com/rive-app/rive-android/issues/468.
 *
 * A402FC/V54RS51B reproduces the crash and reports security patch 2026-08-01. The other model/build
 * pairs were reported by customers. Applying that patch threshold to all four models is a
 * conservative policy, not confirmation of their patch levels or subsequent driver versions.
 * Keep protection across OTAs until fixed firmware is verified. Exact builds remain protected
 * even with missing, malformed, or older patch metadata. Do not order build IDs as versions.
 *
 * @param manufacturer Device manufacturer.
 * @param model Device model associated with the carrier firmware.
 * @param buildId Vendor firmware build ID, not the incremental version or full fingerprint.
 * @param securityPatch Security patch level in YYYY-MM-DD format, unavailable before API 23.
 * @return Whether the Rive OpenGL renderer is prohibited by the compatibility policy.
 */
internal fun hasKnownOpenGLDriverIssue(
    manufacturer: String? = Build.MANUFACTURER,
    model: String? = Build.MODEL,
    buildId: String? = Build.ID,
    securityPatch: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Build.VERSION.SECURITY_PATCH
    } else {
        null
    },
): Boolean {
    if (!manufacturer.equals("FCNT", ignoreCase = true)) return false
    val affectedBuild = when (model) {
        "A402FC" -> "V54RS51B"
        "FCG02" -> "V54RK51B"
        "F-52E" -> "V54RD51B"
        "M07" -> "V54RM51B"
        else -> return false
    }
    return buildId == affectedBuild || isAffectedSecurityPatch(securityPatch)
}

/**
 * Validates a security patch date before comparing it with the first affected patch level.
 *
 * @param securityPatch Reported patch date, which may be missing or malformed.
 * @return Whether the date is valid and at least 2026-08-01.
 */
private fun isAffectedSecurityPatch(securityPatch: String?): Boolean {
    if (securityPatch == null || !Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(securityPatch)) {
        return false
    }
    // Use java.text for API 21 compatibility. Each parser is local because it is not thread-safe.
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
    val position = ParsePosition(0)
    if (parser.parse(securityPatch, position) == null || position.index != securityPatch.length) {
        return false
    }
    // Fixed-width, validated ISO dates sort chronologically without depending on the timezone.
    return securityPatch >= "2026-08-01"
}

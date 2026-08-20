package app.rive

import androidx.annotation.Keep

/**
 * Metadata describing a file asset exported in a [RiveFile].
 *
 * @param name The authored asset name stored in the Rive file.
 * @param registrationKey The exact key used to register a replacement [Asset] with the owning Rive
 *    worker.
 * @param assetId The unsigned 32-bit asset identifier stored in the Rive file, represented as a
 *    [Long] for Java interoperability.
 * @param cdnUuid The CDN UUID stored for a hosted asset, or an empty string when absent.
 * @param cdnBaseUrl The CDN base URL stored in the Rive file.
 * @param fileExtension The asset's file extension without a leading dot.
 * @param typeKey The Rive core type key identifying the concrete file asset type.
 */
@Keep // Constructed from JNI.
data class RiveFileAsset(
    val name: String,
    val registrationKey: String,
    val assetId: Long,
    val cdnUuid: String,
    val cdnBaseUrl: String,
    val fileExtension: String,
    val typeKey: Int,
)

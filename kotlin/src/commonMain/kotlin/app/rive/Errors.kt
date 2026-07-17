package app.rive

import kotlin.jvm.JvmOverloads

// @JvmOverloads generates the (String) constructor that native code requires:
// C++ raises these via JNI ThrowNew, which only looks up `<init>(String)`.

/** Exceptions related to Rive failing to initialize properly. */
class RiveInitializationException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/** Exceptions related to Rive failing to shut down properly. */
class RiveShutdownException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/** Exceptions related to Rive rendering. */
class RiveRenderException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/** Exceptions related to Rive file handling. */
class RiveFileException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/** Exceptions related to drawing to a buffer. */
class RiveDrawToBufferException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

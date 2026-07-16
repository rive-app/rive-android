package app.rive

/** Exceptions related to Rive failing to initialize properly. */
class RiveInitializationException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** Exceptions related to Rive failing to shut down properly. */
class RiveShutdownException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** Exceptions related to Rive rendering. */
class RiveRenderException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** Exceptions related to Rive file handling. */
class RiveFileException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** Exceptions related to drawing to a buffer. */
class RiveDrawToBufferException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

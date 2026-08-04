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

/** Exceptions reported while performing an operation on a Rive artboard. */
class RiveArtboardException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/** Exceptions reported while performing an operation on a Rive state machine. */
class RiveStateMachineException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/** Exceptions reported while performing an operation on a Rive view model instance. */
class RiveViewModelInstanceException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/** Exceptions reported while decoding a Rive image asset. */
class RiveImageException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/** Exceptions reported while decoding a Rive audio asset. */
class RiveAudioException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/** Exceptions reported while decoding a Rive font asset. */
class RiveFontException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/** Exceptions related to drawing to a buffer. */
class RiveDrawToBufferException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** Thrown when an operation requires a Rive resource that has already been closed. */
class RiveResourceClosedException(message: String) :
    IllegalStateException(message)

/** Thrown when otherwise valid Rive resources cannot be used together. */
class RiveIncompatibleResourceException(message: String) :
    IllegalArgumentException(message)

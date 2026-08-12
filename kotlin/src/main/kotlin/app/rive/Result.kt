@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER") // Retained for pre-12.0 import compatibility.

package app.rive

import androidx.compose.runtime.Composable

/**
 * Represents an asynchronous operation that can be loading, failed, or successful.
 *
 * A [Success] must be unwrapped before its value can be used. Suspending APIs generally return
 * their values directly and throw on failure; this type is used where callers must observe an
 * operation across Compose recompositions.
 */
sealed interface Result<out T> {
    /** The operation has not completed. */
    object Loading : Result<Nothing>

    /** The operation failed with [throwable]. */
    data class Error(val throwable: Throwable) : Result<Nothing>

    /** The operation completed with [value]. */
    data class Success<T>(val value: T) : Result<T>

    /**
     * Chains this result into another result, forwarding loading and error states unchanged.
     *
     * The success transform is composable so it can invoke result-producing `remember` APIs.
     *
     * @param onSuccess Produces the next result from a successful value.
     * @return The result produced by [onSuccess], or this loading or error state.
     */
    @Composable
    fun <R> andThen(
        onSuccess: @Composable (T) -> Result<R>,
    ): Result<R> = when (this) {
        is Loading -> Loading
        is Error -> this
        is Success -> onSuccess(value)
    }

    /**
     * Maps the successful value while forwarding loading and error states unchanged.
     *
     * @param transform Maps a successful value to another value.
     * @return The mapped success, or this loading or error state.
     */
    fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Loading -> Loading
        is Error -> this
        is Success -> Success(transform(value))
    }

    /**
     * Combines two successful results while forwarding the first loading or error state.
     *
     * @param other The other result to combine with.
     * @param combine Maps both successful values to the combined value.
     * @return The combined success, or the first loading or error state.
     */
    fun <U, R> zip(
        other: Result<U>,
        combine: (T, U) -> R,
    ): Result<R> = when (this) {
        is Loading -> Loading
        is Error -> this
        is Success -> when (other) {
            is Loading -> Loading
            is Error -> other
            is Success -> Success(combine(value, other.value))
        }
    }

    /**
     * Pairs two successful results while forwarding the first loading or error state.
     *
     * @param other The other result to pair with.
     * @return The paired success, or the first loading or error state.
     */
    fun <U> zip(other: Result<U>): Result<Pair<T, U>> = zip(other) { value, otherValue ->
        value to otherValue
    }

    /**
     * Compatibility extension for the former dispatch-receiver API.
     *
     * @param onSuccess Produces the next result from a successful value.
     * @return The result produced by [onSuccess], or the original loading or error state.
     * @deprecated Call [andThen] directly. This extension will be removed in 12.0.
     */
    @Deprecated(
        message = "Call andThen directly. This dispatch-receiver extension will be removed in " +
            "12.0.",
        replaceWith = ReplaceWith("this.andThen(onSuccess)")
    )
    @Composable
    fun <U, R> Result<U>.andThen(
        onSuccess: @Composable (U) -> Result<R>,
    ): Result<R> = this.andThen(onSuccess)

    /**
     * Compatibility extension for the former dispatch-receiver API.
     *
     * @param other The other result to combine with.
     * @param combine Maps both successful values to the combined value.
     * @return The combined success, or the first loading or error state.
     * @deprecated Call [zip] directly. This extension will be removed in 12.0.
     */
    @Deprecated(
        message = "Call zip directly. This dispatch-receiver extension will be removed in 12.0.",
        replaceWith = ReplaceWith("this.zip(other, combine)")
    )
    fun <A, B, R> Result<A>.zip(
        other: Result<B>,
        combine: (A, B) -> R,
    ): Result<R> = this.zip(other, combine)

    /**
     * Compatibility extension for the former dispatch-receiver API.
     *
     * @param other The other result to pair with.
     * @return The paired success, or the first loading or error state.
     * @deprecated Call [zip] directly. This extension will be removed in 12.0.
     */
    @Deprecated(
        message = "Call zip directly. This dispatch-receiver extension will be removed in 12.0.",
        replaceWith = ReplaceWith("this.zip(other)")
    )
    fun <A, B> Result<A>.zip(other: Result<B>): Result<Pair<A, B>> = this.zip(other)

    /**
     * Compatibility extension for the former dispatch-receiver API.
     *
     * @return The list of successful values, or the first loading or error state.
     * @deprecated Import and use the top-level [app.rive.sequence] extension. This extension will
     *    be removed in 12.0.
     */
    @Deprecated(
        message = "Import and use the top-level app.rive.sequence extension. This " +
            "dispatch-receiver extension will be removed in 12.0.",
        replaceWith = ReplaceWith("this.sequence()", "app.rive.sequence")
    )
    fun <U> Iterable<Result<U>>.sequence(): Result<List<U>> = sequenceResults(this)
}

/**
 * Joins these results into one list result, forwarding the first loading or error state.
 *
 * @return The list of successful values, or the first loading or error state.
 */
fun <T> Iterable<Result<T>>.sequence(): Result<List<T>> = sequenceResults(this)

/**
 * Implements result sequencing for the canonical and compatibility extensions.
 *
 * Inline this into [sequence] when the deprecated dispatch-receiver extension is removed in 12.0.
 *
 * @param results The results to join in iteration order.
 * @return The list of successful values, or the first loading or error state.
 */
private fun <T> sequenceResults(results: Iterable<Result<T>>): Result<List<T>> {
    val values = ArrayList<T>()
    for (result in results) {
        when (result) {
            is Result.Loading -> return Result.Loading
            is Result.Error -> return result
            is Result.Success -> values += result.value
        }
    }
    return Result.Success(values)
}

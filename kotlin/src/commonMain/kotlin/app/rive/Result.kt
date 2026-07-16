package app.rive

import androidx.compose.runtime.Composable

/**
 * Represents the result of an operation - typically loading - that can be in a loading, error,
 * or success state. This includes Rive file loading. The Success result must be unwrapped to the
 * value, e.g. through Kotlin's when/is statements.
 */
sealed interface Result<out T> {
    object Loading : Result<Nothing>
    data class Error(val throwable: Throwable) : Result<Nothing>
    data class Success<T>(val value: T) : Result<T>

    /**
     * Convenience to chain the result of one Result into another, forwarding loading and error
     * states, and mapping the success to another Result.
     *
     * @param onSuccess The mapping function to apply to a Success result.
     */
    @Composable
    fun <T, R> Result<T>.andThen(
        onSuccess: @Composable (T) -> Result<R>
    ): Result<R> = when (this) {
        is Loading -> Loading
        is Error -> Error(this.throwable)
        is Success -> onSuccess(this.value)
    }

    /**
     * Convenience to join two Results into one, forwarding loading and error states, and mapping
     * the combination to another Result.
     *
     * @param other The other Result to combine with.
     * @param combine The mapping function to apply to the two values, e.g. into a Pair with `{ a, b
     *    -> a to b }`.
     */
    fun <A, B, R> Result<A>.zip(
        other: Result<B>,
        combine: (A, B) -> R
    ): Result<R> = when (this) {
        is Loading -> Loading
        is Error -> Error(this.throwable)
        is Success -> when (other) {
            is Loading -> Loading
            is Error -> Error(other.throwable)
            is Success -> Success(combine(this.value, other.value))
        }
    }

    /**
     * Convenience to join two Results into one, forwarding loading and error states, and mapping
     * the combination to a Pair.
     *
     * @param other The other Result to combine with.
     */
    fun <A, B> Result<A>.zip(
        other: Result<B>
    ): Result<Pair<A, B>> = zip(other) { a, b -> a to b }

    /**
     * Convenience to join a list of Results into a Result of one List. Any loading or error states
     * will become the final Result state.
     */
    fun <T> Iterable<Result<T>>.sequence(): Result<List<T>> {
        val out = ArrayList<T>()
        for (r in this) {
            when (r) {
                is Error -> return Error(r.throwable)
                is Loading -> return Loading
                is Success -> out += r.value
            }
        }
        return Success(out)
    }
}

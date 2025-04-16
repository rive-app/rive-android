package app.rive.runtime.kotlin.core.errors

/**
 * A custom exception signifying a problem with when working
 * with a [ViewModel][app.rive.runtime.kotlin.core.ViewModel] or
 * [ViewModelInstance][app.rive.runtime.kotlin.core.ViewModelInstance].
 *
 * @param message A description of the issue.
 */
class ViewModelException(message: String) : RiveException(message)

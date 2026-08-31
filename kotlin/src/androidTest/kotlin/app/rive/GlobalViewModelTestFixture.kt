package app.rive

/** Shared schema and resource helpers for the global view models in `data_bind_test_impl.riv`. */
internal object GlobalViewModelTestFixture {
    const val GLOBALS_ARTBOARD = "Test Globals"
    const val OBSERVATION_ARTBOARD = "Test Globals Observation"

    const val MAIN_VIEW_MODEL = "Test Main"
    const val MAIN_STRING = "Test Main String"
    const val ADVANCE_TRIGGER = "Advance"

    const val GLOBAL_VIEW_MODEL = "Test Global"
    const val GLOBAL_STRING = "Test Global String"

    const val GLOBAL_VIEW_MODEL_2 = "Test Global 2"
    const val GLOBAL_STRING_2 = "Test Global String 2"
    const val INVALID_GLOBAL_VIEW_MODEL = "Missing Global"

    const val DEFAULT_INSTANCE = "Default"
    const val ALTERNATE_INSTANCE = "Alternate"

    const val DEFAULT_MAIN = "Default Main"
    const val ALTERNATE_MAIN = "Alternate Main"
    const val DEFAULT_GLOBAL = "Default Global"
    const val ALTERNATE_GLOBAL = "Alternate Global"
    const val DEFAULT_GLOBAL_2 = "Default Global 2"

    const val BASE_GLOBAL_1 = "Base Global 1"
    const val SET_GLOBAL_1 = "Set Global 1"
    const val BASE_GLOBAL_2 = "Base Global 2"
    const val SET_GLOBAL_2 = "Set Global 2"

    /** Identifies one named view model instance to create for a binding test. */
    data class InstanceSpec(
        val viewModel: String,
        val instance: String,
    )

    /**
     * Creates and owns an arbitrary number of named view model instances for [block].
     *
     * Instances are acquired in [specs] order and nested through [AutoCloseable.use], preserving
     * cleanup when creation or [block] fails. The resulting list follows the same order and may be
     * destructured by tests for concise, flat resource setup.
     *
     * @param file The file containing every requested instance.
     * @param specs The view model and named instance pairs to create.
     * @param block The test operation to run with the created instances.
     * @return The value returned by [block].
     */
    suspend fun <T> withInstances(
        file: RiveFile,
        vararg specs: InstanceSpec,
        block: suspend (List<ViewModelInstance>) -> T,
    ): T {
        val instances = mutableListOf<ViewModelInstance>()

        suspend fun acquire(index: Int): T {
            if (index == specs.size) {
                return block(instances.toList())
            }

            val spec = specs[index]
            return ViewModelInstance.create(
                file,
                ViewModelSource.Named(spec.viewModel).namedInstance(spec.instance),
            ).use { instance ->
                instances.add(instance)
                try {
                    acquire(index + 1)
                } finally {
                    instances.removeAt(instances.lastIndex)
                }
            }
        }

        return acquire(0)
    }
}

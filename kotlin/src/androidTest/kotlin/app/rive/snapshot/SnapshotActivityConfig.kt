package app.rive.snapshot

import android.content.Intent

/**
 * This sealed class represents different configurations for test scenarios, used by snapshot test
 * activities. Each variant specifies an artboard name and scenario-specific parameters.
 */
sealed class SnapshotActivityConfig {
    /** The name of the artboard to use for this scenario. */
    abstract val artboardName: String

    /**
     * Sweep scenario: Moves a vertical line from left to right by advancing the state machine by a
     * percentage of the animation duration.
     *
     * @param percentage The percentage of animation progress, from 0f to 1f. This maps to 0-1000ms
     *    for state machine advancement.
     * @param artboardName Set to the sweep artboard.
     */
    data class Sweep(
        val percentage: Float,
        override val artboardName: String = "Sweep"
    ) : SnapshotActivityConfig() {
        init {
            require(percentage in 0f..1f) {
                "Percentage must be between 0 and 1, got $percentage"
            }
        }
    }

    /**
     * Data bind scenario: binds a string value to text.
     *
     * @param value The string value to bind.
     * @param artboardName Set to the data binding artboard.
     */
    data class DataBind(
        val value: String,
        override val artboardName: String = "Data Bind Text"
    ) : SnapshotActivityConfig()

    companion object {
        private const val EXTRA_SCENARIO_TYPE = "app.rive.snapshot.scenario_type"
        private const val EXTRA_PERCENTAGE = "app.rive.snapshot.percentage"
        private const val EXTRA_DATA_BIND_VALUE = "app.rive.snapshot.data_bind_value"

        private const val SCENARIO_TYPE_SWEEP = "sweep"
        private const val SCENARIO_TYPE_DATA_BIND = "data_bind"

        /**
         * Creates a [SnapshotActivityConfig] from an [Intent].
         *
         * @param intent The intent to extract configuration from.
         * @return A [SnapshotActivityConfig] with values from the intent.
         */
        fun fromIntent(intent: Intent): SnapshotActivityConfig {
            val scenarioType = intent.getStringExtra(EXTRA_SCENARIO_TYPE) ?: error(
                "Missing scenario type in intent extras"
            )

            return when (scenarioType) {
                SCENARIO_TYPE_SWEEP -> {
                    val percentage = intent.getFloatExtra(EXTRA_PERCENTAGE, 0.5f).coerceIn(0f, 1f)
                    Sweep(percentage)
                }

                SCENARIO_TYPE_DATA_BIND -> {
                    val value = intent.getStringExtra(EXTRA_DATA_BIND_VALUE) ?: ""
                    DataBind(value)
                }

                else -> error("Unknown scenario type: $scenarioType")
            }
        }

        /**
         * Applies this configuration's properties to an [Intent]'s extras.
         *
         * @param intent The intent to configure.
         * @param config The configuration to apply.
         */
        fun intoIntent(intent: Intent, config: SnapshotActivityConfig) {
            when (config) {
                is Sweep -> {
                    intent.putExtra(EXTRA_SCENARIO_TYPE, SCENARIO_TYPE_SWEEP)
                    intent.putExtra(EXTRA_PERCENTAGE, config.percentage)
                }

                is DataBind -> {
                    intent.putExtra(EXTRA_SCENARIO_TYPE, SCENARIO_TYPE_DATA_BIND)
                    intent.putExtra(EXTRA_DATA_BIND_VALUE, config.value)
                }
            }
        }
    }
}

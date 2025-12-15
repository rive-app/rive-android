package app.rive.snapshot

import android.content.Intent

/**
 * This sealed class represents different configurations for test scenarios, used by snapshot test
 * activities. Each variant specifies an artboard name and scenario-specific parameters.
 */
sealed class SnapshotActivityConfig {
    /** An intent-friendly string representation of the scenario type. */
    enum class ScenarioType {
        SWEEP,
        DATA_BIND,
        LAYOUT;
    }

    /** The scenario type used in the intent. */
    abstract val scenarioType: ScenarioType

    /** The name of the artboard to use for this scenario. */
    abstract val artboardName: String

    /**
     * Applies this configuration's properties to an [Intent]'s extras.
     *
     * @param intent The intent to configure.
     */
    fun applyToIntent(intent: Intent) {
        intent.putExtra(EXTRA_SCENARIO_TYPE, scenarioType)
        applyParamsToIntent(intent)
    }

    /** Apply scenario specific parameters to the intent. */
    abstract fun applyParamsToIntent(intent: Intent)

    /**
     * Sweep scenario: Moves a vertical line from left to right by advancing the state machine by a
     * percentage of the animation duration.
     *
     * @param percentage The percentage of animation progress, from 0f to 1f. This maps to 0-1000ms
     *    for state machine advancement.
     * @param scenarioType Set to sweep.
     * @param artboardName Set to the sweep artboard.
     */
    data class Sweep(
        val percentage: Float,
        override val scenarioType: ScenarioType = ScenarioType.SWEEP,
        override val artboardName: String = "Sweep",
    ) : SnapshotActivityConfig() {
        companion object {
            private const val EXTRA_PERCENTAGE = "app.rive.snapshot.percentage"

            fun fromIntent(intent: Intent): Sweep {
                val percentage =
                    intent.getFloatExtra(EXTRA_PERCENTAGE, 0.5f).coerceIn(0f, 1f)
                return Sweep(percentage)
            }
        }

        init {
            require(percentage in 0f..1f) {
                "Percentage must be between 0 and 1, got $percentage"
            }
        }

        override fun applyParamsToIntent(intent: Intent) {
            intent.putExtra(EXTRA_PERCENTAGE, percentage)
        }
    }

    /**
     * Data bind scenario: binds a string value to text.
     *
     * @param value The string value to bind.
     * @param scenarioType Set to data bind.
     * @param artboardName Set to the data binding artboard.
     */
    data class DataBind(
        val value: String,
        override val scenarioType: ScenarioType = ScenarioType.DATA_BIND,
        override val artboardName: String = "Data Bind Text"
    ) : SnapshotActivityConfig() {
        companion object {
            const val EXTRA_DATA_BIND_STRING = "app.rive.snapshot.data_bind_value"

            fun fromIntent(intent: Intent): DataBind {
                val value = intent.getStringExtra(EXTRA_DATA_BIND_STRING) ?: ""
                return DataBind(value)
            }
        }

        override fun applyParamsToIntent(intent: Intent) {
            intent.putExtra(EXTRA_DATA_BIND_STRING, value)
        }
    }

    /**
     * Layout scenario: tests rendering with or without layout at a given scale.
     *
     * @param useLayout Whether to use layout.
     * @param layoutScale The scale factor to use for layout.
     * @param scenarioType Set to layout.
     * @param artboardName Set to the layout artboard.
     */
    data class Layout(
        val useLayout: Boolean,
        val layoutScale: Float,
        override val scenarioType: ScenarioType = ScenarioType.LAYOUT,
        override val artboardName: String = "Layout"
    ) : SnapshotActivityConfig() {
        companion object {
            const val EXTRA_LAYOUT = "app.rive.snapshot.layout"
            const val EXTRA_LAYOUT_SCALE = "app.rive.snapshot.layout_scale"

            fun fromIntent(intent: Intent): Layout {
                val useLayout = intent.getBooleanExtra(EXTRA_LAYOUT, false)
                val layoutScale = intent.getFloatExtra(EXTRA_LAYOUT_SCALE, 1f)
                return Layout(useLayout, layoutScale)
            }
        }

        override fun applyParamsToIntent(intent: Intent) {
            intent
                .putExtra(EXTRA_LAYOUT, useLayout)
                .putExtra(EXTRA_LAYOUT_SCALE, layoutScale)
        }
    }

    companion object {
        private const val EXTRA_SCENARIO_TYPE = "app.rive.snapshot.scenario_type"

        /**
         * Creates a [SnapshotActivityConfig] from an [Intent].
         *
         * @param intent The intent to extract configuration from.
         * @return A [SnapshotActivityConfig] with values from the intent.
         */
        fun fromIntent(intent: Intent): SnapshotActivityConfig {
            val scenarioType =
                intent.getSerializableExtra(EXTRA_SCENARIO_TYPE, ScenarioType::class.java)
                    ?: error("Missing scenario type in intent extras")

            return when (scenarioType) {
                ScenarioType.SWEEP -> Sweep.fromIntent(intent)
                ScenarioType.DATA_BIND -> DataBind.fromIntent(intent)
                ScenarioType.LAYOUT -> Layout.fromIntent(intent)
            }
        }
    }
}

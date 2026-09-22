package helianthus.core.pipeline

/**
 * A single comparison operator usable in pipeline filter configurations.
 *
 * Operators are looked up by [key] (e.g., `eq`, `gt`) and evaluated against a
 * row's cell value using [args] supplied from the configuration.
 */
internal interface FilterOperator {
    /** The configuration key used to select this operator. */
    val key: String

    /**
     * Returns true when [value] satisfies this operator against [args].
     */
    fun evaluate(value: Any?, args: Any?): Boolean
}

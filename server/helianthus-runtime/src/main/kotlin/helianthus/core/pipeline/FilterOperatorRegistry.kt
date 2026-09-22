package helianthus.core.pipeline

/**
 * Resolves [FilterOperator]s by configuration key and evaluates a cell value
 * against a compound condition (multiple operator entries AND together).
 *
 * @param operators the operators available, keyed by their [FilterOperator.key]
 */
internal class FilterOperatorRegistry(
    private val operators: Map<String, FilterOperator> = defaultOperators()
) {

    /**
     * Evaluates [condition] against a single cell [value]. The condition map
     * maps operator keys to their arguments; all operators must succeed for the
     * overall condition to be true.
     *
     * @throws IllegalArgumentException if [condition] references an unknown operator key
     */
    fun evaluateCondition(value: Any?, condition: Map<String, Any?>): Boolean {
        for ((key, args) in condition) {
            val operator = operators[key]
                    ?: throw IllegalArgumentException("Unknown filter operator: $key")
            if (!operator.evaluate(value, args)) {
                return false
            }
        }
        return true
    }

    /** Returns the set of operator keys registered with this instance. */
    fun operatorKeys(): Set<String> = operators.keys

    companion object {
        /**
         * Builds the default operator registry containing `eq`, `neq`, `gt`,
         * `gte`, `lt`, `lte`, and `in`.
         */
        fun defaultOperators(): Map<String, FilterOperator> {
            val ops: List<FilterOperator> = listOf(
                EqOperator,
                NeqOperator,
                GtOperator,
                GteOperator,
                LtOperator,
                LteOperator,
                InOperator
            )
            return ops.associateBy { it.key }
        }
    }
}

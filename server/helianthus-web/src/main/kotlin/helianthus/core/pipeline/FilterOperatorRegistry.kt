package helianthus.core.pipeline

class FilterOperatorRegistry(
    private val operators: Map<String, FilterOperator> = defaultOperators()
) {

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

    fun operatorKeys(): Set<String> = operators.keys

    companion object {
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

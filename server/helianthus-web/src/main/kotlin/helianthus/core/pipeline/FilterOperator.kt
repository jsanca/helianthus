package helianthus.core.pipeline

interface FilterOperator {
    val key: String

    fun evaluate(value: Any?, args: Any?): Boolean
}

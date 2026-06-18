package helianthus.core.pipeline

object EqOperator : FilterOperator {
    override val key = "eq"
    override fun evaluate(value: Any?, args: Any?): Boolean = value == args
}

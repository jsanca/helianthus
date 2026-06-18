package helianthus.core.pipeline

object NeqOperator : FilterOperator {
    override val key = "neq"
    override fun evaluate(value: Any?, args: Any?): Boolean = value != args
}

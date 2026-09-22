package helianthus.core.pipeline

/** Equality operator (`eq`). */
internal object EqOperator : FilterOperator {
    override val key = "eq"
    override fun evaluate(value: Any?, args: Any?): Boolean = value == args
}

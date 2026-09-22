package helianthus.core.pipeline

/** Inequality operator (`neq`). */
internal object NeqOperator : FilterOperator {
    override val key = "neq"
    override fun evaluate(value: Any?, args: Any?): Boolean = value != args
}

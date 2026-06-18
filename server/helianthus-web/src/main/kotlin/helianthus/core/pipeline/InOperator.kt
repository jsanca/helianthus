package helianthus.core.pipeline

object InOperator : FilterOperator {
    override val key = "in"

    override fun evaluate(value: Any?, args: Any?): Boolean {
        val list = args as? List<*> ?: return false
        return list.contains(value)
    }
}

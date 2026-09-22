package helianthus.core.pipeline

/**
 * Membership operator (`in`). The [args] must be a `List`; any other shape
 * returns false rather than throwing.
 */
internal object InOperator : FilterOperator {
    override val key = "in"

    override fun evaluate(value: Any?, args: Any?): Boolean {
        val list = args as? List<*> ?: return false
        return list.contains(value)
    }
}

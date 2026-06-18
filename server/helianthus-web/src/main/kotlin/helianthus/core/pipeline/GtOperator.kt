package helianthus.core.pipeline

object GtOperator : FilterOperator {
    override val key = "gt"

    @Suppress("UNCHECKED_CAST")
    override fun evaluate(value: Any?, args: Any?): Boolean {
        if (value == null || args == null) return false
        return try {
            (value as Comparable<Any>).compareTo(args) > 0
        } catch (e: ClassCastException) {
            value.toString().compareTo(args.toString()) > 0
        }
    }
}

package helianthus.core.pipeline

/**
 * Greater-than-or-equal operator (`gte`).
 *
 * Falls back to a string comparison when the operands are not mutually
 * comparable as [Comparable].
 */
internal object GteOperator : FilterOperator {
    override val key = "gte"

    @Suppress("UNCHECKED_CAST")
    override fun evaluate(value: Any?, args: Any?): Boolean {
        if (value == null || args == null) return false
        return try {
            (value as Comparable<Any>).compareTo(args) >= 0
        } catch (e: ClassCastException) {
            value.toString().compareTo(args.toString()) >= 0
        }
    }
}

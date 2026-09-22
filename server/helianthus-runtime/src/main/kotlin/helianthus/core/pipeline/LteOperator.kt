package helianthus.core.pipeline

/**
 * Less-than-or-equal operator (`lte`).
 *
 * Falls back to a string comparison when the operands are not mutually
 * comparable as [Comparable].
 */
internal object LteOperator : FilterOperator {
    override val key = "lte"

    @Suppress("UNCHECKED_CAST")
    override fun evaluate(value: Any?, args: Any?): Boolean {
        if (value == null || args == null) return false
        return try {
            (value as Comparable<Any>).compareTo(args) <= 0
        } catch (e: ClassCastException) {
            value.toString().compareTo(args.toString()) <= 0
        }
    }
}

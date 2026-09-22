package helianthus.core.access

/**
 * A single bound parameter for an [SqlExecutionPlan].
 *
 * @property name the parameter's name (for logging/diagnostics)
 * @property type the SQL type name (see [helianthus.core.result.ResultType.fromTypeName])
 * @property value the actual value to bind; `null` binds a typed SQL NULL
 */
data class BoundParameter(
    val name: String,
    val type: String,
    val value: Any?
)

/**
 * A fully-prepared SQL execution plan: positional SQL with `?` placeholders
 * and the ordered list of parameters to bind.
 *
 * @property sql SQL with `?` placeholders, in the order matching [params]
 * @property params parameters to bind in declaration order
 */
data class SqlExecutionPlan(
    val sql: String,
    val params: List<BoundParameter>
)

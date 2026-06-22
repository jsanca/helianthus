package helianthus.core.access

data class BoundParameter(
    val name: String,
    val type: String,
    val value: Any?
)

data class SqlExecutionPlan(
    val sql: String,
    val params: List<BoundParameter>
)

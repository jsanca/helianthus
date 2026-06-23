package helianthus.core.catalog

import helianthus.core.access.SqlDialect
import helianthus.core.access.SqlExecutionPlan
import helianthus.core.access.BoundParameter

class EntityCrudSqlBuilder(
    private val dialect: SqlDialect
) {
    fun buildListSql(
        entity: EntityDef,
        filters: Map<String, Any?>,
        orderBy: String?,
        orderDir: String,
        limit: Int,
        offset: Int
    ): SqlExecutionPlan {
        val q = { name: String -> dialect.quoteIdentifier(name) }

        val selectClause = entity.fields.joinToString(", ") { q(it) }
        val fromClause = q(entity.table)

        val whereClauses = mutableListOf<String>()
        val params = mutableListOf<BoundParameter>()

        filters.forEach { (field, value) ->
            if (field in entity.fields) {
                whereClauses.add("${q(field)} = ?")
                params.add(BoundParameter(field, inferType(value), value))
            }
        }

        val whereClause = if (whereClauses.isEmpty()) "" else "WHERE ${whereClauses.joinToString(" AND ")}"

        val orderByClause = if (orderBy != null && orderBy in entity.fields) {
            "ORDER BY ${q(orderBy)} $orderDir"
        } else {
            ""
        }

        val limitOffsetClause = dialect.limitOffset(limit, offset)

        val sql = """
            SELECT $selectClause
            FROM $fromClause
            $whereClause
            $orderByClause
            $limitOffsetClause
        """.trimIndent().replace("\n", " ").replace("  ", " ")

        return SqlExecutionPlan(sql, params)
    }

    fun buildGetByIdSql(entity: EntityDef, id: Any?): SqlExecutionPlan {
        val q = { name: String -> dialect.quoteIdentifier(name) }

        val selectClause = entity.fields.joinToString(", ") { q(it) }
        val fromClause = q(entity.table)
        val pkColumn = entity.primaryKey.columns.first()
        val whereClause = "WHERE ${q(pkColumn)} = ?"

        val sql = "SELECT $selectClause FROM $fromClause $whereClause"
        val params = listOf(BoundParameter(pkColumn, inferType(id), id))

        return SqlExecutionPlan(sql, params)
    }

    private fun inferType(value: Any?): String {
        return when (value) {
            is Int -> "integer"
            is Long -> "long"
            is Double -> "number"
            is Float -> "number"
            is Boolean -> "boolean"
            else -> "string"
        }
    }
}

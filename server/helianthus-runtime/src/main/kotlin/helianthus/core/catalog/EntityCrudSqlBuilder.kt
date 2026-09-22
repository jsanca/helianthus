package helianthus.core.catalog

import helianthus.core.access.SqlDialect
import helianthus.core.access.SqlExecutionPlan
import helianthus.core.access.BoundParameter

/**
 * Builds dialect-aware SQL for entity list and get-by-id operations.
 *
 * Knows nothing about connections or pipelines — it simply produces an
 * [SqlExecutionPlan] that can be executed against the right datasource.
 */
internal class EntityCrudSqlBuilder(
    private val dialect: SqlDialect
) {
    /**
     * Builds the SQL execution plan for an entity list request with optional
     * filters, ordering, and pagination.
     *
     * @param entity the entity to query
     * @param filters exact-match filters keyed by logical field name
     * @param orderBy optional logical field name to sort by
     * @param orderDir sort direction (`ASC`/`DESC`); ignored when [orderBy] is null
     * @param limit maximum number of rows to return
     * @param offset zero-based row offset
     */
    fun buildListSql(
        entity: EntityDef,
        filters: Map<String, Any?>,
        orderBy: String?,
        orderDir: String,
        limit: Int,
        offset: Int
    ): SqlExecutionPlan {
        val q = { name: String -> dialect.quoteIdentifier(name) }

        val selectClause = entity.fields.joinToString(", ") { field -> selectFragment(field, q) }
        val fromClause = q(entity.table)

        val whereClauses = mutableListOf<String>()
        val params = mutableListOf<BoundParameter>()

        filters.forEach { (fieldName, value) ->
            val field = entity.fieldByName(fieldName)
            if (field != null) {
                whereClauses.add("${columnRef(field, q)} = ?")
                params.add(BoundParameter(field.name, inferType(value), value))
            }
        }

        val whereClause = if (whereClauses.isEmpty()) "" else "WHERE ${whereClauses.joinToString(" AND ")}"

        val orderByClause = if (orderBy != null) {
            val field = entity.fieldByName(orderBy)
            if (field != null) "ORDER BY ${columnRef(field, q)} $orderDir" else ""
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

    /**
     * Builds the SQL execution plan for an entity get-by-id request.
     *
     * Only the first primary-key column is used; composite-key lookups are
     * not supported by this builder.
     */
    fun buildGetByIdSql(entity: EntityDef, id: Any?): SqlExecutionPlan {
        val q = { name: String -> dialect.quoteIdentifier(name) }

        val selectClause = entity.fields.joinToString(", ") { field -> selectFragment(field, q) }
        val fromClause = q(entity.table)
        val pkFieldName = entity.primaryKey.columns.first()
        val pkField = entity.fieldByName(pkFieldName)
            ?: throw IllegalStateException("Primary key field '$pkFieldName' not found in entity fields")
        val whereClause = "WHERE ${columnRef(pkField, q)} = ?"

        val sql = "SELECT $selectClause FROM $fromClause $whereClause"
        val params = listOf(BoundParameter(pkField.name, inferType(id), id))

        return SqlExecutionPlan(sql, params)
    }

    private fun selectFragment(field: FieldDef, q: (String) -> String): String =
        if (field.name == field.column) q(field.column)
        else "${field.column} AS ${q(field.name)}"

    private fun columnRef(field: FieldDef, q: (String) -> String): String =
        if (field.name == field.column) q(field.column)
        else field.column

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

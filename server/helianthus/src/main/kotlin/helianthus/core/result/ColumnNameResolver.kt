package helianthus.core.result

/**
 * Utility for resolving column names case-insensitively.
 *
 * Provides methods to find columns in a schema or row map using case-insensitive
 * matching, while preserving the original column names from the schema.
 */
object ColumnNameResolver {

    /**
     * Resolves a list of requested column names against a schema, returning the
     * matching columns in the order requested.
     *
     * @param requestedColumns column names to find (case-insensitive)
     * @param schema the result schema to search
     * @return list of matching ResultColumn objects in the order requested
     * @throws IllegalArgumentException if any requested column is not found
     */
    fun resolveColumns(requestedColumns: List<String>, schema: ResultSchema): List<ResultColumn> {
        val schemaByLower = schema.columns.associateBy { it.name.lowercase() }
        
        return requestedColumns.map { requested ->
            schemaByLower[requested.lowercase()]
                ?: throw IllegalArgumentException(
                    "Column '${requested}' not found in schema. Available columns: ${schema.columns.map { it.name }}"
                )
        }
    }

    /**
     * Resolves a single column name against a schema.
     *
     * @param requestedColumn column name to find (case-insensitive)
     * @param schema the result schema to search
     * @return the matching ResultColumn
     * @throws IllegalArgumentException if the column is not found
     */
    fun resolveColumn(requestedColumn: String, schema: ResultSchema): ResultColumn {
        val resolved = schema.columns.find { it.name.equals(requestedColumn, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "Column '${requestedColumn}' not found in schema. Available columns: ${schema.columns.map { it.name }}"
            )
        return resolved
    }

    /**
     * Gets a value from a row map using case-insensitive column name matching.
     *
     * @param row the row data map
     * @param columnName the column name to look up (case-insensitive)
     * @return the value for that column, or null if not found
     */
    fun getRowValue(row: Map<String, Any?>, columnName: String): Any? {
        val key = row.keys.find { it.equals(columnName, ignoreCase = true) }
        return if (key != null) row[key] else null
    }

    /**
     * Gets a value from a row map using case-insensitive column name matching,
     * throwing an exception if the column is not found.
     *
     * @param row the row data map
     * @param columnName the column name to look up (case-insensitive)
     * @return the value for that column
     * @throws IllegalArgumentException if the column is not found in the row
     */
    fun getRowValueOrThrow(row: Map<String, Any?>, columnName: String): Any? {
        val key = row.keys.find { it.equals(columnName, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "Column '${columnName}' not found in row. Available columns: ${row.keys}"
            )
        return row[key]
    }
}

package helianthus.core.result

/**
 * Describes the column structure of a result set.
 *
 * @property columns ordered list of column definitions
 */
data class ResultSchema(val columns: List<ResultColumn>) {

    /** Returns the number of columns in this schema. */
    val columnCount: Int get() = columns.size

    /**
     * Finds a column by exact name match.
     * @param name the column name to look up
     * @return the matching column or null if not found
     */
    fun getColumn(name: String): ResultColumn? = columns.find { it.name == name }

    /**
     * Retrieves a column by its ordinal position.
     * @param index zero-based column position
     * @return the column at that position or null if out of bounds
     */
    fun getColumn(index: Int): ResultColumn? = columns.getOrNull(index)
}

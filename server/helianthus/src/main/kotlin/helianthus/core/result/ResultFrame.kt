package helianthus.core.result

/**
 * Represents the complete result of an operation execution.
 *
 * Contains the schema describing the columns, the row data as a list of
 * column-name to value maps, and execution metadata.
 *
 * @property schema describes the columns in this result
 * @property rows the row data, each row a map of column name to value
 * @property metadata execution statistics (row count, execution time)
 */
data class ResultFrame @JvmOverloads constructor(
    val schema: ResultSchema,
    val rows: List<Map<String, Any?>>,
    val metadata: ResultMetadata = ResultMetadata()
)

package helianthus.core.result

/**
 * Metadata about the execution of an operation.
 *
 * @property rowCount total number of rows returned by the query
 * @property executionTimeMs time taken to execute the query in milliseconds
 */
data class ResultMetadata @JvmOverloads constructor(
    val rowCount: Int = 0,
    val executionTimeMs: Long = 0
)

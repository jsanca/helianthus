package helianthus.core.result

/**
 * Defines a single column within a result schema.
 *
 * @property name the column name as returned by the database
 * @property type the data type of the column
 * @property nullable whether null values are permitted in this column
 */
data class ResultColumn @JvmOverloads constructor(
    val name: String,
    val type: ResultType = ResultType.UNKNOWN,
    val nullable: Boolean = true
)

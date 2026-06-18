package helianthus.core.result

/**
 * Supported column data types for result sets.
 */
enum class ResultType {
    STRING,
    INTEGER,
    LONG,
    FLOAT,
    DOUBLE,
    BOOLEAN,
    DECIMAL,
    DATE,
    BYTE_ARRAY,
    UNKNOWN;

    companion object {
        /**
         * Maps a database type name to a ResultType.
         * @param name the type name from the database driver or SQL type declaration
         * @return the corresponding ResultType, or UNKNOWN if no match
         */
        fun fromTypeName(name: String): ResultType = when (name.lowercase()) {
            "integer", "int" -> INTEGER
            "long" -> LONG
            "float" -> FLOAT
            "double" -> DOUBLE
            "boolean", "bool" -> BOOLEAN
            "decimal" -> DECIMAL
            "date", "timestamp", "time" -> DATE
            "string", "text", "varchar" -> STRING
            else -> UNKNOWN
        }
    }
}

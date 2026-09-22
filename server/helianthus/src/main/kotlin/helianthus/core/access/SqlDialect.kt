package helianthus.core.access

/**
 * Dialect-specific SQL rendering hooks used by the catalog builders.
 *
 * Implementations decide how identifiers are quoted and how `LIMIT`/`OFFSET`
 * clauses are formatted for the underlying database engine.
 */
interface SqlDialect {
    /**
     * Returns the dialect-appropriate quoted form of [name].
     */
    fun quoteIdentifier(name: String): String

    /**
     * Returns the dialect-appropriate `LIMIT`/`OFFSET` clause for the given
     * pagination values.
     */
    fun limitOffset(limit: Int, offset: Int): String
}

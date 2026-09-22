package helianthus.core.access.impl.db

import helianthus.core.access.SqlDialect

/**
 * [SqlDialect] for H2 (used in tests).
 *
 * Uses double quotes for identifier quoting and standard SQL
 * `LIMIT <n> OFFSET <m>` for pagination.
 */
internal class H2Dialect : SqlDialect {
    /** Returns [name] wrapped in double quotes. */
    override fun quoteIdentifier(name: String): String = "\"$name\""

    /** Returns `LIMIT <limit> OFFSET <offset>`. */
    override fun limitOffset(limit: Int, offset: Int): String = "LIMIT $limit OFFSET $offset"
}

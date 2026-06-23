package helianthus.core.access.impl.db

import helianthus.core.access.SqlDialect

class PostgresDialect : SqlDialect {
    override fun quoteIdentifier(name: String): String = "\"$name\""
    override fun limitOffset(limit: Int, offset: Int): String = "LIMIT $limit OFFSET $offset"
}

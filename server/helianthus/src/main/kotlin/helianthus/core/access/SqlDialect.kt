package helianthus.core.access

interface SqlDialect {
    fun quoteIdentifier(name: String): String
    fun limitOffset(limit: Int, offset: Int): String
}

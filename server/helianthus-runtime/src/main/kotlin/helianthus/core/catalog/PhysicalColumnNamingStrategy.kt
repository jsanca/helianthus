package helianthus.core.catalog

/**
 * Strategy for deriving physical database column names from logical field names
 * declared in the catalog.
 *
 * Used when an entity field entry is a bare string (no explicit `column`) so the
 * runtime can still issue a SELECT against the underlying table.
 */
internal interface PhysicalColumnNamingStrategy {
    /** Returns the physical column name corresponding to [logicalName]. */
    fun toPhysicalColumn(logicalName: String): String
}

/**
 * Default naming strategy that lowercases the logical name.
 */
internal class LowercaseNamingStrategy : PhysicalColumnNamingStrategy {
    override fun toPhysicalColumn(logicalName: String): String = logicalName.lowercase()
}

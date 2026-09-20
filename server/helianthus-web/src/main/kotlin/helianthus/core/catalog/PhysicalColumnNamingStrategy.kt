package helianthus.core.catalog

interface PhysicalColumnNamingStrategy {
    fun toPhysicalColumn(logicalName: String): String
}

class LowercaseNamingStrategy : PhysicalColumnNamingStrategy {
    override fun toPhysicalColumn(logicalName: String): String = logicalName.lowercase()
}

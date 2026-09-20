package helianthus.core.catalog

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PhysicalColumnNamingStrategyTest {

    private val strategy = LowercaseNamingStrategy()

    @Test
    fun `converts camelCase to lowercase`() {
        assertEquals("productcode", strategy.toPhysicalColumn("productCode"))
        assertEquals("productname", strategy.toPhysicalColumn("productName"))
        assertEquals("quantityinstock", strategy.toPhysicalColumn("quantityInStock"))
        assertEquals("textdescription", strategy.toPhysicalColumn("textDescription"))
        assertEquals("buyprice", strategy.toPhysicalColumn("buyPrice"))
    }

    @Test
    fun `keeps already-lowercase unchanged`() {
        assertEquals("id", strategy.toPhysicalColumn("id"))
        assertEquals("name", strategy.toPhysicalColumn("name"))
        assertEquals("price", strategy.toPhysicalColumn("price"))
    }

    @Test
    fun `converts uppercase to lowercase`() {
        assertEquals("productcode", strategy.toPhysicalColumn("PRODUCTCODE"))
        assertEquals("productline", strategy.toPhysicalColumn("PRODUCTLINE"))
    }

    @Test
    fun `simple string field applies naming strategy as fallback`() {
        val logicalName = "productCode"
        val field = FieldDef(name = logicalName, column = strategy.toPhysicalColumn(logicalName))

        assertEquals("productCode", field.name)
        assertEquals("productcode", field.column)
        assertNotEquals(field.name, field.column)
    }

    @Test
    fun `expanded form with explicit column bypasses naming strategy`() {
        val field = FieldDef(name = "productCode", column = "product_code")

        assertEquals("productCode", field.name)
        assertEquals("product_code", field.column)
    }

    @Test
    fun `expanded form without column applies naming strategy as fallback`() {
        val logicalName = "productCode"
        val field = FieldDef(name = logicalName, column = strategy.toPhysicalColumn(logicalName))

        assertEquals("productcode", field.column)
    }

    @Test
    fun `ClassicModels product fields map correctly`() {
        val fields = listOf("productCode", "productName", "productLine", "buyPrice", "quantityInStock")
            .map { FieldDef(it, strategy.toPhysicalColumn(it)) }

        assertEquals("productcode", fields[0].column)
        assertEquals("productname", fields[1].column)
        assertEquals("productline", fields[2].column)
        assertEquals("buyprice", fields[3].column)
        assertEquals("quantityinstock", fields[4].column)

        fields.forEach { assertEquals(it.name.lowercase(), it.column) }
    }

    @Test
    fun `PostgreSQL compatibility - lowercase column names match unquoted identifiers`() {
        listOf("productCode", "productName", "quantityInStock").forEach { logical ->
            val physical = strategy.toPhysicalColumn(logical)
            assertEquals(physical, physical.lowercase(),
                "Physical column '$physical' must be all-lowercase for unquoted PostgreSQL identifiers")
        }
    }
}

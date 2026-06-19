package helianthus.core.result

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ColumnNameResolverTest {

    private val schema = ResultSchema(listOf(
        ResultColumn("productCode", ResultType.STRING),
        ResultColumn("productName", ResultType.STRING),
        ResultColumn("buyPrice", ResultType.DECIMAL)
    ))

    @Test
    fun `resolveColumns should match case-insensitively`() {
        val requested = listOf("PRODUCTCODE", "productname", "BuyPrice")
        val resolved = ColumnNameResolver.resolveColumns(requested, schema)
        
        assertEquals(3, resolved.size)
        assertEquals("productCode", resolved[0].name)
        assertEquals("productName", resolved[1].name)
        assertEquals("buyPrice", resolved[2].name)
    }

    @Test
    fun `resolveColumns should preserve requested order`() {
        val requested = listOf("buyPrice", "productCode", "productName")
        val resolved = ColumnNameResolver.resolveColumns(requested, schema)
        
        assertEquals(3, resolved.size)
        assertEquals("buyPrice", resolved[0].name)
        assertEquals("productCode", resolved[1].name)
        assertEquals("productName", resolved[2].name)
    }

    @Test
    fun `resolveColumns should throw on missing column`() {
        val requested = listOf("productCode", "nonexistent")
        
        val exception = assertThrows<IllegalArgumentException> {
            ColumnNameResolver.resolveColumns(requested, schema)
        }
        
        assertEquals("Column 'nonexistent' not found in schema. Available columns: [productCode, productName, buyPrice]", exception.message)
    }

    @Test
    fun `resolveColumn should match case-insensitively`() {
        val resolved = ColumnNameResolver.resolveColumn("PRODUCTCODE", schema)
        assertEquals("productCode", resolved.name)
    }

    @Test
    fun `resolveColumn should throw on missing column`() {
        val exception = assertThrows<IllegalArgumentException> {
            ColumnNameResolver.resolveColumn("nonexistent", schema)
        }
        
        assertEquals("Column 'nonexistent' not found in schema. Available columns: [productCode, productName, buyPrice]", exception.message)
    }

    @Test
    fun `getRowValue should match case-insensitively`() {
        val row = mapOf("productCode" to "P001", "productName" to "Widget", "buyPrice" to 50.0)
        
        assertEquals("P001", ColumnNameResolver.getRowValue(row, "PRODUCTCODE"))
        assertEquals("Widget", ColumnNameResolver.getRowValue(row, "productname"))
        assertEquals(50.0, ColumnNameResolver.getRowValue(row, "BuyPrice"))
    }

    @Test
    fun `getRowValue should return null for missing column`() {
        val row = mapOf("productCode" to "P001")
        
        assertNull(ColumnNameResolver.getRowValue(row, "nonexistent"))
    }

    @Test
    fun `getRowValueOrThrow should match case-insensitively`() {
        val row = mapOf("productCode" to "P001", "productName" to "Widget")
        
        assertEquals("P001", ColumnNameResolver.getRowValueOrThrow(row, "PRODUCTCODE"))
        assertEquals("Widget", ColumnNameResolver.getRowValueOrThrow(row, "productname"))
    }

    @Test
    fun `getRowValueOrThrow should throw on missing column`() {
        val row = mapOf("productCode" to "P001")
        
        val exception = assertThrows<IllegalArgumentException> {
            ColumnNameResolver.getRowValueOrThrow(row, "nonexistent")
        }
        
        assertEquals("Column 'nonexistent' not found in row. Available columns: [productCode]", exception.message)
    }
}

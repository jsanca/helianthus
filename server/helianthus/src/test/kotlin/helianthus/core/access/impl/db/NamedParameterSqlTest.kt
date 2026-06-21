package helianthus.core.access.impl.db

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NamedParameterSqlTest {

    @Test
    fun `parse simple named parameter`() {
        val result = NamedParameterSql.parse("SELECT * FROM t WHERE name = :name")
        assertEquals("SELECT * FROM t WHERE name = ?", result.actualSql)
        assertEquals(listOf("name"), result.paramNames)
    }

    @Test
    fun `parse multiple named parameters`() {
        val result = NamedParameterSql.parse(
            "SELECT * FROM t WHERE a = :a AND b = :b AND c = :c"
        )
        assertEquals("SELECT * FROM t WHERE a = ? AND b = ? AND c = ?", result.actualSql)
        assertEquals(listOf("a", "b", "c"), result.paramNames)
    }

    @Test
    fun `parse repeated named parameter`() {
        val result = NamedParameterSql.parse(
            "SELECT * FROM t WHERE (:x IS NULL OR x = :x)"
        )
        assertEquals("SELECT * FROM t WHERE (? IS NULL OR x = ?)", result.actualSql)
        assertEquals(listOf("x", "x"), result.paramNames)
    }

    @Test
    fun `parse ignores PostgreSQL cast operator`() {
        val result = NamedParameterSql.parse("SELECT val::text FROM t WHERE name = :name")
        assertEquals("SELECT val::text FROM t WHERE name = ?", result.actualSql)
        assertEquals(listOf("name"), result.paramNames)
    }

    @Test
    fun `parse ignores string literals`() {
        val result = NamedParameterSql.parse(
            "SELECT * FROM t WHERE name = :name AND status = ':notAParam'"
        )
        assertEquals("SELECT * FROM t WHERE name = ? AND status = ':notAParam'", result.actualSql)
        assertEquals(listOf("name"), result.paramNames)
    }

    @Test
    fun `parse ignores line comments`() {
        val result = NamedParameterSql.parse(
            "SELECT * FROM t -- :notAParam\nWHERE name = :name"
        )
        assertEquals("SELECT * FROM t -- :notAParam\nWHERE name = ?", result.actualSql)
        assertEquals(listOf("name"), result.paramNames)
    }

    @Test
    fun `parse ignores block comments`() {
        val result = NamedParameterSql.parse(
            "SELECT * FROM t /* :notAParam */ WHERE name = :name"
        )
        assertEquals("SELECT * FROM t /* :notAParam */ WHERE name = ?", result.actualSql)
        assertEquals(listOf("name"), result.paramNames)
    }

    @Test
    fun `parse handles escaped quotes in string literals`() {
        val result = NamedParameterSql.parse(
            "SELECT * FROM t WHERE name = :name AND note = 'it''s :notAParam'"
        )
        assertEquals("SELECT * FROM t WHERE name = ? AND note = 'it''s :notAParam'", result.actualSql)
        assertEquals(listOf("name"), result.paramNames)
    }

    @Test
    fun `parse no named parameters returns original SQL`() {
        val sql = "SELECT * FROM t WHERE name = ?"
        val result = NamedParameterSql.parse(sql)
        assertEquals(sql, result.actualSql)
        assertEquals(emptyList(), result.paramNames)
    }

    @Test
    fun `parse complex products-search SQL`() {
        val sql = """
            SELECT * FROM products
            WHERE (:productLine IS NULL OR productLine = :productLine)
              AND (:minPrice IS NULL OR buyPrice >= :minPrice)
            ORDER BY productCode
        """.trimIndent()
        val result = NamedParameterSql.parse(sql)
        assertEquals(listOf("productLine", "productLine", "minPrice", "minPrice"), result.paramNames)
        assertTrue(result.actualSql.contains("?"))
        assertFalse(result.actualSql.contains(":productLine"))
        assertFalse(result.actualSql.contains(":minPrice"))
    }

    @Test
    fun `hasNamedParameters returns true for named params`() {
        assertTrue(NamedParameterSql.hasNamedParameters("SELECT * FROM t WHERE name = :name"))
    }

    @Test
    fun `hasNamedParameters returns false for positional params`() {
        assertFalse(NamedParameterSql.hasNamedParameters("SELECT * FROM t WHERE name = ?"))
    }

    @Test
    fun `hasNamedParameters returns false for cast operator`() {
        assertFalse(NamedParameterSql.hasNamedParameters("SELECT val::text FROM t"))
    }

    @Test
    fun `hasNamedParameters returns false for string literal`() {
        assertFalse(NamedParameterSql.hasNamedParameters("SELECT * FROM t WHERE status = ':notAParam'"))
    }

    @Test
    fun `parse parameter with underscore in name`() {
        val result = NamedParameterSql.parse("SELECT * FROM t WHERE product_line = :product_line")
        assertEquals("SELECT * FROM t WHERE product_line = ?", result.actualSql)
        assertEquals(listOf("product_line"), result.paramNames)
    }
}

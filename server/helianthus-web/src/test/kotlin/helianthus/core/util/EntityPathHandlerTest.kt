package helianthus.core.util

import helianthus.core.exception.InvalidOperationPathException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EntityPathHandlerTest {

    private val handler = EntityPathHandler()

    @Test
    fun `parsePath should parse list path with json format`() {
        val result = handler.parsePath("/api/entities/products.json")

        assertEquals("products", result.entityName)
        assertNull(result.id)
        assertEquals("json", result.format)
    }

    @Test
    fun `parsePath should parse list path with csv format`() {
        val result = handler.parsePath("/api/entities/products.csv")

        assertEquals("products", result.entityName)
        assertNull(result.id)
        assertEquals("csv", result.format)
    }

    @Test
    fun `parsePath should parse list path with xml format`() {
        val result = handler.parsePath("/api/entities/products.xml")

        assertEquals("products", result.entityName)
        assertNull(result.id)
        assertEquals("xml", result.format)
    }

    @Test
    fun `parsePath should parse list path with html format`() {
        val result = handler.parsePath("/api/entities/products.html")

        assertEquals("products", result.entityName)
        assertNull(result.id)
        assertEquals("html", result.format)
    }

    @Test
    fun `parsePath should parse get-by-id path`() {
        val result = handler.parsePath("/api/entities/products/S10_1678.json")

        assertEquals("products", result.entityName)
        assertEquals("S10_1678", result.id)
        assertEquals("json", result.format)
    }

    @Test
    fun `parsePath should parse get-by-id path with numeric id`() {
        val result = handler.parsePath("/api/entities/customers/103.json")

        assertEquals("customers", result.entityName)
        assertEquals("103", result.id)
        assertEquals("json", result.format)
    }

    @Test
    fun `parsePath should parse get-by-id path with csv format`() {
        val result = handler.parsePath("/api/entities/products/S10_1678.csv")

        assertEquals("products", result.entityName)
        assertEquals("S10_1678", result.id)
        assertEquals("csv", result.format)
    }

    @Test
    fun `parsePath should handle entity names with hyphens`() {
        val result = handler.parsePath("/api/entities/product-lines.json")

        assertEquals("product-lines", result.entityName)
        assertNull(result.id)
        assertEquals("json", result.format)
    }

    @Test
    fun `parsePath should handle entity names with underscores`() {
        val result = handler.parsePath("/api/entities/product_lines.json")

        assertEquals("product_lines", result.entityName)
        assertNull(result.id)
        assertEquals("json", result.format)
    }

    @Test
    fun `parsePath should throw for null path`() {
        val exception = assertThrows<InvalidOperationPathException> {
            handler.parsePath(null)
        }
        assertEquals("path must not be null or blank", exception.message)
    }

    @Test
    fun `parsePath should throw for blank path`() {
        val exception = assertThrows<InvalidOperationPathException> {
            handler.parsePath("   ")
        }
        assertEquals("path must not be null or blank", exception.message)
    }

    @Test
    fun `parsePath should throw for path without prefix`() {
        val exception = assertThrows<InvalidOperationPathException> {
            handler.parsePath("/api/operations/products.json")
        }
        assertEquals("path must start with /api/entities/", exception.message)
    }

    @Test
    fun `parsePath should throw for path without entity name`() {
        val exception = assertThrows<InvalidOperationPathException> {
            handler.parsePath("/api/entities/")
        }
        assertEquals("path must contain an entity name", exception.message)
    }

    @Test
    fun `parsePath should throw for path without format extension`() {
        val exception = assertThrows<InvalidOperationPathException> {
            handler.parsePath("/api/entities/products")
        }
        assertEquals("path must contain a format extension: '/api/entities/products'", exception.message)
    }

    @Test
    fun `parsePath should throw for path with empty format`() {
        val exception = assertThrows<InvalidOperationPathException> {
            handler.parsePath("/api/entities/products.")
        }
        assertEquals("format must not be blank: '/api/entities/products.'", exception.message)
    }

    @Test
    fun `parsePath should throw for get-by-id path with blank id`() {
        val exception = assertThrows<InvalidOperationPathException> {
            handler.parsePath("/api/entities/products/.json")
        }
        assertEquals("entity id must not be blank: '/api/entities/products/.json'", exception.message)
    }

    @Test
    fun `parsePath should handle id with special characters`() {
        val result = handler.parsePath("/api/entities/products/S10-1678-v2.json")

        assertEquals("products", result.entityName)
        assertEquals("S10-1678-v2", result.id)
        assertEquals("json", result.format)
    }
}

package helianthus.core.util

import helianthus.core.exception.InvalidOperationPathException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PathHandlerTest {

    private val pathHandler = PathHandler()

    @Test
    fun `parsePath should extract operationId and format for legacy path`() {
        val result = pathHandler.parsePath("/api/op/all-products.json")
        assertEquals("all-products", result.operationId)
        assertEquals("json", result.format)
        assertNull(result.configurationId)
    }

    @Test
    fun `parsePath should handle parameterized query path`() {
        val result = pathHandler.parsePath("/api/op/get-product.json")
        assertEquals("get-product", result.operationId)
        assertEquals("json", result.format)
        assertNull(result.configurationId)
    }

    @Test
    fun `parsePath should handle named configuration path`() {
        val result = pathHandler.parsePath("/api/op/users/all.json")
        assertEquals("users", result.operationId)
        assertEquals("all", result.configurationId)
        assertEquals("json", result.format)
    }

    @Test
    fun `parsePath should handle multi-segment operation id with configuration`() {
        val result = pathHandler.parsePath("/api/op/reports/sales-by-month.html")
        assertEquals("reports", result.operationId)
        assertEquals("sales-by-month", result.configurationId)
        assertEquals("html", result.format)
    }

    @Test
    fun `parsePath should handle deep operation id with configuration`() {
        val result = pathHandler.parsePath("/api/op/admin/users/export.csv")
        assertEquals("admin/users", result.operationId)
        assertEquals("export", result.configurationId)
        assertEquals("csv", result.format)
    }

    @Test
    fun `parsePath should use last dot to split format`() {
        val result = pathHandler.parsePath("/api/op/data.v1.json")
        assertEquals("data.v1", result.operationId)
        assertEquals("json", result.format)
        assertNull(result.configurationId)
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = ["/"])
    fun `parsePath should reject null blank or root path`(path: String?) {
        assertThrows<InvalidOperationPathException> {
            pathHandler.parsePath(path)
        }
    }

    @Test
    fun `parsePath should reject path not starting with api prefix`() {
        assertThrows<InvalidOperationPathException> {
            pathHandler.parsePath("/products.json")
        }
    }

    @Test
    fun `parsePath should reject path with no format extension`() {
        assertThrows<InvalidOperationPathException> {
            pathHandler.parsePath("/api/op/all-products")
        }
    }

    @Test
    fun `parsePath should reject path with blank operation id`() {
        assertThrows<InvalidOperationPathException> {
            pathHandler.parsePath("/api/op/.json")
        }
    }

    @Test
    fun `parsePath should reject path with blank format`() {
        assertThrows<InvalidOperationPathException> {
            pathHandler.parsePath("/api/op/all-products.")
        }
    }

    @Test
    fun `parsePath should reject path with only prefix`() {
        assertThrows<InvalidOperationPathException> {
            pathHandler.parsePath("/api/op/")
        }
    }
}

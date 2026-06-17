package helianthus.core.util

import helianthus.core.exception.InvalidOperationPathException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

class PathHandlerTest {

    private val pathHandler = PathHandler()

    @Test
    fun `parsePath should extract operationId and format for valid path`() {
        val result = pathHandler.parsePath("/api/op/all-products.json")
        assertEquals("/all-products", result.operationId)
        assertEquals("json", result.format)
    }

    @Test
    fun `parsePath should handle parameterized query path`() {
        val result = pathHandler.parsePath("/api/op/get-product.json")
        assertEquals("/get-product", result.operationId)
        assertEquals("json", result.format)
    }

    @Test
    fun `parsePath should handle multi-segment operation id`() {
        val result = pathHandler.parsePath("/api/op/reports/sales-by-month.html")
        assertEquals("/reports/sales-by-month", result.operationId)
        assertEquals("html", result.format)
    }

    @Test
    fun `parsePath should use last dot to split`() {
        val result = pathHandler.parsePath("/api/op/data.v1.json")
        assertEquals("/data.v1", result.operationId)
        assertEquals("json", result.format)
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

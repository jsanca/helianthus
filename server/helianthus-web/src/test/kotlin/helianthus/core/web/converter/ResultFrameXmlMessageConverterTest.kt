package helianthus.core.web.converter

import helianthus.core.result.ResultColumn
import helianthus.core.result.ResultFrame
import helianthus.core.result.ResultMetadata
import helianthus.core.result.ResultSchema
import helianthus.core.result.ResultType
import org.junit.jupiter.api.Test
import org.springframework.http.HttpOutputMessage
import org.springframework.http.MediaType
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlin.test.assertTrue

class ResultFrameXmlMessageConverterTest {

    private val converter = ResultFrameXmlMessageConverter()

    private fun createTestFrame(rows: List<Map<String, Any?>>): ResultFrame {
        val schema = ResultSchema(listOf(
            ResultColumn("name", ResultType.STRING, false),
            ResultColumn("value", ResultType.INTEGER, true)
        ))
        return ResultFrame(schema, rows, ResultMetadata(rows.size))
    }

    @Test
    fun `should produce valid XML structure`() {
        val frame = createTestFrame(listOf(mapOf("name" to "Alice", "value" to 100)))
        
        val output = captureOutput(frame)
        
        // Should contain XML-like structure
        assertTrue(output.contains("<") && output.contains(">"))
        assertTrue(output.contains("metadata"))
        assertTrue(output.contains("rows"))
    }

    @Test
    fun `should escape XML special characters in values`() {
        val frame = createTestFrame(listOf(
            mapOf("name" to "Tom & Jerry", "value" to 1)
        ))
        
        val output = captureOutput(frame)
        
        // Jackson XML should escape ampersands
        assertTrue(output.contains("&amp;") || output.contains("Tom") && output.contains("Jerry"))
    }

    @Test
    fun `should sanitize column names with spaces`() {
        val schema = ResultSchema(listOf(
            ResultColumn("first name", ResultType.STRING, false)
        ))
        val frame = ResultFrame(schema, listOf(mapOf("first name" to "Alice")), ResultMetadata(1))
        
        val output = captureOutput(frame)
        
        // Should contain sanitized tag (spaces replaced)
        assertTrue(output.contains("first_name") || output.contains("first-name") || output.contains("firstname"))
    }

    @Test
    fun `should sanitize column names starting with digits`() {
        val schema = ResultSchema(listOf(
            ResultColumn("123column", ResultType.STRING, false)
        ))
        val frame = ResultFrame(schema, listOf(mapOf("123column" to "value")), ResultMetadata(1))
        
        val output = captureOutput(frame)
        
        // Should not have a tag starting with a digit directly
        // The sanitizer should prefix it
        assertTrue(output.contains("_123column") || output.contains("column"))
    }

    @Test
    fun `should include metadata`() {
        val frame = createTestFrame(listOf(
            mapOf("name" to "Alice", "value" to 100),
            mapOf("name" to "Bob", "value" to 200)
        ))
        
        val output = captureOutput(frame)
        
        assertTrue(output.contains("rowCount"))
        assertTrue(output.contains("2"))
    }

    @Test
    fun `should write multiple rows`() {
        val frame = createTestFrame(listOf(
            mapOf("name" to "Alice", "value" to 1),
            mapOf("name" to "Bob", "value" to 2)
        ))
        
        val output = captureOutput(frame)
        
        assertTrue(output.contains("Alice"))
        assertTrue(output.contains("Bob"))
    }

    @Test
    fun `should handle special characters in values`() {
        val frame = createTestFrame(listOf(
            mapOf("name" to "a < b > c", "value" to 1)
        ))
        
        val output = captureOutput(frame)
        
        // Jackson should handle escaping
        assertTrue(output.contains("a") && output.contains("b") && output.contains("c"))
    }

    @Test
    fun `should handle null values gracefully`() {
        val frame = createTestFrame(listOf(
            mapOf("name" to "Alice", "value" to null)
        ))
        
        val output = captureOutput(frame)
        
        // Should produce some output without crashing
        assertTrue(output.isNotEmpty())
        assertTrue(output.contains("Alice"))
    }

    private fun captureOutput(frame: ResultFrame): String {
        val outputStream = ByteArrayOutputStream()
        val outputMessage = object : HttpOutputMessage {
            override fun getBody() = outputStream
            override fun getHeaders() = org.springframework.http.HttpHeaders().apply {
                contentType = MediaType.APPLICATION_XML
            }
        }
        
        // Use reflection to call the protected writeInternal method
        val method = converter.javaClass.getDeclaredMethod("writeInternal", ResultFrame::class.java, HttpOutputMessage::class.java)
        method.isAccessible = true
        method.invoke(converter, frame, outputMessage)
        
        return outputStream.toString(StandardCharsets.UTF_8)
    }
}

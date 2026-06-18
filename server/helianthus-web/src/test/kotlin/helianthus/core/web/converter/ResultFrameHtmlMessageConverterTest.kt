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

class ResultFrameHtmlMessageConverterTest {

    private val converter = ResultFrameHtmlMessageConverter()

    private fun createTestFrame(rows: List<Map<String, Any?>>): ResultFrame {
        val schema = ResultSchema(listOf(
            ResultColumn("name", ResultType.STRING, false),
            ResultColumn("value", ResultType.INTEGER, true)
        ))
        return ResultFrame(schema, rows, ResultMetadata(rows.size))
    }

    @Test
    fun `should write HTML document structure`() {
        val frame = createTestFrame(listOf(mapOf("name" to "Alice", "value" to 100)))
        
        val output = captureOutput(frame)
        
        assertTrue(output.contains("<!DOCTYPE html>"))
        assertTrue(output.contains("<html"))
        assertTrue(output.contains("</html>"))
        assertTrue(output.contains("<head>"))
        assertTrue(output.contains("<body>"))
    }

    @Test
    fun `should write table structure`() {
        val frame = createTestFrame(listOf(mapOf("name" to "Alice", "value" to 100)))
        
        val output = captureOutput(frame)
        
        assertTrue(output.contains("<table>"))
        assertTrue(output.contains("<thead>"))
        assertTrue(output.contains("<tbody>"))
        assertTrue(output.contains("<th>name</th>"))
        assertTrue(output.contains("<th>value</th>"))
        assertTrue(output.contains("<td>Alice</td>"))
        assertTrue(output.contains("<td>100</td>"))
    }

    @Test
    fun `should escape HTML special characters in column names`() {
        val schema = ResultSchema(listOf(
            ResultColumn("name<script>", ResultType.STRING, false)
        ))
        val frame = ResultFrame(schema, listOf(mapOf("name<script>" to "value")), ResultMetadata(1))
        
        val output = captureOutput(frame)
        
        assertTrue(output.contains("&lt;script&gt;"))
        assertTrue(!output.contains("<script>"))
    }

    @Test
    fun `should escape HTML special characters in values`() {
        val frame = createTestFrame(listOf(
            mapOf("name" to "Tom & Jerry", "value" to 1)
        ))
        
        val output = captureOutput(frame)
        
        assertTrue(output.contains("Tom &amp; Jerry"))
    }

    @Test
    fun `should escape less than and greater than in values`() {
        val frame = createTestFrame(listOf(
            mapOf("name" to "a < b > c", "value" to 1)
        ))
        
        val output = captureOutput(frame)
        
        assertTrue(output.contains("a &lt; b &gt; c"))
    }

    @Test
    fun `should escape quotes in values`() {
        val frame = createTestFrame(listOf(
            mapOf("name" to "He said \"hello\"", "value" to 1)
        ))
        
        val output = captureOutput(frame)
        
        assertTrue(output.contains("&quot;"))
    }

    @Test
    fun `should include row count metadata`() {
        val frame = createTestFrame(listOf(
            mapOf("name" to "Alice", "value" to 1),
            mapOf("name" to "Bob", "value" to 2)
        ))
        
        val output = captureOutput(frame)
        
        assertTrue(output.contains("2 row(s)"))
    }

    @Test
    fun `should handle null values`() {
        val frame = createTestFrame(listOf(
            mapOf("name" to "Alice", "value" to null)
        ))
        
        val output = captureOutput(frame)
        
        assertTrue(output.contains("<td></td>"))
    }

    @Test
    fun `should write multiple rows`() {
        val frame = createTestFrame(listOf(
            mapOf("name" to "Alice", "value" to 1),
            mapOf("name" to "Bob", "value" to 2),
            mapOf("name" to "Charlie", "value" to 3)
        ))
        
        val output = captureOutput(frame)
        
        assertTrue(output.contains("<td>Alice</td>"))
        assertTrue(output.contains("<td>Bob</td>"))
        assertTrue(output.contains("<td>Charlie</td>"))
    }

    @Test
    fun `should include CSS styles`() {
        val frame = createTestFrame(listOf(mapOf("name" to "Alice", "value" to 100)))
        
        val output = captureOutput(frame)
        
        assertTrue(output.contains("<style>"))
        assertTrue(output.contains("border-collapse"))
    }

    private fun captureOutput(frame: ResultFrame): String {
        val outputStream = ByteArrayOutputStream()
        val outputMessage = object : HttpOutputMessage {
            override fun getBody() = outputStream
            override fun getHeaders() = org.springframework.http.HttpHeaders().apply {
                contentType = MediaType.TEXT_HTML
            }
        }
        
        // Use reflection to call the protected writeInternal method
        val method = converter.javaClass.getDeclaredMethod("writeInternal", ResultFrame::class.java, HttpOutputMessage::class.java)
        method.isAccessible = true
        method.invoke(converter, frame, outputMessage)
        
        return outputStream.toString(StandardCharsets.UTF_8)
    }
}

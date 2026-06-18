package helianthus.core.web.converter

import helianthus.core.result.ResultColumn
import helianthus.core.result.ResultFrame
import helianthus.core.result.ResultMetadata
import helianthus.core.result.ResultSchema
import helianthus.core.result.ResultType
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.junit.jupiter.api.Test
import org.springframework.http.HttpOutputMessage
import org.springframework.http.MediaType
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResultFrameCsvMessageConverterTest {

    private val converter = ResultFrameCsvMessageConverter()

    private fun createTestFrame(rows: List<Map<String, Any?>>): ResultFrame {
        val schema = ResultSchema(listOf(
            ResultColumn("name", ResultType.STRING, false),
            ResultColumn("description", ResultType.STRING, true),
            ResultColumn("value", ResultType.INTEGER, true)
        ))
        return ResultFrame(schema, rows, ResultMetadata(rows.size))
    }

    @Test
    fun `should write CSV with headers`() {
        val frame = createTestFrame(listOf(
            mapOf("name" to "Alice", "description" to "Engineer", "value" to 100)
        ))
        
        val output = captureOutput(frame)
        val lines = output.lines().filter { it.isNotBlank() }
        
        assertEquals(2, lines.size)
        assertEquals("name,description,value", lines[0])
        assertEquals("Alice,Engineer,100", lines[1])
    }

    @Test
    fun `should escape commas in CSV fields`() {
        val frame = createTestFrame(listOf(
            mapOf("name" to "Smith, John", "description" to "Developer", "value" to 50)
        ))
        
        val output = captureOutput(frame)
        
        assertTrue(output.contains("\"Smith, John\""))
    }

    @Test
    fun `should escape quotes in CSV fields`() {
        val frame = createTestFrame(listOf(
            mapOf("name" to "He said \"hello\"", "description" to "Test", "value" to 1)
        ))
        
        val output = captureOutput(frame)
        
        assertTrue(output.contains("\"He said \"\"hello\"\"\""))
    }

    @Test
    fun `should escape newlines in CSV fields`() {
        val frame = createTestFrame(listOf(
            mapOf("name" to "Line1\nLine2", "description" to "Test", "value" to 1)
        ))
        
        val output = captureOutput(frame)
        
        assertTrue(output.contains("\"Line1\nLine2\""))
    }

    @Test
    fun `should handle null values`() {
        val frame = createTestFrame(listOf(
            mapOf("name" to "Alice", "description" to null, "value" to 100)
        ))
        
        val output = captureOutput(frame)
        
        assertTrue(output.contains("Alice,,100"))
    }

    @Test
    fun `should write multiple rows`() {
        val frame = createTestFrame(listOf(
            mapOf("name" to "Alice", "description" to "A", "value" to 1),
            mapOf("name" to "Bob", "description" to "B", "value" to 2),
            mapOf("name" to "Charlie", "description" to "C", "value" to 3)
        ))
        
        val output = captureOutput(frame)
        val lines = output.lines().filter { it.isNotBlank() }
        
        assertEquals(4, lines.size) // header + 3 rows
    }

    @Test
    fun `should be parseable by Commons CSV`() {
        val frame = createTestFrame(listOf(
            mapOf("name" to "Alice, Bob", "description" to "Test \"quoted\"", "value" to 100)
        ))
        
        val output = captureOutput(frame)
        
        val parser = CSVParser.parse(output, CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build())
        val records = parser.records
        
        assertEquals(1, records.size)
        assertEquals("Alice, Bob", records[0].get("name"))
        assertEquals("Test \"quoted\"", records[0].get("description"))
        assertEquals("100", records[0].get("value"))
    }

    private fun captureOutput(frame: ResultFrame): String {
        val outputStream = ByteArrayOutputStream()
        val outputMessage = object : HttpOutputMessage {
            override fun getBody() = outputStream
            override fun getHeaders() = org.springframework.http.HttpHeaders().apply {
                contentType = MediaType.parseMediaType("text/csv")
            }
        }
        
        // Use reflection to call the protected writeInternal method
        val method = converter.javaClass.getDeclaredMethod("writeInternal", ResultFrame::class.java, HttpOutputMessage::class.java)
        method.isAccessible = true
        method.invoke(converter, frame, outputMessage)
        
        return outputStream.toString(StandardCharsets.UTF_8)
    }
}

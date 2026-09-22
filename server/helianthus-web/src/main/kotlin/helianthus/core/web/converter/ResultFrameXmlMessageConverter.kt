package helianthus.core.web.converter

import tools.jackson.dataformat.xml.XmlMapper
import helianthus.core.result.ColumnNameResolver
import helianthus.core.result.ResultFrame
import org.slf4j.LoggerFactory
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpOutputMessage
import org.springframework.http.MediaType
import org.springframework.http.converter.AbstractHttpMessageConverter
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.http.converter.HttpMessageNotWritableException
import java.io.IOException

/**
 * Spring HTTP message converter that serializes a [ResultFrame] as
 * `application/xml`.
 *
 * Column names are sanitized to be valid XML tag names (letters/digits/`_`/`-`/`.`,
 * and prefixed with `_` if they would otherwise start with a digit, `-`, or `.`).
 * Reading XML into a [ResultFrame] is not supported.
 */
class ResultFrameXmlMessageConverter : AbstractHttpMessageConverter<ResultFrame>(MediaType.APPLICATION_XML) {

    private val log = LoggerFactory.getLogger(ResultFrameXmlMessageConverter::class.java)
    private val xmlMapper = XmlMapper()

    /** Only supports serializing [ResultFrame] instances. */
    override fun supports(clazz: Class<*>): Boolean {
        return ResultFrame::class.java.isAssignableFrom(clazz)
    }

    /**
     * Writes the [ResultFrame] as XML to the response body. The output is a
     * `<root>` document containing a `<metadata>` element (with `rowCount`) and
     * a `<rows>` element with one `<row>` child per row.
     */
    @Throws(HttpMessageNotWritableException::class, IOException::class)
    override fun writeInternal(resultFrame: ResultFrame, outputMessage: HttpOutputMessage) {
        log.debug("Writing ResultFrame as XML: {} rows", resultFrame.metadata.rowCount)

        val columns = resultFrame.schema.columns

        // Transform ResultFrame to a structure suitable for XML serialization
        // with sanitized column names as XML tags
        val xmlStructure = mapOf(
            "metadata" to mapOf("rowCount" to resultFrame.metadata.rowCount),
            "rows" to resultFrame.rows.map { row ->
                val sanitizedRow = mutableMapOf<String, Any?>()
                columns.forEach { col ->
                    val sanitizedTagName = sanitizeXmlTagName(col.name)
                    sanitizedRow[sanitizedTagName] = ColumnNameResolver.getRowValue(row, col.name)
                }
                mapOf("row" to sanitizedRow)
            }
        )

        xmlMapper.writeValue(outputMessage.body, xmlStructure)
    }

    /**
     * Sanitizes [name] for use as an XML tag name.
     *
     * Allowed characters are letters, digits, `_`, `-`, and `.`; spaces and any
     * other characters become `_`. The result is prefixed with `_` if it would
     * otherwise start with a digit, `-`, or `.` (none of which are valid XML tag
     * name leading characters).
     */
    private fun sanitizeXmlTagName(name: String): String {
        if (name.isEmpty()) return "_"

        val sanitized = buildString {
            name.forEachIndexed { index, char ->
                when {
                    char.isLetterOrDigit() -> append(char)
                    char == '_' || char == '-' || char == '.' -> append(char)
                    char == ' ' -> append('_')
                    else -> append('_')
                }
            }
        }

        // XML tag names cannot start with a digit, hyphen, or period
        return if (sanitized[0].isDigit() || sanitized[0] == '-' || sanitized[0] == '.') {
            "_$sanitized"
        } else {
            sanitized
        }
    }

    /** Always throws — reading [ResultFrame] from XML is not supported. */
    @Throws(HttpMessageNotReadableException::class, IOException::class)
    override fun readInternal(clazz: Class<out ResultFrame>, inputMessage: HttpInputMessage): ResultFrame {
        throw UnsupportedOperationException("Reading ResultFrame from XML is not supported")
    }
}

package helianthus.core.web.converter

import helianthus.core.result.ColumnNameResolver
import helianthus.core.result.ResultFrame
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.QuoteMode
import org.slf4j.LoggerFactory
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpOutputMessage
import org.springframework.http.MediaType
import org.springframework.http.converter.AbstractHttpMessageConverter
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.http.converter.HttpMessageNotWritableException
import java.io.IOException

/**
 * Spring HTTP message converter that serializes a [ResultFrame] as `text/csv`.
 *
 * Uses Apache Commons CSV with `QuoteMode.MINIMAL`. Reading CSV into a
 * [ResultFrame] is not supported.
 */
class ResultFrameCsvMessageConverter : AbstractHttpMessageConverter<ResultFrame>(MediaType.parseMediaType("text/csv")) {

    private val log = LoggerFactory.getLogger(ResultFrameCsvMessageConverter::class.java)

    /** Only supports serializing [ResultFrame] instances. */
    override fun supports(clazz: Class<*>): Boolean {
        return ResultFrame::class.java.isAssignableFrom(clazz)
    }

    /**
     * Writes the [ResultFrame] as CSV to the response body. Cell values are
     * looked up case-insensitively via [ColumnNameResolver].
     */
    @Throws(HttpMessageNotWritableException::class, IOException::class)
    override fun writeInternal(resultFrame: ResultFrame, outputMessage: HttpOutputMessage) {
        log.debug("Writing ResultFrame as CSV: {} rows", resultFrame.metadata.rowCount)

        val columns = resultFrame.schema.columns
        val headers = columns.map { it.name }.toTypedArray()

        val format = CSVFormat.DEFAULT
            .builder()
            .setHeader(*headers)
            .setQuoteMode(QuoteMode.MINIMAL)
            .build()

        outputMessage.body.writer().use { writer ->
            format.print(writer).use { printer ->
                resultFrame.rows.forEach { row ->
                    val values = columns.map { col -> ColumnNameResolver.getRowValue(row, col.name) }
                    printer.printRecord(values)
                }
            }
        }
    }

    /** Always throws — reading [ResultFrame] from CSV is not supported. */
    @Throws(HttpMessageNotReadableException::class, IOException::class)
    override fun readInternal(clazz: Class<out ResultFrame>, inputMessage: HttpInputMessage): ResultFrame {
        throw UnsupportedOperationException("Reading ResultFrame from CSV is not supported")
    }
}

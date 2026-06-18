package helianthus.core.web.converter

import helianthus.core.result.ResultFrame
import org.slf4j.LoggerFactory
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpOutputMessage
import org.springframework.http.MediaType
import org.springframework.http.converter.AbstractHttpMessageConverter
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.http.converter.HttpMessageNotWritableException
import java.io.IOException

class ResultFrameCsvMessageConverter : AbstractHttpMessageConverter<ResultFrame>(MediaType.parseMediaType("text/csv")) {

    private val log = LoggerFactory.getLogger(ResultFrameCsvMessageConverter::class.java)

    override fun supports(clazz: Class<*>): Boolean {
        return ResultFrame::class.java.isAssignableFrom(clazz)
    }

    @Throws(HttpMessageNotWritableException::class, IOException::class)
    override fun writeInternal(resultFrame: ResultFrame, outputMessage: HttpOutputMessage) {
        log.debug("Writing ResultFrame as CSV: {} rows", resultFrame.metadata.rowCount)
        TODO("CSV rendering not yet implemented")
    }

    @Throws(HttpMessageNotReadableException::class, IOException::class)
    override fun readInternal(clazz: Class<out ResultFrame>, inputMessage: HttpInputMessage): ResultFrame {
        throw UnsupportedOperationException("Reading ResultFrame from CSV is not supported")
    }
}

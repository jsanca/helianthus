package helianthus.core.web.converter

import helianthus.core.result.ResultFrame
import org.slf4j.LoggerFactory
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpOutputMessage
import org.springframework.http.MediaType
import org.springframework.http.converter.AbstractHttpMessageConverter
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.http.converter.HttpMessageNotWritableException
import tools.jackson.databind.ObjectMapper
import java.io.IOException

class ResultFrameJsonMessageConverter : AbstractHttpMessageConverter<ResultFrame>(MediaType.APPLICATION_JSON) {

    private val log = LoggerFactory.getLogger(ResultFrameJsonMessageConverter::class.java)
    private val objectMapper = ObjectMapper()

    override fun supports(clazz: Class<*>): Boolean {
        return ResultFrame::class.java.isAssignableFrom(clazz)
    }

    @Throws(HttpMessageNotWritableException::class, IOException::class)
    override fun writeInternal(resultFrame: ResultFrame, outputMessage: HttpOutputMessage) {
        log.debug("Writing ResultFrame as JSON: {} rows", resultFrame.metadata.rowCount)
        objectMapper.writeValue(outputMessage.body, resultFrame)
    }

    @Throws(HttpMessageNotReadableException::class, IOException::class)
    override fun readInternal(clazz: Class<out ResultFrame>, inputMessage: HttpInputMessage): ResultFrame {
        throw UnsupportedOperationException("Reading ResultFrame from JSON is not supported")
    }
}

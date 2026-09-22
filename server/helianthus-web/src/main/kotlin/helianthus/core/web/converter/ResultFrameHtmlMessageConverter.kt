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

/**
 * Spring HTTP message converter that serializes a [ResultFrame] as
 * `text/html` using [ResultFrameHtmlRenderer].
 *
 * Reading HTML into a [ResultFrame] is not supported.
 */
class ResultFrameHtmlMessageConverter : AbstractHttpMessageConverter<ResultFrame>(MediaType.TEXT_HTML) {

    private val log = LoggerFactory.getLogger(ResultFrameHtmlMessageConverter::class.java)
    private val renderer = ResultFrameHtmlRenderer()

    /** Only supports serializing [ResultFrame] instances. */
    override fun supports(clazz: Class<*>): Boolean {
        return ResultFrame::class.java.isAssignableFrom(clazz)
    }

    /**
     * Renders [resultFrame] via [ResultFrameHtmlRenderer] and writes the HTML
     * document to the response body.
     */
    @Throws(HttpMessageNotWritableException::class, IOException::class)
    override fun writeInternal(resultFrame: ResultFrame, outputMessage: HttpOutputMessage) {
        log.debug("Writing ResultFrame as HTML: {} rows", resultFrame.metadata.rowCount)
        val html = renderer.render(resultFrame)
        outputMessage.body.writer().use { it.write(html) }
    }

    /** Always throws — reading [ResultFrame] from HTML is not supported. */
    @Throws(HttpMessageNotReadableException::class, IOException::class)
    override fun readInternal(clazz: Class<out ResultFrame>, inputMessage: HttpInputMessage): ResultFrame {
        throw UnsupportedOperationException("Reading ResultFrame from HTML is not supported")
    }
}

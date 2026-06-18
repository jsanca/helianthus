package helianthus.core.marshall.tableresult

import helianthus.core.MarshallFormatterException
import helianthus.core.marshall.ResultFrameMarshaller
import helianthus.core.result.ResultFrame
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.io.OutputStream

@Component
class JacksonResultFrameMarshallFormatter : ResultFrameMarshaller {

    override fun process(outputStream: OutputStream, resultFrame: ResultFrame) {
        try {
            objectMapper.writeValue(outputStream, resultFrame)
        } catch (e: Exception) {
            throw MarshallFormatterException(e)
        }
    }

    companion object {
        private val objectMapper = ObjectMapper()
    }
}

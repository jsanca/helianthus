package helianthus.core.marshall

import helianthus.core.result.ResultFrame
import java.io.OutputStream
import java.io.Serializable

interface ResultFrameMarshaller : Serializable {
    fun process(outputStream: OutputStream, resultFrame: ResultFrame)
}

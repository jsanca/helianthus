package helianthus.core.marshall

import java.io.Serializable

class MarshallFormatFactory : Serializable {

    private val resultFrameMarshallerMap: MutableMap<String, ResultFrameMarshaller> = HashMap()

    fun getResultFrameMarshaller(format: String): ResultFrameMarshaller? {
        return resultFrameMarshallerMap[format]
    }

    fun setResultFrameMarshallerMap(map: MutableMap<String, ResultFrameMarshaller>) {
        resultFrameMarshallerMap.clear()
        resultFrameMarshallerMap.putAll(map)
    }
}

package helianthus.core.pipeline

import helianthus.core.result.CloseableRowStream
import helianthus.core.result.DefaultRowStream
import helianthus.core.result.ResultFrame
import helianthus.core.result.ResultMetadata
import helianthus.core.result.ResultSchema

object RowStream {

    fun fromRows(schema: ResultSchema, rows: List<Map<String, Any?>>): CloseableRowStream {
        return DefaultRowStream.fromRows(schema, rows)
    }

    fun toResultFrame(stream: CloseableRowStream): ResultFrame {
        val rows = stream.rows.toList()
        return ResultFrame(stream.schema, rows, ResultMetadata(rows.size))
    }
}

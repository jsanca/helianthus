package helianthus.core.pipeline

import helianthus.core.result.CloseableRowStream
import helianthus.core.result.DefaultRowStream
import helianthus.core.result.ResultFrame
import helianthus.core.result.ResultMetadata
import helianthus.core.result.ResultSchema

/**
 * Small adapter between [CloseableRowStream] (streaming, lazy) and
 * [ResultFrame] (fully-materialized).
 */
internal object RowStream {

    /**
     * Wraps a fully-materialized row list in a closeable stream.
     */
    fun fromRows(schema: ResultSchema, rows: List<Map<String, Any?>>): CloseableRowStream {
        return DefaultRowStream.fromRows(schema, rows)
    }

    /**
     * Materializes [stream] into a [ResultFrame], draining the rows eagerly
     * and recording the row count in metadata.
     */
    fun toResultFrame(stream: CloseableRowStream): ResultFrame {
        val rows = stream.rows.toList()
        return ResultFrame(stream.schema, rows, ResultMetadata(rows.size))
    }
}

package helianthus.core.pipeline

import org.slf4j.LoggerFactory

/**
 * Pipeline step that materializes the row stream into a [helianthus.core.result.ResultFrame].
 *
 * Consumes the [helianthus.core.result.CloseableRowStream] from the context,
 * collects all rows into memory, and produces a ResultFrame with schema and metadata.
 * Closes the stream after materialization.
 */
class ToResultFrameStep : PipelineComponent {

    private val log = LoggerFactory.getLogger(ToResultFrameStep::class.java)

    override fun process(context: PipelineContext): PipelineContext {
        val stream = context.rowStream
                ?: throw IllegalStateException("No row stream to materialize")

        try {
            stream.use { s ->
                val frame = RowStream.toResultFrame(s)
                context.resultFrame = frame
                log.debug("Result frame materialized: {} rows, {} columns",
                        frame.metadata.rowCount, frame.schema.columnCount)
            }
        } finally {
            context.rowStream = null
        }

        return context
    }
}

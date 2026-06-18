package helianthus.core.pipeline

import org.slf4j.LoggerFactory

/**
 * Pipeline step that limits the number of rows in the stream.
 *
 * Reads the `limit` configuration from the resolved operation and applies
 * a take transformation to the row stream. If no limit is configured,
 * the stream passes through unchanged.
 */
class LimitStep : PipelineComponent {

    private val log = LoggerFactory.getLogger(LimitStep::class.java)

    override fun process(context: PipelineContext): PipelineContext {
        val limit = context.resolvedOperation?.pipelineConfig?.limit
                ?: return context

        val stream = context.rowStream
                ?: throw IllegalStateException("No row stream to limit")

        context.rowStream = stream.transformRows { rows -> rows.take(limit) }

        log.debug("Applied row limit: {}", limit)
        return context
    }
}

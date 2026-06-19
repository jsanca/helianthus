package helianthus.core.pipeline

import helianthus.core.result.CloseableRowStream
import helianthus.core.result.ColumnNameResolver
import org.slf4j.LoggerFactory

/**
 * Pipeline step that projects (selects) specific columns from the row stream.
 *
 * Reads the `project` configuration from the resolved operation and applies
 * a column selection transform to the stream. If no project configuration
 * is present, the stream passes through unchanged.
 *
 * Column names are resolved case-insensitively, and the projection order
 * follows the order specified in the configuration.
 */
class ProjectStep : PipelineComponent {

    private val log = LoggerFactory.getLogger(ProjectStep::class.java)

    override fun process(context: PipelineContext): PipelineContext {
        val columns = context.resolvedOperation?.pipelineConfig?.project
                ?: return context
        if (columns.isEmpty()) return context

        val stream = context.rowStream
                ?: throw IllegalStateException("No row stream to project")

        val newColumns = ColumnNameResolver.resolveColumns(columns, stream.schema)
        val newSchema = helianthus.core.result.ResultSchema(newColumns)

        context.rowStream = stream.withSchema(newSchema).transformRows { rows ->
            rows.map { row ->
                val projected = LinkedHashMap<String, Any?>()
                for (col in newColumns) {
                    projected[col.name] = ColumnNameResolver.getRowValueOrThrow(row, col.name)
                }
                projected as Map<String, Any?>
            }
        }

        log.debug("Projected to {} columns", newColumns.size)
        return context
    }
}

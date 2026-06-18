package helianthus.core.pipeline

import org.slf4j.LoggerFactory

/**
 * Pipeline step that filters rows based on configured column conditions.
 *
 * Reads the `filter` configuration from the resolved operation and applies
 * a filtering transform to the row stream. Supports both equality conditions
 * and operator-based conditions (e.g., gt, lt, like). If no filter is
 * configured, the stream passes through unchanged.
 *
 * @param operatorRegistry registry of filter operators available for conditions
 */
class FilterStep(
    private val operatorRegistry: FilterOperatorRegistry = FilterOperatorRegistry()
) : PipelineComponent {

    private val log = LoggerFactory.getLogger(FilterStep::class.java)

    override fun process(context: PipelineContext): PipelineContext {
        val filterConfig = context.resolvedOperation?.pipelineConfig?.filter
                ?: return context
        if (filterConfig.isEmpty()) return context

        val stream = context.rowStream
                ?: throw IllegalStateException("No row stream to filter")

        val transformed = stream.transformRows { rows ->
            rows.filter { row ->
                (filterConfig as Map<String, Any>).all { (column, condition) ->
                    val cellValue = row[column]
                    when (condition) {
                        is Map<*, *> -> {
                            @Suppress("UNCHECKED_CAST")
                            operatorRegistry.evaluateCondition(
                                    cellValue, condition as Map<String, Any?>)
                        }
                        else -> cellValue == condition
                    }
                }
            }
        }

        context.rowStream = transformed

        log.debug("Applied filter with columns: {}", filterConfig.keys)
        return context
    }
}

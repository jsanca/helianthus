package helianthus.core.pipeline

import helianthus.core.access.GenericDataAccess
import org.slf4j.LoggerFactory

/**
 * Pipeline step that executes the resolved SQL query and opens a streaming row cursor.
 *
 * Uses [GenericDataAccess.executeQueryStream] to execute the SQL with bound parameters,
 * storing the resulting [helianthus.core.result.CloseableRowStream] in the context.
 *
 * @param dataAccess the data access layer for query execution
 * @param fetchSize number of rows to fetch per database roundtrip (default 1000)
 */
class QueryStep(
    private val dataAccess: GenericDataAccess,
    private val fetchSize: Int = 1000
) : PipelineComponent {

    private val log = LoggerFactory.getLogger(QueryStep::class.java)

    override fun process(context: PipelineContext): PipelineContext {
        val op = context.resolvedOperation
                ?: throw IllegalStateException("No resolved operation in context")

        val bound = context.boundParameters
                ?: throw IllegalStateException("No bound parameters in context")

        val startTime = System.currentTimeMillis()

        log.debug("Executing streaming query for operation: {}", op.operationId)

        // ensure typeNames length == paramValues length
        val boundPairs = op.parameters.mapNotNull { paramDef ->
            bound.values[paramDef.name]?.let { value ->
                Pair(paramDef.type, value)
            }
        }
        val typeNames = boundPairs.map { it.first }.toTypedArray()
        val paramValues = boundPairs.map { it.second }.toTypedArray()

        val stream = dataAccess.executeQueryStream(
            op.sql,
            typeNames,
            op.datasource ?: GenericDataAccess.DEFAULT_DATA_SOURCE,
            fetchSize,
            *paramValues
        )

        context.rowStream = stream

        val duration = System.currentTimeMillis() - startTime
        log.debug("Query stream opened in {} ms", duration)

        return context
    }
}

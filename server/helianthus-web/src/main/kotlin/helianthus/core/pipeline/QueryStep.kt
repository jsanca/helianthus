package helianthus.core.pipeline

import helianthus.core.access.GenericDataAccess
import helianthus.core.access.impl.db.NamedParameterSql
import org.slf4j.LoggerFactory

/**
 * Pipeline step that executes the resolved SQL query and opens a streaming row cursor.
 *
 * Uses [GenericDataAccess.executeQueryStream] to execute the SQL with bound parameters,
 * storing the resulting [helianthus.core.result.CloseableRowStream] in the context.
 *
 * Supports both positional (?) and named (:paramName) parameter styles.
 * For named parameters, all declared parameters are passed (null for missing optional ones).
 * For positional parameters, only non-null values are passed (backward compatible).
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

        val useNamedParams = NamedParameterSql.hasNamedParameters(op.sql)

        val stream = if (useNamedParams) {
            executeWithNamedParams(op, bound)
        } else {
            executeWithPositionalParams(op, bound)
        }

        context.rowStream = stream

        val duration = System.currentTimeMillis() - startTime
        log.debug("Query stream opened in {} ms", duration)

        return context
    }

    /**
     * Named parameter mode: pass ALL parameters including null for missing optional ones.
     * The SQL uses :paramName syntax and the JDBC layer binds by name.
     */
    private fun executeWithNamedParams(
        op: ResolvedOperation,
        bound: BoundParameters
    ): helianthus.core.result.CloseableRowStream {
        val paramNames = op.parameters.map { it.name }.toTypedArray()
        val typeNames = op.parameters.map { it.type }.toTypedArray()
        val paramValues = op.parameters.map { paramDef ->
            bound.values[paramDef.name]
        }.toTypedArray()

        log.debug(
            "Named param binding for {}: {} params ({})",
            op.operationId, paramNames.size,
            paramNames.zip(paramValues).joinToString { "${it.first}=${it.second}" }
        )

        return dataAccess.executeQueryStream(
            op.sql,
            paramNames,
            typeNames,
            op.datasource ?: GenericDataAccess.DEFAULT_DATA_SOURCE,
            fetchSize,
            *paramValues
        )
    }

    /**
     * Positional parameter mode (backward compatible): only pass non-null values.
     * The SQL uses ? placeholders and parameters are bound by position.
     */
    private fun executeWithPositionalParams(
        op: ResolvedOperation,
        bound: BoundParameters
    ): helianthus.core.result.CloseableRowStream {
        val boundPairs = op.parameters.mapNotNull { paramDef ->
            bound.values[paramDef.name]?.let { value ->
                Pair(paramDef.type, value)
            }
        }
        val typeNames = boundPairs.map { it.first }.toTypedArray()
        val paramValues = boundPairs.map { it.second }.toTypedArray()

        return dataAccess.executeQueryStream(
            op.sql,
            typeNames,
            op.datasource ?: GenericDataAccess.DEFAULT_DATA_SOURCE,
            fetchSize,
            *paramValues
        )
    }
}

package helianthus.core.pipeline

import helianthus.core.InvalidParameterException
import helianthus.core.access.BoundParameter
import helianthus.core.access.GenericDataAccess
import helianthus.core.access.SqlExecutionPlan
import helianthus.core.access.impl.db.NamedParameterSql
import org.slf4j.LoggerFactory

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

        val plan = buildExecutionPlan(op, bound)

        log.debug(
            "Execution plan for {}: {} positional params ({})",
            op.operationId, plan.params.size,
            plan.params.joinToString { "${it.name}=${it.value}" }
        )

        val stream = dataAccess.executeQueryStream(
            plan,
            op.datasource ?: GenericDataAccess.DEFAULT_DATA_SOURCE,
            fetchSize
        )

        context.rowStream = stream

        val duration = System.currentTimeMillis() - startTime
        log.debug("Query stream opened in {} ms", duration)

        return context
    }

    private fun buildExecutionPlan(
        op: ResolvedOperation,
        bound: BoundParameters
    ): SqlExecutionPlan {
        if (NamedParameterSql.hasNamedParameters(op.sql)) {
            return buildNamedPlan(op, bound)
        }
        return buildPositionalPlan(op, bound)
    }

    private fun buildNamedPlan(
        op: ResolvedOperation,
        bound: BoundParameters
    ): SqlExecutionPlan {
        val parsed = NamedParameterSql.parse(op.sql)
        val paramTypeMap = op.parameters.associate { it.name to it.type }

        val expandedParams = parsed.paramNames.map { name ->
            val type = paramTypeMap[name]
                ?: throw InvalidParameterException(
                    "SQL references undeclared parameter ':$name'"
                )
            BoundParameter(
                name = name,
                type = type,
                value = bound.values[name]
            )
        }

        return SqlExecutionPlan(sql = parsed.actualSql, params = expandedParams)
    }

    private fun buildPositionalPlan(
        op: ResolvedOperation,
        bound: BoundParameters
    ): SqlExecutionPlan {
        val params = op.parameters.mapNotNull { paramDef ->
            bound.values[paramDef.name]?.let { value ->
                BoundParameter(name = paramDef.name, type = paramDef.type, value = value)
            }
        }
        return SqlExecutionPlan(sql = op.sql, params = params)
    }
}

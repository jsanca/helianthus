package helianthus.core.pipeline

import helianthus.core.NoMappingException
import helianthus.core.catalog.OperationCatalog
import org.slf4j.LoggerFactory

/**
 * Pipeline step that resolves an operation request into a fully configured operation.
 *
 * Looks up the operation identifier in the [OperationCatalog] and populates
 * the context with a [ResolvedOperation] containing the SQL statement,
 * datasource, parameters, and pipeline configuration.
 *
 * @param catalog the operation catalog to resolve against
 */
class ResolveStep(
    private val catalog: OperationCatalog
) : PipelineComponent {

    private val log = LoggerFactory.getLogger(ResolveStep::class.java)

    override fun process(context: PipelineContext): PipelineContext {
        val request = context.operationRequest
        log.debug(
            "Resolving operation: {} configuration: {}",
            request.operationId, request.configurationId
        )

        val entry = catalog.resolveOperation(
            request.operationId,
            request.configurationId
        )

        val parameters = entry.parameters.map { p ->
            ParameterDefinition(name = p.name, type = p.type, required = p.required)
        }

        context.resolvedOperation = ResolvedOperation(
            operationId = entry.operationId,
            configurationId = entry.configurationId,
            datasource = entry.datasource,
            sql = entry.sql
                ?: throw NoMappingException("No SQL for operation: ${request.operationId}"),
            parameters = parameters,
            pipelineConfig = entry.pipelineConfig
        )

        log.debug(
            "Resolved operation: {} config: {} with {} parameters and {} pipeline steps",
            request.operationId, entry.configurationId, parameters.size,
            listOfNotNull(
                entry.pipelineConfig.project?.let { "project" },
                entry.pipelineConfig.filter?.let { "filter" },
                entry.pipelineConfig.limit?.let { "limit" },
                entry.pipelineConfig.derive?.let { "derive" }
            ).size
        )

        return context
    }
}

package helianthus.core

import helianthus.core.catalog.CatalogSummary
import helianthus.core.catalog.ConfigurationSummary
import helianthus.core.catalog.EntityCatalog
import helianthus.core.catalog.EntityDef
import helianthus.core.catalog.EntityRoleInfo
import helianthus.core.catalog.EntitySecurityInfo
import helianthus.core.catalog.EntitySummary
import helianthus.core.catalog.InputInfo
import helianthus.core.catalog.OperationCatalog
import helianthus.core.catalog.OperationDef
import helianthus.core.catalog.OperationSummary
import helianthus.core.catalog.ParameterInfo
import helianthus.core.pipeline.OperationRequest
import helianthus.core.pipeline.PipelineContext
import helianthus.core.pipeline.PipelineFactory
import helianthus.core.result.ResultFrame
import helianthus.core.security.AccessDeniedException
import helianthus.core.security.EntityPermissionEvaluator
import helianthus.core.security.OperationPermissionEvaluator
import helianthus.core.security.Principal
import helianthus.core.service.EntityListRequest
import helianthus.core.service.EntityService

/**
 * Framework-neutral entry point to the Helianthus runtime.
 *
 * Exposes the smallest coherent execution surface an adapter (HTTP, embedded,
 * or otherwise) needs, without requiring knowledge of the pipeline, catalogs,
 * or data-access plumbing.
 *
 * Instances are created via [HelianthusRuntimeBuilder].
 */
class HelianthusRuntime internal constructor(
    private val operationCatalog: OperationCatalog,
    private val operationPermissionEvaluator: OperationPermissionEvaluator,
    private val pipelineFactory: PipelineFactory,
    private val entityService: EntityService,
    private val entityCatalog: EntityCatalog,
    private val entityPermissionEvaluator: EntityPermissionEvaluator
) {

    /**
     * Executes an operation and returns its [ResultFrame].
     *
     * @throws NoMappingException if the operation or configuration does not exist
     * @throws AccessDeniedException if the caller is not permitted
     */
    fun executeOperation(
        principal: Principal,
        operationId: String,
        configurationId: String = "default",
        params: Map<String, String> = emptyMap()
    ): ResultFrame {
        if (!operationCatalog.operations.containsKey(operationId)) {
            throw NoMappingException("Operation not found: $operationId")
        }
        if (!operationPermissionEvaluator.checkPermission(principal, operationId, configurationId)) {
            throw AccessDeniedException(
                "Access denied to operation '$operationId' configuration '$configurationId'"
            )
        }

        val request = OperationRequest(
            operationId = operationId,
            configurationId = configurationId,
            format = "json",
            params = params
        )
        val pipeline = pipelineFactory.createPipeline(request)
        val context = PipelineContext(request)
        val result = pipeline.execute(context)

        if (result.error != null) {
            throw result.error!!
        }

        return result.resultFrame
            ?: throw IllegalStateException("Pipeline produced no result")
    }

    /**
     * Lists entities for the given request.
     */
    fun listEntities(principal: Principal, request: EntityListRequest): ResultFrame =
        entityService.listEntities(principal, request)

    /**
     * Retrieves a single entity by primary key.
     */
    fun getEntity(principal: Principal, entityName: String, id: String): ResultFrame =
        entityService.getEntity(principal, entityName, id)

    /**
     * Produces a read-model summary of the operations and entities visible to
     * the given caller.
     */
    fun catalogSummary(principal: Principal): CatalogSummary {
        val operations = operationPermissionEvaluator
            .filterVisibleOperations(principal)
            .map { (name, op) -> operationSummary(name, op) }

        val entities = entityPermissionEvaluator
            .filterVisibleEntities(principal)
            .map { (name, entity) -> entitySummary(name, entity) }

        return CatalogSummary(
            app = operationCatalog.app?.name,
            formats = FORMATS,
            operations = operations,
            entities = entities
        )
    }

    private fun operationSummary(name: String, op: OperationDef): OperationSummary =
        OperationSummary(
            name = name,
            label = op.label,
            description = op.description,
            datasource = op.datasource,
            parameters = op.parameters.map { p ->
                ParameterInfo(
                    name = p.name,
                    type = p.type,
                    required = p.required,
                    label = p.label,
                    description = p.description,
                    placeholder = p.placeholder,
                    input = p.input?.let {
                        InputInfo(
                            kind = it.kind,
                            options = it.options,
                            min = it.min,
                            max = it.max,
                            step = it.step
                        )
                    }
                )
            },
            configurations = op.configurations.map { (configName, config) ->
                ConfigurationSummary(
                    name = configName,
                    label = config.label,
                    description = config.description
                )
            }
        )

    private fun entitySummary(name: String, entity: EntityDef): EntitySummary =
        EntitySummary(
            name = name,
            label = entity.label,
            description = entity.description,
            datasource = entity.datasource,
            table = entity.table,
            primaryKey = entity.primaryKey.columns,
            fields = entity.fieldNames,
            security = entity.security?.let {
                EntitySecurityInfo(
                    read = it.read?.let { read -> EntityRoleInfo(read.roles) },
                    write = it.write?.let { write -> EntityRoleInfo(write.roles) }
                )
            }
        )

    companion object {
        private val FORMATS = listOf("json", "html", "csv", "xml")
    }
}

package helianthus.core

import helianthus.core.access.DataAccessFactory
import helianthus.core.catalog.CatalogLoader
import helianthus.core.pipeline.PipelineFactory
import helianthus.core.security.EntityPermissionEvaluator
import helianthus.core.security.OperationPermissionEvaluator
import helianthus.core.service.EntityService
import java.io.InputStream
import javax.sql.DataSource

/**
 * Assembles a fully configured [HelianthusRuntime] from neutral inputs.
 *
 * This is the supported construction path for consumers: it accepts a set of
 * named [DataSource]s and a catalog [InputStream], and hides the construction of
 * the data-access implementation, catalogs, pipeline, permission rules, and
 * entity service behind the runtime facade.
 */
class HelianthusRuntimeBuilder(
    private val dataSources: Map<String, DataSource>
) {

    /**
     * Loads the catalog from [catalogInput] and builds a [HelianthusRuntime].
     *
     * @throws IllegalStateException if the catalog is empty, fails validation,
     *         or is structurally invalid
     */
    fun build(catalogInput: InputStream): HelianthusRuntime {
        val loaded = CatalogLoader().load(catalogInput)
        val operationCatalog = loaded.operationCatalog
        val entityCatalog = loaded.entityCatalog

        val genericDataAccess = DataAccessFactory.jdbc(dataSources)
        val dialects = DataAccessFactory.sqlDialects(dataSources)

        val operationPermissionEvaluator = OperationPermissionEvaluator(operationCatalog)
        val entityPermissionEvaluator = EntityPermissionEvaluator(entityCatalog)
        val pipelineFactory = PipelineFactory(operationCatalog, genericDataAccess)
        val entityService = EntityService(
            entityCatalog = entityCatalog,
            permissionEvaluator = entityPermissionEvaluator,
            dataAccess = genericDataAccess,
            dialects = dialects
        )

        return HelianthusRuntime(
            operationCatalog = operationCatalog,
            operationPermissionEvaluator = operationPermissionEvaluator,
            pipelineFactory = pipelineFactory,
            entityService = entityService,
            entityCatalog = entityCatalog,
            entityPermissionEvaluator = entityPermissionEvaluator
        )
    }
}

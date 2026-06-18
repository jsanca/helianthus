package helianthus.core.web

import helianthus.core.catalog.OperationCatalog
import helianthus.core.security.OperationPermissionEvaluator
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class CatalogController(
    private val catalog: OperationCatalog,
    private val permissionEvaluator: OperationPermissionEvaluator
) {

    @GetMapping("/api/admin/catalog")
    fun catalog(): CatalogResponse {
        val auth = SecurityContextHolder.getContext().authentication
        val visibleOps = if (auth != null) {
            permissionEvaluator.filterVisibleOperations(auth)
        } else {
            emptyMap()
        }

        val operations = visibleOps.map { (name, op) ->
            OperationSummary(
                name = name,
                type = when {
                    op.queryRef != null -> "queryRef"
                    op.query != null -> "query"
                    else -> "unknown"
                },
                queryRef = op.queryRef,
                datasource = op.datasource,
                parameters = op.parameters.map { p ->
                    ParameterInfo(name = p.name, type = p.type, required = p.required)
                },
                configurations = op.configurations.map { (configName, config) ->
                    ConfigurationSummary(
                        name = configName,
                        pipeline = config.pipeline
                    )
                }
            )
        }

        return CatalogResponse(
            app = catalog.app?.name,
            formats = listOf("json", "html", "csv", "xml"),
            operations = operations
        )
    }
}

data class CatalogResponse(
    val app: String?,
    val formats: List<String>,
    val operations: List<OperationSummary>
)

data class OperationSummary(
    val name: String,
    val type: String,
    val queryRef: String?,
    val datasource: String?,
    val parameters: List<ParameterInfo>,
    val configurations: List<ConfigurationSummary>
)

data class ParameterInfo(
    val name: String,
    val type: String,
    val required: Boolean
)

data class ConfigurationSummary(
    val name: String,
    val pipeline: helianthus.core.pipeline.PipelineConfig
)

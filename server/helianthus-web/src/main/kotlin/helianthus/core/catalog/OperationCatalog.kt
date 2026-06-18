package helianthus.core.catalog

import helianthus.core.NoMappingException
import helianthus.core.pipeline.PipelineConfig

class OperationCatalog(
    val app: AppMetadata? = null,
    val datasources: Map<String, DatasourceDef> = emptyMap(),
    val queries: Map<String, QueryDef> = emptyMap(),
    val operations: Map<String, OperationDef> = emptyMap()
) {
    @JvmOverloads
    fun resolveOperation(
        operationId: String,
        configurationId: String? = null
    ): ResolvedCatalogEntry {
        val op = operations[operationId]
            ?: throw NoMappingException("Operation not found: $operationId")

        val configId = configurationId ?: "default"
        val config = op.configurations[configId]
            ?: throw NoMappingException(
                "Configuration '$configId' not found for operation: $operationId"
            )

        val resolved = when {
            op.query != null ->
                ResolvedQuery(op.query, op.parameters, op.datasource ?: "default")
            op.queryRef != null -> {
                val queryDef = queries[op.queryRef]
                    ?: throw NoMappingException(
                        "Query reference '${op.queryRef}' not found for operation: $operationId"
                    )
                ResolvedQuery(
                    queryDef.sql,
                    queryDef.parameters.map {
                        ParameterDef(it.key, it.value.type, it.value.required)
                    },
                    op.datasource ?: queryDef.datasource
                )
            }
            else -> throw NoMappingException(
                "Operation '$operationId' must define either 'query' or 'queryRef'"
            )
        }

        return ResolvedCatalogEntry(
            operationId = operationId,
            configurationId = configId,
            datasource = resolved.datasource,
            sql = resolved.sql,
            parameters = resolved.parameters,
            pipelineConfig = config.pipeline
        )
    }

    private data class ResolvedQuery(
        val sql: String,
        val parameters: List<ParameterDef>,
        val datasource: String
    )
}

data class ResolvedCatalogEntry(
    val operationId: String,
    val configurationId: String,
    val datasource: String,
    val sql: String,
    val parameters: List<ParameterDef>,
    val pipelineConfig: PipelineConfig
)

data class AppMetadata(
    val name: String? = null
)

data class DatasourceDef(
    val type: String = "postgres"
)

data class QueryDef(
    val datasource: String = "default",
    val sql: String,
    val parameters: Map<String, QueryParameterDef> = emptyMap()
)

data class QueryParameterDef(
    val type: String = "string",
    val required: Boolean = false
)

data class OperationDef(
    val queryRef: String? = null,
    val query: String? = null,
    val datasource: String? = null,
    val parameters: List<ParameterDef> = emptyList(),
    val security: SecurityDef? = null,
    val configurations: Map<String, ConfigurationDef> = mapOf(
        "default" to ConfigurationDef()
    )
)

data class ParameterDef(
    val name: String,
    val type: String = "string",
    val required: Boolean = false
)

data class SecurityDef(
    val roles: List<String>? = null,
    val realm: String? = null
)

data class ConfigurationDef(
    val security: SecurityDef? = null,
    val pipeline: PipelineConfig = PipelineConfig()
)

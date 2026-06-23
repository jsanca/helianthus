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
    val label: String? = null,
    val description: String? = null,
    val parameters: List<ParameterDef> = emptyList(),
    val security: SecurityDef? = null,
    val configurations: Map<String, ConfigurationDef> = mapOf(
        "default" to ConfigurationDef()
    )
)

data class ParameterDef(
    val name: String,
    val type: String = "string",
    val required: Boolean = false,
    val label: String? = null,
    val description: String? = null,
    val placeholder: String? = null,
    val input: InputDef? = null
)

data class InputDef(
    val kind: String = "text",
    val options: List<String>? = null,
    val min: Number? = null,
    val max: Number? = null,
    val step: Number? = null
)

data class SecurityDef(
    val roles: List<String>? = null,
    val realm: String? = null
)

data class ConfigurationDef(
    val label: String? = null,
    val description: String? = null,
    val security: SecurityDef? = null,
    val pipeline: PipelineConfig = PipelineConfig()
)

data class EntityDef(
    val label: String? = null,
    val description: String? = null,
    val datasource: String = "default",
    val table: String,
    val primaryKey: PrimaryKeyDef,
    val fields: List<String>,
    val security: EntitySecurityDef? = null
)

data class PrimaryKeyDef(
    val columns: List<String>
) {
    constructor(singleColumn: String) : this(listOf(singleColumn))
}

data class EntitySecurityDef(
    val read: EntityRoleDef? = null,
    val write: EntityRoleDef? = null
)

data class EntityRoleDef(
    val roles: List<String>
)

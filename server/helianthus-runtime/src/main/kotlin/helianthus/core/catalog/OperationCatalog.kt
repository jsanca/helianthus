package helianthus.core.catalog

import helianthus.core.NoMappingException
import helianthus.core.pipeline.PipelineConfig

/**
 * In-memory catalog of operation definitions loaded from `operations.yml`.
 *
 * Holds the application metadata, named datasources, shared queries, and the
 * operations themselves. The single lookup entry point is [resolveOperation].
 */
internal class OperationCatalog(
    val app: AppMetadata? = null,
    val datasources: Map<String, DatasourceDef> = emptyMap(),
    val queries: Map<String, QueryDef> = emptyMap(),
    val operations: Map<String, OperationDef> = emptyMap()
) {
    /**
     * Resolves the operation/configuration pair into a fully-prepared
     * [ResolvedCatalogEntry] containing SQL, parameters, datasource, and pipeline
     * configuration.
     *
     * @throws NoMappingException if the operation or configuration is unknown,
     *         references an undefined query, or defines neither `query` nor `queryRef`
     */
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

/**
 * Output of [OperationCatalog.resolveOperation]: the SQL to execute, the
 * parameters it expects, and the pipeline configuration to apply.
 */
internal data class ResolvedCatalogEntry(
    val operationId: String,
    val configurationId: String,
    val datasource: String,
    val sql: String,
    val parameters: List<ParameterDef>,
    val pipelineConfig: PipelineConfig
)

/** Application-level metadata (currently just the optional name). */
internal data class AppMetadata(
    val name: String? = null
)

/** Definition of a named datasource declared in the catalog. */
internal data class DatasourceDef(
    val type: String = "postgres"
)

/** A reusable query that multiple operations may reference via `queryRef`. */
internal data class QueryDef(
    val datasource: String = "default",
    val sql: String,
    val parameters: Map<String, QueryParameterDef> = emptyMap()
)

/** Type/required-ness of a single query parameter. */
internal data class QueryParameterDef(
    val type: String = "string",
    val required: Boolean = false
)

/** Catalog definition of a single operation. */
internal data class OperationDef(
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

/** Definition of a single operation parameter (name, type, UI hints). */
internal data class ParameterDef(
    val name: String,
    val type: String = "string",
    val required: Boolean = false,
    val label: String? = null,
    val description: String? = null,
    val placeholder: String? = null,
    val input: InputDef? = null
)

/** Client UI input hints for a parameter (kind, options, numeric bounds). */
internal data class InputDef(
    val kind: String = "text",
    val options: List<String>? = null,
    val min: Number? = null,
    val max: Number? = null,
    val step: Number? = null
)

/** Security configuration: required roles and optional realm. */
internal data class SecurityDef(
    val roles: List<String>? = null,
    val realm: String? = null
)

/** A named configuration variant of an operation (label, security, pipeline). */
internal data class ConfigurationDef(
    val label: String? = null,
    val description: String? = null,
    val security: SecurityDef? = null,
    val pipeline: PipelineConfig = PipelineConfig()
)

/** A single entity field: logical name plus physical column name. */
internal data class FieldDef(
    val name: String,
    val column: String = name
)

/**
 * Catalog definition of a single entity: table, primary key, exposed fields,
 * and optional security rules.
 */
internal data class EntityDef(
    val label: String? = null,
    val description: String? = null,
    val datasource: String = "default",
    val table: String,
    val primaryKey: PrimaryKeyDef,
    val fields: List<FieldDef>,
    val security: EntitySecurityDef? = null
) {
    /** Logical field names in declaration order. */
    val fieldNames: List<String> get() = fields.map { it.name }

    /** Returns the field with the given logical name, or null if not exposed. */
    fun fieldByName(name: String): FieldDef? = fields.find { it.name == name }
}

/** Primary-key declaration: a single column or a composite key. */
internal data class PrimaryKeyDef(
    val columns: List<String>
) {
    /** Convenience constructor for the single-column case. */
    constructor(singleColumn: String) : this(listOf(singleColumn))
}

/** Entity security configuration: read and write role lists. */
internal data class EntitySecurityDef(
    val read: EntityRoleDef? = null,
    val write: EntityRoleDef? = null
)

/** A list of roles granted an entity read/write permission. */
internal data class EntityRoleDef(
    val roles: List<String>
)

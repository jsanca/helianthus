package helianthus.core.catalog

/**
 * Public read-model of the catalog, produced by [helianthus.core.HelianthusRuntime.catalogSummary].
 *
 * These are the only catalog types that form part of Helianthus's supported
 * consumer API. The underlying catalog representation ([OperationCatalog],
 * [EntityCatalog], and the `*Def` model) is internal.
 */
data class CatalogSummary(
    val app: String?,
    val formats: List<String>,
    val operations: List<OperationSummary>,
    val entities: List<EntitySummary>
)

/** Read-model summary of a single operation. */
data class OperationSummary(
    val name: String,
    val label: String?,
    val description: String?,
    val datasource: String?,
    val parameters: List<ParameterInfo>,
    val configurations: List<ConfigurationSummary>
)

/** Read-model summary of a single operation parameter. */
data class ParameterInfo(
    val name: String,
    val type: String,
    val required: Boolean,
    val label: String?,
    val description: String?,
    val placeholder: String?,
    val input: InputInfo?
)

/** Read-model summary of the client UI hints for a parameter input. */
data class InputInfo(
    val kind: String,
    val options: List<String>?,
    val min: Number?,
    val max: Number?,
    val step: Number?
)

/** Read-model summary of a named operation configuration. */
data class ConfigurationSummary(
    val name: String,
    val label: String?,
    val description: String?
)

/** Read-model summary of a single entity. */
data class EntitySummary(
    val name: String,
    val label: String?,
    val description: String?,
    val datasource: String,
    val table: String,
    val primaryKey: List<String>,
    val fields: List<String>,
    val security: EntitySecurityInfo?
)

/** Read-model summary of an entity's security configuration. */
data class EntitySecurityInfo(
    val read: EntityRoleInfo?,
    val write: EntityRoleInfo?
)

/** Read-model summary of the role list attached to an entity security entry. */
data class EntityRoleInfo(
    val roles: List<String>
)

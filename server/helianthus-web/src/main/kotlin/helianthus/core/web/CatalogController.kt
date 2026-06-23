package helianthus.core.web

import helianthus.core.catalog.EntityCatalog
import helianthus.core.catalog.OperationCatalog
import helianthus.core.security.EntityPermissionEvaluator
import helianthus.core.security.OperationPermissionEvaluator
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class CatalogController(
    private val catalog: OperationCatalog,
    private val permissionEvaluator: OperationPermissionEvaluator,
    private val entityCatalog: EntityCatalog,
    private val entityPermissionEvaluator: EntityPermissionEvaluator
) {

    @GetMapping("/api/admin/catalog")
    fun catalog(): CatalogResponse {
        val auth = SecurityContextHolder.getContext().authentication
        val visibleOps = if (auth != null) {
            permissionEvaluator.filterVisibleOperations(auth)
        } else {
            emptyMap()
        }

        val visibleEntities = if (auth != null) {
            entityPermissionEvaluator.filterVisibleEntities(auth)
        } else {
            emptyMap()
        }

        val operations = visibleOps.map { (name, op) ->
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
        }

        val entities = visibleEntities.map { (name, entity) ->
            EntitySummary(
                name = name,
                label = entity.label,
                description = entity.description,
                datasource = entity.datasource,
                table = entity.table,
                primaryKey = entity.primaryKey.columns,
                fields = entity.fields,
                security = entity.security?.let {
                    EntitySecurityInfo(
                        read = it.read?.let { read -> EntityRoleInfo(read.roles) },
                        write = it.write?.let { write -> EntityRoleInfo(write.roles) }
                    )
                }
            )
        }

        return CatalogResponse(
            app = catalog.app?.name,
            formats = listOf("json", "html", "csv", "xml"),
            operations = operations,
            entities = entities
        )
    }
}

data class CatalogResponse(
    val app: String?,
    val formats: List<String>,
    val operations: List<OperationSummary>,
    val entities: List<EntitySummary>
)

data class OperationSummary(
    val name: String,
    val label: String?,
    val description: String?,
    val datasource: String?,
    val parameters: List<ParameterInfo>,
    val configurations: List<ConfigurationSummary>
)

data class ParameterInfo(
    val name: String,
    val type: String,
    val required: Boolean,
    val label: String?,
    val description: String?,
    val placeholder: String?,
    val input: InputInfo?
)

data class InputInfo(
    val kind: String,
    val options: List<String>?,
    val min: Number?,
    val max: Number?,
    val step: Number?
)

data class ConfigurationSummary(
    val name: String,
    val label: String?,
    val description: String?
)

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

data class EntitySecurityInfo(
    val read: EntityRoleInfo?,
    val write: EntityRoleInfo?
)

data class EntityRoleInfo(
    val roles: List<String>
)

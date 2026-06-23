package helianthus.core.catalog

import helianthus.core.EntityNotFoundException
import org.slf4j.LoggerFactory

class EntityCatalog(
    val entities: Map<String, EntityDef> = emptyMap()
) {
    private val log = LoggerFactory.getLogger(EntityCatalog::class.java)

    fun resolveEntity(entityName: String): EntityDef {
        return entities[entityName]
            ?: throw EntityNotFoundException("Entity not found: $entityName")
    }

    fun validate(datasources: Map<String, DatasourceDef>) {
        entities.forEach { (name, entity) ->
            validateEntity(name, entity, datasources)
        }
        log.info("Validated {} entities", entities.size)
    }

    private fun validateEntity(name: String, entity: EntityDef, datasources: Map<String, DatasourceDef>) {
        if (entity.fields.isEmpty()) {
            throw IllegalStateException("Entity '$name' must have at least one field")
        }

        if (entity.primaryKey.columns.isEmpty()) {
            throw IllegalStateException("Entity '$name' must have a primary key")
        }

        entity.primaryKey.columns.forEach { pkCol ->
            if (pkCol !in entity.fields) {
                throw IllegalStateException(
                    "Entity '$name' primary key column '$pkCol' is not in fields list ${entity.fields}"
                )
            }
        }

        if (entity.datasource !in datasources) {
            throw IllegalStateException(
                "Entity '$name' references unknown datasource '${entity.datasource}'. " +
                "Available datasources: ${datasources.keys}"
            )
        }
    }
}

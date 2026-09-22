package helianthus.core.catalog

import helianthus.core.EntityNotFoundException
import org.slf4j.LoggerFactory

/**
 * In-memory catalog of entity definitions loaded from `operations.yml`.
 *
 * Provides lookup and structural validation (non-empty fields, primary key
 * presence, primary-key columns included in fields, datasource known).
 */
internal class EntityCatalog(
    val entities: Map<String, EntityDef> = emptyMap()
) {
    private val log = LoggerFactory.getLogger(EntityCatalog::class.java)

    /**
     * Looks up [entityName] in the catalog.
     *
     * @throws EntityNotFoundException if no entity is registered under [entityName]
     */
    fun resolveEntity(entityName: String): EntityDef {
        return entities[entityName]
            ?: throw EntityNotFoundException("Entity not found: $entityName")
    }

    /**
     * Validates structural integrity of every entity against the available
     * datasources.
     *
     * @throws IllegalStateException if any entity fails validation
     */
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
            if (entity.fields.none { it.name == pkCol }) {
                throw IllegalStateException(
                    "Entity '$name' primary key column '$pkCol' is not in fields list ${entity.fieldNames}"
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

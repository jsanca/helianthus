package helianthus.core.security

import helianthus.core.catalog.EntityCatalog
import helianthus.core.catalog.EntityDef
import org.slf4j.LoggerFactory

/**
 * Evaluates entity read-permission rules against an [EntityCatalog].
 *
 * Reads `security.read.roles` from each entity definition. [ADMIN_ROLE] bypasses
 * all checks; missing read roles allow any authenticated caller.
 */
internal class EntityPermissionEvaluator(
    private val entityCatalog: EntityCatalog
) {
    private val log = LoggerFactory.getLogger(EntityPermissionEvaluator::class.java)

    /**
     * Returns true if [principal] may read the entity identified by [entityName].
     */
    fun checkReadPermission(principal: Principal, entityName: String): Boolean {
        if (principal.roles.contains(ADMIN_ROLE)) {
            return true
        }

        val entity = entityCatalog.entities[entityName] ?: return false
        val readRoles = entity.security?.read?.roles ?: emptyList()

        if (readRoles.isEmpty()) {
            log.debug("No read roles required for entity '{}'", entityName)
            return true
        }

        val hasRole = readRoles.any { role -> role in principal.roles }

        if (!hasRole) {
            log.debug(
                "Access denied for user '{}' to entity '{}': requires read roles {}",
                principal.name, entityName, readRoles
            )
        }

        return hasRole
    }

    /**
     * Returns the subset of entities that [principal] is allowed to read.
     * ADMIN sees everything.
     */
    fun filterVisibleEntities(principal: Principal): Map<String, EntityDef> {
        if (principal.roles.contains(ADMIN_ROLE)) {
            return entityCatalog.entities
        }

        return entityCatalog.entities.filter { (_, entity) ->
            val readRoles = entity.security?.read?.roles
            if (readRoles.isNullOrEmpty()) {
                true
            } else {
                readRoles.any { role -> role in principal.roles }
            }
        }
    }
}
